package com.oniolla.interviewer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.GetTopicsUseCase
import com.oniolla.interviewer.domain.TopicsFilter
import com.oniolla.interviewer.domain.TopicCatalogUseCase
import com.oniolla.interviewer.domain.GetQuestionsUseCase
import com.oniolla.interviewer.domain.QuestionsRequest
import com.oniolla.interviewer.domain.SetQuestionSavedUseCase
import com.oniolla.interviewer.domain.QuestionSavedRequest
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private val setQuestionSavedUseCase: SetQuestionSavedUseCase
) : ViewModel() {
    private val _stateFlow = MutableStateFlow(HomeViewState(
        tab = HomeTab.entries.firstOrNull { it.name == savedStateHandle.get<String>("homeTab") }
            ?: HomeTab.HOME,
        topicsFilter = TopicsFilter.entries.firstOrNull { it.name == savedStateHandle.get<String>("topicsFilter") }
            ?: TopicsFilter.ALL,
        questionSearch = savedStateHandle.get<String>("questionSearch").orEmpty(),
        questionTopic = savedStateHandle.get<Long>("questionTopicId")?.let {
            QuestionTopicFilter(it, savedStateHandle.get<String>("questionTopicName").orEmpty())
        }
    ))
    val stateFlow = _stateFlow.asStateFlow()

    private val _uiEventsFlow = MutableSharedFlow<UiEvent>()
    val uiEventsFlow = _uiEventsFlow.asSharedFlow()
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

    init {
        loadDashboard()
        when (_stateFlow.value.tab) {
            HomeTab.TOPICS -> loadTopics()
            HomeTab.QUESTIONS -> loadQuestions()
            else -> Unit
        }
    }

    fun selectTab(tab: HomeTab) {
        savedStateHandle["homeTab"] = tab.name
        _stateFlow.update { it.copy(tab = tab) }
        if (tab == HomeTab.TOPICS && _stateFlow.value.topics == ContentViewState.NotLoaded) loadTopics()
        if (tab == HomeTab.QUESTIONS && _stateFlow.value.questions == ContentViewState.NotLoaded) loadQuestions()
    }

    // Weak topics on Home retain their existing search-based navigation.
    fun openTopicQuestions(topicName: String) {
        savedStateHandle["questionSearch"] = topicName
        savedStateHandle["homeTab"] = HomeTab.QUESTIONS.name
        _stateFlow.update { it.copy(tab = HomeTab.QUESTIONS, questionSearch = topicName) }
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
        cursor = cursor
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
        val question = (_stateFlow.value.questions as? ContentViewState.Ready)?.content?.items
            ?.firstOrNull { it.id == questionId } ?: return
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
                        is ContentResult.Success -> Unit
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
            val content = (state.questions as? ContentViewState.Ready)?.content ?: return@update state
            state.copy(questions = ContentViewState.Ready(content.copy(items = applySavedChanges(content.items))))
        }
    }

    private fun applySavedChanges(items: List<QuestionResponse>): List<QuestionResponse> = items.map { question ->
        savedQuestionChanges[question.id]?.let { question.copy(saved = it.saved) } ?: question
    }

    fun retryQuestionPage() {
        val content = (_stateFlow.value.questions as? ContentViewState.Ready)?.content ?: return
        if (content.appendError == null) return
        _stateFlow.update { it.copy(questions = ContentViewState.Ready(content.copy(appendError = null))) }
        loadMoreQuestions()
    }

    fun loadDashboard() {
        if (dashboardJob?.isActive == true) return
        _stateFlow.update { it.copy(dashboard = ContentViewState.Loading) }
        dashboardJob = viewModelScope.launch {
            val result = getDashboardUseCase(Unit)
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
                it.copy(dashboard = result.toViewState(),
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
