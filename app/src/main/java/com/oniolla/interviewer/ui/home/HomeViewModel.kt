package com.oniolla.interviewer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.GetTopicsUseCase
import com.oniolla.interviewer.domain.GetQuestionsUseCase
import com.oniolla.interviewer.domain.QuestionsRequest
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getDashboardUseCase: GetDashboardUseCase,
    private val getTopicsUseCase: GetTopicsUseCase,
    private val getQuestionsUseCase: GetQuestionsUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _stateFlow = MutableStateFlow(HomeViewState(
        tab = HomeTab.entries.firstOrNull { it.name == savedStateHandle.get<String>("homeTab") }
            ?: HomeTab.HOME,
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
    private var questionsJob: Job? = null
    private var questionsGeneration = 0
    private val consumedCursors = mutableSetOf<String>()

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
            _stateFlow.value.questions != ContentViewState.NotLoaded) refreshQuestions(debounce = true)
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
        _stateFlow.update { it.copy(questions = ContentViewState.Loading) }
        questionsJob = viewModelScope.launch {
            if (debounce) delay(300)
            val result = getQuestionsUseCase(request)
            currentCoroutineContext().ensureActive()
            if (generation != questionsGeneration) return@launch
            _stateFlow.update {
                val contentState = when (result) {
                    is ContentResult.Success -> ContentViewState.Ready(QuestionsContent(
                        items = result.content.items.distinctBy { item -> item.id },
                        nextCursor = result.content.nextCursor?.takeIf { cursor -> cursor.isNotEmpty() },
                        topics = result.content.topics,
                        access = result.content.access
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
                        items = (current.items + result.content.items).distinctBy { it.id },
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
            _stateFlow.update {
                it.copy(dashboard = result.toViewState(),
                    sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }

    fun loadTopics() {
        if (topicsJob?.isActive == true) return
        _stateFlow.update { it.copy(topics = ContentViewState.Loading) }
        topicsJob = viewModelScope.launch {
            val result = getTopicsUseCase(Unit)
            _stateFlow.update {
                it.copy(topics = result.toViewState(),
                    sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired)
            }
            if (result is ContentResult.Error) _uiEventsFlow.emit(UiEvent.Toast(result.message))
        }
    }
}
