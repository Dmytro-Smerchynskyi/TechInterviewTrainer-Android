package com.oniolla.interviewer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetStatsUseCase
import com.oniolla.interviewer.domain.StatsRequest
import com.oniolla.interviewer.domain.StatsDates
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.GetTopicsUseCase
import com.oniolla.interviewer.domain.TopicsFilter
import com.oniolla.interviewer.domain.TopicCatalogUseCase
import com.oniolla.interviewer.domain.GetFavoriteQuestionsUseCase
import com.oniolla.interviewer.domain.FavoriteQuestionsRequest
import com.oniolla.interviewer.domain.FavoritesFilter
import com.oniolla.interviewer.domain.GetQuestionAnswerUseCase
import com.oniolla.interviewer.domain.GetQuestionsUseCase
import com.oniolla.interviewer.domain.QuestionsRequest
import com.oniolla.interviewer.domain.QuestionDifficultyFilter
import com.oniolla.interviewer.domain.SetQuestionSavedUseCase
import com.oniolla.interviewer.domain.QuestionSavedRequest
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDashboardUseCase: GetDashboardUseCase,
    private val getTopicsUseCase: GetTopicsUseCase,
    private val getQuestionsUseCase: GetQuestionsUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val topicCatalogUseCase: TopicCatalogUseCase,
    private val setQuestionSavedUseCase: SetQuestionSavedUseCase,
    private val getFavoriteQuestionsUseCase: GetFavoriteQuestionsUseCase,
    private val getStatsUseCase: GetStatsUseCase,
    private val getQuestionAnswerUseCase: GetQuestionAnswerUseCase
) : ViewModel() {
    private val _stateFlow = MutableStateFlow(HomeViewState(
        paywallOpen = savedStateHandle.get<Boolean>("paywallOpen") ?: false,
        tab = HomeTab.entries.firstOrNull { it.name == savedStateHandle.get<String>("homeTab") }
            ?: HomeTab.HOME,
        topicsFilter = TopicsFilter.entries.firstOrNull { it.name == savedStateHandle.get<String>("topicsFilter") }
            ?: TopicsFilter.ALL,
        favoritesFilter = FavoritesFilter.entries.firstOrNull {
            it.name == savedStateHandle.get<String>("favoritesFilter")
        } ?: FavoritesFilter.SAVED,
        questionSearch = savedStateHandle.get<String>("questionSearch").orEmpty(),
        questionDifficulty = QuestionDifficultyFilter.entries.firstOrNull {
            it.name == savedStateHandle.get<String>("questionDifficulty")
        } ?: QuestionDifficultyFilter.ALL,
        questionTopic = savedStateHandle.get<Long>("questionTopicId")?.let {
            QuestionTopicFilter(it, savedStateHandle.get<String>("questionTopicName").orEmpty())
        }
    ))
    val stateFlow = _stateFlow.asStateFlow()

    private val _uiEventsFlow = MutableSharedFlow<UiEvent>()
    val uiEventsFlow = _uiEventsFlow.asSharedFlow()
    private var answerJob: Job? = null
    private var questionRefreshJob: Job? = null
    private var questionUsageJob: Job? = null
    private var detailGeneration = 0
    private val revealedQuestionIds = mutableSetOf<Long>()
    private var dashboardJob: Job? = null
    private var topicsJob: Job? = null
    private var topicsGeneration = 0
    private var catalogScope: String? = null
    private var catalogObserverJob: Job? = null
    private var catalogRefreshJob: Job? = null
    private var questionsJob: Job? = null
    private var questionsGeneration = 0
    private val consumedCursors = mutableSetOf<String>()
    private data class SavedQuestionChange(val saved: Boolean, val revision: Long)
    private val savedQuestionChanges = mutableMapOf<Long, SavedQuestionChange>()
    private val savedDebounceJobs = mutableMapOf<Long, Job>()
    private val savedRequestLocks = mutableMapOf<Long, Mutex>()
    private var savedRevision = 0L
    private val changedQuestions = mutableMapOf<Long, QuestionResponse>()
    private val removedSavedQuestions = mutableSetOf<Long>()
    private var favoritesJob: Job? = null
    private var favoritesGeneration = 0
    private var favoritesDirty = false
    private val favoriteConsumedCursors = mutableSetOf<String>()
    private var statsJob: Job? = null
    private var statsGeneration = 0
    private val statsTimeZone = StatsDates.deviceTimeZone()
    private var statsWeekStart = StatsDates.validMonday(savedStateHandle.get<String>("statsWeekStart"))
        ?.takeIf { it <= StatsDates.currentWeek(statsTimeZone) }

    init {
        observeUsage()
        loadDashboard()
        loadStats()
        when (_stateFlow.value.tab) {
            HomeTab.TOPICS -> loadTopics()
            HomeTab.QUESTIONS -> loadQuestions()
            HomeTab.FAVORITES -> loadFavorites()
            else -> Unit
        }
    }

    private fun observeUsage() {
        viewModelScope.launch { getQuestionAnswerUseCase.cachedUsage().collect { usage ->
            _stateFlow.update { state ->
                val dashboard = (state.dashboard as? ContentViewState.Ready)?.content
                state.copy(questionDetail = state.questionDetail?.copy(usage = usage),
                    dashboard = dashboard?.let { ContentViewState.Ready(it.copy(
                        dashboard = it.dashboard.copy(usage = usage))) } ?: state.dashboard)
            }
        } }
    }

    fun openQuestion(question: QuestionResponse) {
        answerJob?.cancel()
        questionRefreshJob?.cancel()
        questionUsageJob?.cancel()
        detailGeneration++
        val current = applySavedChanges(listOf(question)).single()
        val usage = getQuestionAnswerUseCase.currentUsage()
        _stateFlow.update { it.copy(questionDetail = QuestionDetailState(current, usage = usage)) }
        refreshQuestionUsage()
    }

    fun closeQuestion() {
        answerJob?.cancel()
        questionRefreshJob?.cancel()
        questionUsageJob?.cancel()
        detailGeneration++
        _stateFlow.update { it.copy(questionDetail = null) }
    }

    fun refreshQuestionUsage() {
        val detail = _stateFlow.value.questionDetail ?: return
        if (questionUsageJob?.isActive == true) return
        val generation = detailGeneration
        val id = detail.question.id
        _stateFlow.update { it.copy(questionDetail = it.questionDetail?.copy(isUsageLoading = true, usageError = null)) }
        questionUsageJob = viewModelScope.launch {
            val result = getQuestionAnswerUseCase.usage()
            currentCoroutineContext().ensureActive()
            if (generation != detailGeneration || _stateFlow.value.questionDetail?.question?.id != id) return@launch
            _stateFlow.update { state ->
                val current = state.questionDetail ?: return@update state
                when (result) {
                    is ContentResult.Success -> {
                        state.copy(questionDetail = current.copy(
                            usage = getQuestionAnswerUseCase.currentUsage(), isUsageLoading = false))
                    }
                    is ContentResult.Error -> state.copy(questionDetail = current.copy(
                        isUsageLoading = false, usageError = result.message))
                    ContentResult.SessionExpired -> state.copy(sessionExpired = true,
                        questionDetail = current.copy(isUsageLoading = false))
                }
            }
        }
    }

    fun revealQuestionAnswer() {
        val detail = _stateFlow.value.questionDetail ?: return
        if (!detail.canReveal) return
        if (detail.answer is ContentViewState.Ready) {
            _stateFlow.update { state ->
                state.copy(questionDetail = state.questionDetail?.let {
                    it.copy(isAnswerVisible = !it.isAnswerVisible)
                })
            }
            return
        }
        if (answerJob?.isActive == true) return
        val generation = detailGeneration
        val id = detail.question.id
        _stateFlow.update { it.copy(questionDetail = it.questionDetail?.copy(
            answer = ContentViewState.Loading, isAnswerVisible = true)) }
        answerJob = viewModelScope.launch {
            val result = getQuestionAnswerUseCase(id)
            currentCoroutineContext().ensureActive()
            if (generation != detailGeneration || _stateFlow.value.questionDetail?.question?.id != id) return@launch
            if (result is ContentResult.Success) revealedQuestionIds.add(id)
            _stateFlow.update { state ->
                fun updated(list: ContentViewState<QuestionsContent>): ContentViewState<QuestionsContent> {
                    val content = (list as? ContentViewState.Ready)?.content ?: return list
                    return ContentViewState.Ready(content.copy(items = applySavedChanges(content.items)))
                }
                val dashboard = (state.dashboard as? ContentViewState.Ready)?.content
                state.copy(
                    questionDetail = state.questionDetail?.let {
                        it.copy(question = applySavedChanges(listOf(it.question)).single(),
                            answer = result.toViewState())
                    },
                    questions = updated(state.questions),
                    favorites = updated(state.favorites),
                    dashboard = dashboard?.let { ContentViewState.Ready(it.copy(
                        dashboard = it.dashboard.copy(todayReview = applySavedChanges(it.dashboard.todayReview)))) }
                        ?: state.dashboard,
                    sessionExpired = state.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (!detail.question.answerRevealed && result != ContentResult.SessionExpired) refreshQuestionUsage()
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    fun selectTab(tab: HomeTab) {
        if (_stateFlow.value.questionDetail != null) closeQuestion()
        val previousTab = _stateFlow.value.tab
        savedStateHandle["homeTab"] = tab.name
        _stateFlow.update { it.copy(tab = tab) }
        if (tab == HomeTab.STATS && _stateFlow.value.statistics == ContentViewState.NotLoaded) loadStats()
        if (tab == HomeTab.TOPICS && _stateFlow.value.topics == ContentViewState.NotLoaded) loadTopics()
        if (tab == HomeTab.QUESTIONS && _stateFlow.value.questions == ContentViewState.NotLoaded) loadQuestions()
        if (tab == HomeTab.FAVORITES && (_stateFlow.value.favorites == ContentViewState.NotLoaded ||
                previousTab != tab && favoritesDirty)) loadFavorites()
    }

    fun openTopicQuestions(topicId: Long, topicName: String) {
        savedStateHandle["questionSearch"] = ""
        _stateFlow.update { it.copy(questionSearch = "") }
        openQuestionTopic(topicId, topicName)
    }

    fun openQuestionTopic(topicId: Long, topicName: String) {
        savedStateHandle["questionDifficulty"] = QuestionDifficultyFilter.ALL.name
        _stateFlow.update { it.copy(questionDifficulty = QuestionDifficultyFilter.ALL) }
        selectQuestionTopic(topicId, topicName)
    }

    fun selectQuestionDifficulty(filter: QuestionDifficultyFilter) {
        if (filter == _stateFlow.value.questionDifficulty) return
        savedStateHandle["questionDifficulty"] = filter.name
        _stateFlow.update { it.copy(questionDifficulty = filter) }
        refreshQuestions()
    }

    fun selectQuestionTopic(topicId: Long, topicName: String) {
        val topic = QuestionTopicFilter(topicId, topicName)
        saveQuestionTopic(topic)
        savedStateHandle["homeTab"] = HomeTab.QUESTIONS.name
        _stateFlow.update { it.copy(tab = HomeTab.QUESTIONS, questionTopic = topic) }
        refreshQuestions()
    }

    fun clearQuestionTopic() {
        if (_stateFlow.value.questionTopic == null) return
        saveQuestionTopic(null)
        _stateFlow.update { it.copy(questionTopic = null) }
        refreshQuestions()
    }

    private fun saveQuestionTopic(topic: QuestionTopicFilter?) {
        savedStateHandle["questionTopicId"] = topic?.id
        savedStateHandle["questionTopicName"] = topic?.name
    }

    fun updateQuestionSearch(query: String) {
        if (query == _stateFlow.value.questionSearch) return
        savedStateHandle["questionSearch"] = query
        _stateFlow.update { it.copy(questionSearch = query) }
        if (_stateFlow.value.tab == HomeTab.QUESTIONS ||
            _stateFlow.value.questions != ContentViewState.NotLoaded) refreshQuestions(debounce = query.isNotEmpty())
    }

    fun loadQuestions() {
        if (questionsJob?.isActive == true) return
        refreshQuestions()
    }

    private fun refreshQuestions(debounce: Boolean = false) {
        questionsJob?.cancel()
        val generation = ++questionsGeneration
        consumedCursors.clear()
        val request = questionRequest()
        _stateFlow.update { it.copy(
            questions = if (debounce) it.questions else ContentViewState.Loading,
            isQuestionSearchPending = debounce
        ) }
        questionsJob = viewModelScope.launch {
            if (debounce) {
                delay(0.75.seconds)
                _stateFlow.update { it.copy(questions = ContentViewState.Loading, isQuestionSearchPending = false) }
            }
            val result = getQuestionsUseCase(request)
            currentCoroutineContext().ensureActive()
            if (generation != questionsGeneration) return@launch
            _stateFlow.update {
                val contentState = when (result) {
                    is ContentResult.Success -> ContentViewState.Ready(QuestionsContent(
                        items = applySavedChanges(result.content.items.distinctBy { item -> item.id }),
                        nextCursor = result.content.nextCursor?.takeIf { cursor -> cursor.isNotEmpty() },
                        topics = result.content.topics,
                        access = result.content.access,
                        resultRevision = generation
                    ))
                    is ContentResult.Error -> ContentViewState.Failed(result.message)
                    ContentResult.SessionExpired -> ContentViewState.NotLoaded
                }
                it.copy(questions = contentState,
                    sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    private fun questionRequest(cursor: String? = null) = QuestionsRequest(
        topicId = _stateFlow.value.questionTopic?.id,
        search = _stateFlow.value.questionSearch,
        cursor = cursor,
        difficulty = _stateFlow.value.questionDifficulty
    )

    fun loadMoreQuestions() {
        if (_stateFlow.value.isQuestionSearchPending) return
        val content = (_stateFlow.value.questions as? ContentViewState.Ready)?.content ?: return
        val cursor = content.nextCursor ?: return
        if (questionsJob?.isActive == true || content.isAppending || content.appendError != null) return
        val generation = questionsGeneration
        val request = questionRequest(cursor)
        _stateFlow.update { it.copy(questions = ContentViewState.Ready(content.copy(isAppending = true))) }
        questionsJob = viewModelScope.launch {
            val result = getQuestionsUseCase(request)
            currentCoroutineContext().ensureActive()
            if (generation != questionsGeneration) return@launch
            if (result is ContentResult.Success) consumedCursors.add(cursor)
            _stateFlow.update { state ->
                val current = (state.questions as? ContentViewState.Ready)?.content ?: return@update state
                when (result) {
                    is ContentResult.Success -> state.copy(questions = ContentViewState.Ready(current.copy(
                        items = applySavedChanges((current.items + result.content.items).distinctBy { it.id }),
                        nextCursor = result.content.nextCursor?.takeIf {
                            it.isNotEmpty() && it !in consumedCursors
                        },
                        topics = result.content.topics.ifEmpty { current.topics },
                        access = result.content.access ?: current.access,
                        isAppending = false,
                        appendError = null
                    )))
                    is ContentResult.Error -> state.copy(questions = ContentViewState.Ready(current.copy(
                        isAppending = false, appendError = result.message
                    )))
                    ContentResult.SessionExpired -> state.copy(sessionExpired = true,
                        questions = ContentViewState.NotLoaded)
                }
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    fun toggleQuestionSaved(questionId: Long) {
        if (_stateFlow.value.sessionExpired) return
        val state = _stateFlow.value
        val lists = if (state.tab == HomeTab.FAVORITES) listOf(state.favorites, state.questions)
            else listOf(state.questions, state.favorites)
        val question = state.questionDetail?.question?.takeIf { it.id == questionId }
            ?: lists.firstNotNullOfOrNull { list ->
                (list as? ContentViewState.Ready)?.content?.items?.firstOrNull { it.id == questionId }
            } ?: (state.dashboard as? ContentViewState.Ready)?.content?.dashboard?.todayReview
                ?.firstOrNull { it.id == questionId } ?: return
        changedQuestions[questionId] = question
        favoritesDirty = true
        removedSavedQuestions.remove(questionId)
        val change = SavedQuestionChange(!question.saved, ++savedRevision)
        updateSavedQuestion(questionId, change)
        savedDebounceJobs.remove(questionId)?.cancel()
        savedDebounceJobs[questionId] = viewModelScope.launch {
            delay(1.seconds)
            if (savedQuestionChanges[questionId]?.revision != change.revision) return@launch
            savedDebounceJobs.remove(questionId)
            // Cancel only the debounce. An already sent request must finish before the next one.
            viewModelScope.launch {
                savedRequestLocks.getOrPut(questionId) { Mutex() }.withLock {
                    if (savedQuestionChanges[questionId]?.revision != change.revision) return@withLock
                    val result = setQuestionSavedUseCase(QuestionSavedRequest(questionId, change.saved))
                    currentCoroutineContext().ensureActive()
                    if (result == ContentResult.SessionExpired) {
                        _stateFlow.update { it.copy(sessionExpired = true) }
                    }
                    if (savedQuestionChanges[questionId]?.revision != change.revision) return@withLock
                    when (result) {
                        is ContentResult.Success -> {
                            favoritesDirty = true
                            if (!change.saved) {
                                removedSavedQuestions.add(questionId)
                                _stateFlow.update { state ->
                                    val content = (state.favorites as? ContentViewState.Ready)?.content
                                    if (state.favoritesFilter != FavoritesFilter.SAVED || content == null) state
                                    else state.copy(favorites = ContentViewState.Ready(content.copy(
                                        items = content.items.filterNot { it.id == questionId }
                                    )))
                                }
                            }
                        }
                        is ContentResult.Error -> {
                            updateSavedQuestion(questionId, change.copy(saved = false))
                            _uiEventsFlow.emit(UiEvent.Toast(result.message))
                        }
                        ContentResult.SessionExpired -> updateSavedQuestion(questionId, change.copy(saved = false))
                    }
                }
            }
        }
    }

    private fun updateSavedQuestion(questionId: Long, change: SavedQuestionChange) {
        savedQuestionChanges[questionId] = change
        _stateFlow.update { state ->
            fun updated(list: ContentViewState<QuestionsContent>, addSaved: Boolean = false): ContentViewState<QuestionsContent> {
                val content = (list as? ContentViewState.Ready)?.content ?: return list
                val extra = if (addSaved && change.saved) listOfNotNull(changedQuestions[questionId]) else emptyList()
                return ContentViewState.Ready(content.copy(
                    items = applySavedChanges((content.items + extra).distinctBy { it.id })
                ))
            }
            val dashboard = (state.dashboard as? ContentViewState.Ready)?.content
            state.copy(questions = updated(state.questions),
                favorites = updated(state.favorites, state.favoritesFilter == FavoritesFilter.SAVED),
                questionDetail = state.questionDetail?.let { detail ->
                    if (detail.question.id == questionId) detail.copy(question = detail.question.copy(saved = change.saved))
                    else detail
                },
                dashboard = dashboard?.let { ContentViewState.Ready(it.copy(
                    dashboard = it.dashboard.copy(todayReview = applySavedChanges(it.dashboard.todayReview)))) }
                    ?: state.dashboard)
        }
    }

    private fun applySavedChanges(items: List<QuestionResponse>): List<QuestionResponse> = items.map { question ->
        val updated = savedQuestionChanges[question.id]?.let { question.copy(saved = it.saved) } ?: question
        if (question.id in revealedQuestionIds) updated.copy(answerRevealed = true) else updated
    }

    fun retryQuestionPage() {
        val content = (_stateFlow.value.questions as? ContentViewState.Ready)?.content ?: return
        if (content.appendError == null) return
        _stateFlow.update { it.copy(questions = ContentViewState.Ready(content.copy(appendError = null))) }
        loadMoreQuestions()
    }

    fun selectFavoritesFilter(filter: FavoritesFilter) {
        if (filter == _stateFlow.value.favoritesFilter) return
        savedStateHandle["favoritesFilter"] = filter.name
        _stateFlow.update { it.copy(favoritesFilter = filter) }
        refreshFavorites()
    }

    fun loadFavorites() {
        if (favoritesJob?.isActive == true) return
        refreshFavorites()
    }

    private fun favoriteItems(items: List<QuestionResponse>, filter: FavoritesFilter): List<QuestionResponse> {
        val localSaved = if (filter == FavoritesFilter.SAVED) changedQuestions.values.filter {
            savedQuestionChanges[it.id]?.saved == true
        } else emptyList()
        return applySavedChanges((items + localSaved).distinctBy { it.id })
            .filterNot { filter == FavoritesFilter.SAVED && it.id in removedSavedQuestions }
    }

    private fun refreshFavorites() {
        favoritesJob?.cancel()
        val generation = ++favoritesGeneration
        val filter = _stateFlow.value.favoritesFilter
        val revision = savedRevision
        favoriteConsumedCursors.clear()
        _stateFlow.update { it.copy(favorites = ContentViewState.Loading) }
        favoritesJob = viewModelScope.launch {
            val result = getFavoriteQuestionsUseCase(FavoriteQuestionsRequest(filter))
            currentCoroutineContext().ensureActive()
            if (generation != favoritesGeneration) return@launch
            if (result is ContentResult.Success) favoritesDirty = revision != savedRevision
            _stateFlow.update { state ->
                val contentState = when (result) {
                    is ContentResult.Success -> ContentViewState.Ready(QuestionsContent(
                        items = favoriteItems(result.content.items, filter),
                        nextCursor = result.content.nextCursor?.takeIf { it.isNotEmpty() },
                        topics = result.content.topics,
                        access = result.content.access,
                        resultRevision = generation
                    ))
                    is ContentResult.Error -> ContentViewState.Failed(result.message)
                    ContentResult.SessionExpired -> ContentViewState.NotLoaded
                }
                state.copy(favorites = contentState,
                    sessionExpired = state.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    fun loadMoreFavorites() {
        val content = (_stateFlow.value.favorites as? ContentViewState.Ready)?.content ?: return
        val cursor = content.nextCursor ?: return
        if (favoritesJob?.isActive == true || content.isAppending || content.appendError != null) return
        val generation = favoritesGeneration
        val filter = _stateFlow.value.favoritesFilter
        _stateFlow.update { it.copy(favorites = ContentViewState.Ready(content.copy(isAppending = true))) }
        favoritesJob = viewModelScope.launch {
            val result = getFavoriteQuestionsUseCase(FavoriteQuestionsRequest(filter, cursor))
            currentCoroutineContext().ensureActive()
            if (generation != favoritesGeneration) return@launch
            if (result is ContentResult.Success) favoriteConsumedCursors.add(cursor)
            _stateFlow.update { state ->
                val current = (state.favorites as? ContentViewState.Ready)?.content ?: return@update state
                when (result) {
                    is ContentResult.Success -> state.copy(favorites = ContentViewState.Ready(current.copy(
                        items = favoriteItems(current.items + result.content.items, filter),
                        nextCursor = result.content.nextCursor?.takeIf {
                            it.isNotEmpty() && it !in favoriteConsumedCursors
                        },
                        topics = result.content.topics.ifEmpty { current.topics },
                        access = result.content.access ?: current.access,
                        isAppending = false,
                        appendError = null
                    )))
                    is ContentResult.Error -> state.copy(favorites = ContentViewState.Ready(current.copy(
                        isAppending = false, appendError = result.message
                    )))
                    ContentResult.SessionExpired -> state.copy(sessionExpired = true,
                        favorites = ContentViewState.NotLoaded)
                }
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    fun retryFavoritePage() {
        val content = (_stateFlow.value.favorites as? ContentViewState.Ready)?.content ?: return
        if (content.appendError == null) return
        _stateFlow.update { it.copy(favorites = ContentViewState.Ready(content.copy(appendError = null))) }
        loadMoreFavorites()
    }

    fun loadStats() {
        if (statsJob?.isActive == true) return
        refreshStats()
    }

    fun changeStatsWeek(direction: Int) {
        if (direction != -1 && direction != 1) return
        val content = (_stateFlow.value.statistics as? ContentViewState.Ready)?.content ?: return
        val nextWeek = StatsDates.shiftDays(content.weekStart, direction * 7)
        if (nextWeek > content.currentWeekStart) return
        statsWeekStart = nextWeek
        savedStateHandle["statsWeekStart"] = nextWeek
        refreshStats()
    }

    private fun refreshStats() {
        statsJob?.cancel()
        val generation = ++statsGeneration
        val requestedWeek = statsWeekStart
        _stateFlow.update { state ->
            val content = (state.statistics as? ContentViewState.Ready)?.content
            state.copy(statistics = if (content == null) ContentViewState.Loading else
                ContentViewState.Ready(content.copy(
                    weekStart = requestedWeek ?: content.weekStart,
                    isChartLoading = true, chartError = null
                )))
        }
        statsJob = viewModelScope.launch {
            val result = getStatsUseCase(StatsRequest(statsTimeZone, requestedWeek))
            currentCoroutineContext().ensureActive()
            if (generation != statsGeneration) return@launch
            _stateFlow.update { state ->
                val contentState = when (result) {
                    is ContentResult.Success -> {
                        val response = result.content
                        val currentWeek = StatsDates.currentWeek(response.timeZone,
                            StatsDates.serverTimeMillis(response.serverTime) ?: System.currentTimeMillis())
                        val week = (StatsDates.validMonday(response.weekStart) ?: requestedWeek ?: currentWeek)
                            .coerceAtMost(currentWeek)
                        statsWeekStart = week
                        savedStateHandle["statsWeekStart"] = week
                        ContentViewState.Ready(StatisticsContent(response, week, currentWeek))
                    }
                    is ContentResult.Error -> {
                        val content = (state.statistics as? ContentViewState.Ready)?.content
                        if (content == null) ContentViewState.Failed(result.message) else
                            ContentViewState.Ready(content.copy(isChartLoading = false, chartError = result.message))
                    }
                    ContentResult.SessionExpired -> ContentViewState.NotLoaded
                }
                state.copy(statistics = contentState,
                    sessionExpired = state.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    fun stopSessionWork() {
        viewModelScope.coroutineContext.cancelChildren()
    }

    fun reloadAfterProfileChange() {
        stopSessionWork()
        detailGeneration++
        questionsGeneration++
        favoritesGeneration++
        topicsGeneration++
        statsGeneration++
        savedDebounceJobs.clear()
        savedQuestionChanges.clear()
        revealedQuestionIds.clear()
        changedQuestions.clear()
        removedSavedQuestions.clear()
        consumedCursors.clear()
        favoriteConsumedCursors.clear()
        catalogScope = null
        statsWeekStart = null
        savedStateHandle["statsWeekStart"] = null
        savedStateHandle["homeTab"] = HomeTab.HOME.name
        savedStateHandle["questionTopicId"] = null
        savedStateHandle["questionTopicName"] = null
        savedStateHandle["questionSearch"] = ""
        getStatsUseCase.clearCache()
        _stateFlow.value = HomeViewState()
        observeUsage()
        refreshDashboard(refreshProfile = true)
        loadStats()
        loadTopics()
        loadQuestions()
        loadFavorites()
    }

    fun openPaywall() {
        if (_stateFlow.value.sessionExpired) return
        savedStateHandle["paywallOpen"] = true
        _stateFlow.update { it.copy(paywallOpen = true) }
    }

    fun closePaywall() {
        savedStateHandle["paywallOpen"] = false
        _stateFlow.update { it.copy(paywallOpen = false) }
    }

    fun refreshAfterPractice(questionId: Long? = null) {
        questionId?.let(::refreshPracticedQuestion)
        favoritesDirty = true
        loadDashboard()
        loadStats()
        if (_stateFlow.value.questions is ContentViewState.Ready) loadQuestions()
        if (_stateFlow.value.favorites is ContentViewState.Ready) loadFavorites()
    }

    private fun refreshPracticedQuestion(questionId: Long) {
        questionRefreshJob?.cancel()
        val generation = detailGeneration
        questionRefreshJob = viewModelScope.launch {
            val result = getQuestionsUseCase.question(questionId)
            currentCoroutineContext().ensureActive()
            if (generation != detailGeneration) return@launch
            when (result) {
                is ContentResult.Success -> _stateFlow.update { state ->
                    val detail = state.questionDetail?.takeIf { it.question.id == questionId } ?: return@update state
                    state.copy(questionDetail = detail.copy(
                        question = applySavedChanges(listOf(result.content)).single()))
                }
                is ContentResult.Error -> _uiEventsFlow.emit(UiEvent.Toast(result.message))
                ContentResult.SessionExpired -> _stateFlow.update { it.copy(sessionExpired = true) }
            }
        }
    }

    fun loadDashboard() = refreshDashboard(refreshProfile = false)

    private fun refreshDashboard(refreshProfile: Boolean) {
        if (dashboardJob?.isActive == true) return
        _stateFlow.update { it.copy(dashboard = ContentViewState.Loading) }
        dashboardJob = viewModelScope.launch {
            val result = if (refreshProfile) getDashboardUseCase.refresh() else getDashboardUseCase(Unit)
            if (result is ContentResult.Success) {
                val scope = topicCatalogUseCase.scope(result.content.profile)
                val initializeCatalog = catalogScope != scope
                observeTopicCatalog(scope)
                if (initializeCatalog) refreshTopicCatalog()
            } else if (_stateFlow.value.topicCatalog == ContentViewState.Loading) {
                _stateFlow.update { it.copy(topicCatalog = when (result) {
                    is ContentResult.Error -> ContentViewState.Failed(result.message)
                    else -> ContentViewState.NotLoaded
                }) }
            }
            _stateFlow.update {
                val dashboard = if (result is ContentResult.Success) ContentViewState.Ready(
                    result.content.copy(dashboard = result.content.dashboard.copy(
                        usage = getQuestionAnswerUseCase.currentUsage()))) else result.toViewState()
                it.copy(dashboard = dashboard,
                    sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    private fun observeTopicCatalog(scope: String) {
        if (catalogScope == scope) return
        catalogObserverJob?.cancel()
        catalogRefreshJob?.cancel()
        catalogScope = scope
        _stateFlow.update { it.copy(topicCatalog = ContentViewState.Loading) }
        catalogObserverJob = viewModelScope.launch {
            topicCatalogUseCase.observe(scope).catch { error ->
                _stateFlow.update { it.copy(topicCatalog = ContentViewState.Failed(
                    error.message ?: "Unable to load topics")) }
            }.collect { topics ->
                if (topics != null) _stateFlow.update { it.copy(topicCatalog = ContentViewState.Ready(topics)) }
            }
        }
    }

    fun refreshTopicCatalog() {
        val scope = catalogScope ?: return
        if (catalogRefreshJob?.isActive == true) return
        if (_stateFlow.value.topicCatalog !is ContentViewState.Ready) {
            _stateFlow.update { it.copy(topicCatalog = ContentViewState.Loading) }
        }
        catalogRefreshJob = viewModelScope.launch {
            val result = topicCatalogUseCase.refresh(scope)
            currentCoroutineContext().ensureActive()
            if (scope != catalogScope) return@launch
            when (result) {
                is ContentResult.Error -> {
                    _stateFlow.update { if (it.topicCatalog is ContentViewState.Ready) it
                        else it.copy(topicCatalog = ContentViewState.Failed(result.message)) }
                    _uiEventsFlow.emit(UiEvent.Toast(result.message))
                }
                ContentResult.SessionExpired -> _stateFlow.update { it.copy(sessionExpired = true) }
                is ContentResult.Success -> Unit
            }
        }
    }

    fun selectTopicsFilter(filter: TopicsFilter) {
        if (filter == _stateFlow.value.topicsFilter) return
        savedStateHandle["topicsFilter"] = filter.name
        _stateFlow.update { it.copy(topicsFilter = filter) }
        refreshTopics()
    }

    fun openWeakTopics() {
        val filterChanged = _stateFlow.value.topicsFilter != TopicsFilter.WEAK
        savedStateHandle["homeTab"] = HomeTab.TOPICS.name
        savedStateHandle["topicsFilter"] = TopicsFilter.WEAK.name
        _stateFlow.update { it.copy(tab = HomeTab.TOPICS, topicsFilter = TopicsFilter.WEAK) }
        if (filterChanged) refreshTopics()
        else if (_stateFlow.value.topics == ContentViewState.NotLoaded ||
            _stateFlow.value.topics is ContentViewState.Failed) loadTopics()
    }

    fun loadTopics() {
        if (topicsJob?.isActive == true) return
        refreshTopics()
    }

    private fun refreshTopics() {
        topicsJob?.cancel()
        val generation = ++topicsGeneration
        val filter = _stateFlow.value.topicsFilter
        _stateFlow.update { it.copy(topics = ContentViewState.Loading) }
        topicsJob = viewModelScope.launch {
            val result = getTopicsUseCase(filter)
            currentCoroutineContext().ensureActive()
            if (generation != topicsGeneration) return@launch
            _stateFlow.update {
                it.copy(topics = result.toViewState(),
                    sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }
}
