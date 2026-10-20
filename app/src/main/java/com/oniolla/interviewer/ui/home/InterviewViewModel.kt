package com.oniolla.interviewer.ui.home

import com.oniolla.interviewer.analytics.AnalyticsEvent
import com.oniolla.interviewer.analytics.AppAnalytics
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class InterviewViewModel @Inject constructor(
    private val useCase: InterviewUseCase,
    private val saved: SavedStateHandle,
    private val analytics: AppAnalytics = AppAnalytics.None
) : ViewModel() {
    private val _state = MutableStateFlow(InterviewViewState(practice = PracticeViewState(
        grade = PracticeGrade.entries.firstOrNull { it.name == saved.get<String>("interviewGrade") }
            ?: PracticeGrade.MIDDLE)))
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()
    private var historyJob: Job? = null
    private var operation: Job? = null
    private var summaryJob: Job? = null
    private var uncertainCreate = false
    private var uncertainAnswer = false
    private var changed = false
    private var refreshPending = false

    init {
        refreshHistory()
        viewModelScope.launch { useCase.usage().collect { usage ->
            _state.update {
                val remaining = usage?.aiEvaluations?.remaining
                val unlocked = it.page == InterviewPage.LIMIT && remaining != null && remaining > 0
                it.copy(page = if (unlocked) InterviewPage.ANSWERING else it.page,
                    practice = it.practice.copy(reviewsRemaining = remaining,
                        page = if (unlocked) PracticePage.ANSWERING else it.practice.page))
            }
        } }
        saved.get<String>("interviewId")?.let { resume(it, restoreDraft = true) }
    }

    fun refreshHistory() {
        if (historyJob?.isActive == true) return
        _state.update { it.copy(history = ContentViewState.Loading) }
        historyJob = viewModelScope.launch {
            val result = useCase.history()
            _state.update { it.copy(history = result.toViewState(),
                sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired) }
        }
    }

    fun open() {
        if (_state.value.isBusy || _state.value.isOpen) return
        when (val history = _state.value.history) {
            is ContentViewState.Ready -> if (history.content != null) resume(history.content.id)
                else _state.update { it.copy(page = InterviewPage.SETUP, error = null,
                    practice = PracticeViewState(grade = it.practice.grade)) }
            is ContentViewState.Failed -> refreshHistory()
            else -> Unit
        }
    }

    fun selectGrade(grade: PracticeGrade) {
        if (_state.value.page != InterviewPage.SETUP || _state.value.isBusy) return
        saved["interviewGrade"] = grade.name
        _state.update { it.copy(practice = it.practice.copy(grade = grade)) }
    }

    fun create() {
        if (_state.value.page != InterviewPage.SETUP || _state.value.isBusy) return
        _state.update { it.copy(actionLoading = true, error = null) }
        operation = viewModelScope.launch {
            // A lost creation response must not create a second interview on Retry.
            if (uncertainCreate) {
                when (val history = useCase.history()) {
                    is ContentResult.Success -> history.content?.let {
                        uncertainCreate = false
                        loadSession(it.id, false)
                        return@launch
                    }
                    else -> { fail(history); return@launch }
                }
            }
            when (val result = useCase.create(_state.value.practice.grade)) {
                is ContentResult.Success -> {
                    uncertainCreate = false
                    changed = true
                    analytics.track(AnalyticsEvent.INTERVIEW_STARTED, mapOf("grade" to result.content.grade))
                    acceptInterview(result.content)
                    loadCurrent(result.content)
                }
                else -> { uncertainCreate = true; fail(result) }
            }
        }
    }

    private fun resume(id: String, restoreDraft: Boolean = false) {
        if (_state.value.isBusy) return
        _state.update { it.copy(page = InterviewPage.LOADING, error = null) }
        operation = viewModelScope.launch { loadSession(id, restoreDraft) }
    }

    private fun acceptInterview(interview: InterviewResponse) {
        saved["interviewId"] = interview.id
        val active = if (interview.inProgress) LastInterview(interview.id, interview.grade, interview.status,
            interview.overallScore, interview.startedAt, interview.finishedAt, null) else null
        _state.update { it.copy(interview = interview, history = ContentViewState.Ready(active)) }
    }

    private suspend fun loadSession(id: String, restoreDraft: Boolean) {
        when (val result = useCase.get(id)) {
            is ContentResult.Success -> {
                acceptInterview(result.content)
                if (!result.content.inProgress) showSummary(result.content)
                else loadCurrent(result.content, restoreDraft)
            }
            else -> { _state.update { it.copy(page = InterviewPage.ERROR) }; fail(result) }
        }
    }

    private suspend fun loadCurrent(interview: InterviewResponse, restoreDraft: Boolean = false) {
        when (val result = useCase.current(interview.id)) {
            is ContentResult.Success -> {
                val current = result.content
                if (current.position == null || current.question == null) {
                    _state.update { it.copy(page = InterviewPage.READY_TO_FINISH, actionLoading = false, error = null) }
                } else {
                    val draft = if (restoreDraft && saved.get<Long>("interviewQuestionId") == current.question.id)
                        saved.get<String>("interviewAnswer").orEmpty() else ""
                    saved["interviewQuestionId"] = current.question.id
                    saved["interviewAnswer"] = draft
                    uncertainAnswer = false
                    _state.update { it.copy(page = InterviewPage.ANSWERING, position = current.position,
                        actionLoading = false, error = null, practice = PracticeViewState(
                            page = PracticePage.ANSWERING, question = current.question,
                            grade = PracticeGrade.entries.firstOrNull { it.name == interview.grade } ?: it.practice.grade,
                            answer = draft, reviewsRemaining = useCase.currentUsage()?.aiEvaluations?.remaining)) }
                }
            }
            else -> { _state.update { it.copy(page = InterviewPage.ERROR) }; fail(result) }
        }
    }

    fun retry() {
        if (_state.value.isBusy) return
        val id = _state.value.interview?.id ?: saved.get<String>("interviewId")
        if (id != null) resume(id, restoreDraft = true) else close()
    }

    fun updateAnswer(value: String) {
        if (_state.value.page != InterviewPage.ANSWERING || _state.value.isBusy) return
        val answer = value.take(10_000)
        saved["interviewAnswer"] = answer
        _state.update { it.copy(error = null, practice = it.practice.copy(answer = answer, answerError = null)) }
    }
    fun setMode(mode: AnswerMode) {
        if (_state.value.page != InterviewPage.ANSWERING || _state.value.isBusy) return
        _state.update { it.copy(practice = it.practice.copy(mode = mode, isRecording = false,
            isTranscribing = false, voiceError = null, voiceLevel = 0f)) }
    }
    fun recordingStarted(at: Long) = updateVoice { it.copy(isRecording = true, isTranscribing = false,
        recordingStartedAt = at, voiceError = null) }
    fun transcriptionStarted() = updateVoice { it.copy(isRecording = false, isTranscribing = true, voiceLevel = 0f) }
    fun recordingFinished(error: String?) = updateVoice { it.copy(isRecording = false, isTranscribing = false,
        voiceError = error, voiceLevel = 0f) }
    fun voiceLevel(level: Float) = updateVoice { it.copy(voiceLevel = level.coerceIn(0f, 1f)) }
    private fun updateVoice(transform: (PracticeViewState) -> PracticeViewState) {
        if (_state.value.page == InterviewPage.ANSWERING)
            _state.update { it.copy(practice = transform(it.practice)) }
    }

    fun evaluate() {
        val state = _state.value
        val id = state.interview?.id ?: return
        val questionId = state.practice.question?.id ?: return
        if (state.page != InterviewPage.ANSWERING || state.isBusy || !state.practice.canEvaluate) return
        _state.update { it.copy(page = InterviewPage.EVALUATING, error = null,
            practice = it.practice.copy(page = PracticePage.EVALUATING, answerError = null)) }
        operation = viewModelScope.launch {
            if (uncertainAnswer) {
                when (val server = useCase.get(id)) {
                    is ContentResult.Success -> {
                        val previous = server.content.questions.firstOrNull { it.questionId == questionId }?.evaluation
                        if (previous != null) {
                            acceptInterview(server.content)
                            acceptEvaluation(previous)
                            return@launch
                        }
                    }
                    else -> { answerFailure(server); return@launch }
                }
            }
            analytics.track(AnalyticsEvent.AI_EVALUATION_STARTED, mapOf(
                "context" to "interview", "grade" to state.practice.grade.name,
                "input_mode" to if (state.practice.mode == AnswerMode.VOICE) "voice" else "text"))
            when (val result = useCase.answer(id, questionId, state.practice.answer)) {
                is EvaluationResult.Success -> acceptEvaluation(result.evaluation)
                is EvaluationResult.LimitReached -> {
                    uncertainAnswer = false
                    _state.update { it.copy(page = InterviewPage.LIMIT,
                        practice = it.practice.copy(page = PracticePage.LIMIT)) }
                    refreshUsage()
                }
                is EvaluationResult.Error -> {
                    uncertainAnswer = true
                    _state.update { it.copy(page = InterviewPage.ANSWERING,
                        practice = it.practice.copy(page = PracticePage.ANSWERING, answerError = result.message)) }
                    _events.emit(UiEvent.Toast(result.message))
                    refreshUsage()
                }
                EvaluationResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
            }
        }
    }

    private fun acceptEvaluation(evaluation: QuestionEvaluationResponse) {
        changed = true
        uncertainAnswer = false
        _state.update { state -> state.copy(page = InterviewPage.REVIEW,
            interview = state.interview?.let { interview -> interview.copy(questions = interview.questions.map {
                if (it.position == state.position) it.copy(answer = evaluation.userAnswer ?: state.practice.answer,
                    score = evaluation.displayedScore.toDouble(), evaluation = evaluation) else it
            }) },
            practice = state.practice.copy(page = PracticePage.REVIEW, result = evaluation)) }
        refreshUsage()
    }

    private fun refreshUsage() {
        viewModelScope.launch {
            if (useCase.refreshUsage() == ContentResult.SessionExpired) _state.update { it.copy(sessionExpired = true) }
        }
    }

    fun next() {
        val state = _state.value
        if (state.isBusy || state.page != InterviewPage.REVIEW) return
        if (state.isLast) finish() else {
            _state.update { it.copy(page = InterviewPage.LOADING, error = null) }
            operation = viewModelScope.launch { state.interview?.let { loadCurrent(it) } }
        }
    }

    fun finish() {
        val state = _state.value
        val id = state.interview?.id ?: return
        if (state.isBusy || state.page !in setOf(InterviewPage.REVIEW, InterviewPage.LIMIT, InterviewPage.READY_TO_FINISH)) return
        _state.update { it.copy(actionLoading = true, error = null) }
        operation = viewModelScope.launch {
            when (val result = useCase.finish(id)) {
                is ContentResult.Success -> {
                    changed = true
                    analytics.track(AnalyticsEvent.INTERVIEW_COMPLETED, mapOf(
                        "grade" to result.content.grade,
                        "completion" to if (result.content.questions.all { it.evaluation != null }) "full" else "partial"))
                    acceptInterview(result.content)
                    showSummary(result.content)
                    refreshHistory()
                }
                else -> fail(result)
            }
        }
    }

    private fun showSummary(interview: InterviewResponse) {
        _state.update { it.copy(page = InterviewPage.SUMMARY, interview = interview, actionLoading = false,
            error = null, summary = ContentViewState.Loading) }
        loadSummary()
    }

    fun loadSummary() {
        if (summaryJob?.isActive == true) return
        _state.update { it.copy(summary = ContentViewState.Loading) }
        summaryJob = viewModelScope.launch {
            val result = useCase.summary()
            _state.update { it.copy(summary = result.toViewState(),
                sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired) }
        }
    }

    private fun answerFailure(result: ContentResult<*>) {
        _state.update { it.copy(page = InterviewPage.ANSWERING, practice = it.practice.copy(page = PracticePage.ANSWERING)) }
        fail(result)
    }

    private fun fail(result: ContentResult<*>) {
        val message = (result as? ContentResult.Error)?.message
        _state.update { it.copy(actionLoading = false, error = message,
            practice = it.practice.copy(answerError = if (it.page == InterviewPage.ANSWERING) message else it.practice.answerError),
            sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired) }
    }

    fun close() {
        if (_state.value.isBusy || !_state.value.isOpen) return
        refreshPending = refreshPending || changed
        changed = false
        saved.remove<String>("interviewId")
        saved.remove<String>("interviewAnswer")
        saved.remove<Long>("interviewQuestionId")
        _state.update { it.copy(previousPage = it.page, page = InterviewPage.CLOSED) }
    }
    fun takeContentRefresh() = refreshPending.also { refreshPending = false }
    fun stopSessionWork() { viewModelScope.coroutineContext.cancelChildren() }
}
