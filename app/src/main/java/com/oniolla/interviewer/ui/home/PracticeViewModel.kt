package com.oniolla.interviewer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.oniolla.interviewer.data.model.PracticeGrade
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.EvaluateQuestionRequest
import com.oniolla.interviewer.domain.EvaluateQuestionUseCase
import com.oniolla.interviewer.domain.EvaluationResult
import com.oniolla.interviewer.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val evaluate: EvaluateQuestionUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val gson = Gson()
    private val restoredQuestion = savedState.get<String>("practiceQuestion")?.let {
        runCatching { gson.fromJson(it, QuestionResponse::class.java) }.getOrNull()
    }
    private val _state = MutableStateFlow(PracticeViewState(
        question = restoredQuestion,
        page = if (restoredQuestion == null) PracticePage.CLOSED else
            if (savedState.get<String>("practicePage") == PracticePage.GRADE.name) PracticePage.GRADE
            else PracticePage.ANSWERING,
        grade = PracticeGrade.entries.firstOrNull { it.name == savedState.get<String>("practiceGrade") }
            ?: PracticeGrade.MIDDLE,
        answer = savedState.get<String>("practiceAnswer").orEmpty().take(10_000),
        mode = AnswerMode.entries.firstOrNull { it.name == savedState.get<String>("practiceMode") }
            ?: AnswerMode.VOICE,
        reviewsRemaining = evaluate.currentUsage()?.aiEvaluations?.remaining
    ))
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()
    private var evaluationJob: Job? = null
    private var refreshPending = false

    init {
        viewModelScope.launch { evaluate.cachedUsage().collect { usage ->
            _state.update { it.copy(reviewsRemaining = usage?.aiEvaluations?.remaining?.coerceAtLeast(0)) }
        } }
    }

    fun open(question: QuestionResponse) {
        if (_state.value.isBusy) return
        savedState["practiceQuestion"] = gson.toJson(question)
        savedState["practiceAnswer"] = ""
        savedState["practiceMode"] = AnswerMode.VOICE.name
        savedState["practicePage"] = PracticePage.GRADE.name
        _state.value = PracticeViewState(page = PracticePage.GRADE, question = question,
            grade = _state.value.grade,
            reviewsRemaining = evaluate.currentUsage()?.aiEvaluations?.remaining)
    }

    fun selectGrade(grade: PracticeGrade) {
        if (_state.value.page != PracticePage.GRADE) return
        savedState["practiceGrade"] = grade.name
        _state.update { it.copy(grade = grade) }
    }

    fun continueToAnswer() {
        if (_state.value.page != PracticePage.GRADE) return
        savedState["practicePage"] = PracticePage.ANSWERING.name
        _state.update { it.copy(page = PracticePage.ANSWERING) }
    }

    fun setMode(mode: AnswerMode) {
        if (_state.value.page != PracticePage.ANSWERING) return
        savedState["practiceMode"] = mode.name
        _state.update { it.copy(mode = mode, isRecording = false, isTranscribing = false,
            voiceError = null, voiceLevel = 0f) }
    }

    fun updateAnswer(answer: String) {
        if (_state.value.page != PracticePage.ANSWERING) return
        val text = answer.take(10_000)
        savedState["practiceAnswer"] = text
        _state.update { it.copy(answer = text, answerError = null) }
    }

    fun recordingStarted(startedAt: Long) {
        if (_state.value.page != PracticePage.ANSWERING || _state.value.mode != AnswerMode.VOICE) return
        _state.update { it.copy(isRecording = true, isTranscribing = false,
            recordingStartedAt = startedAt, voiceError = null) }
    }

    fun transcriptionStarted() {
        if (_state.value.page != PracticePage.ANSWERING) return
        _state.update { it.copy(isRecording = false, isTranscribing = true, voiceLevel = 0f) }
    }

    fun recordingFinished(error: String? = null) {
        _state.update { it.copy(isRecording = false, isTranscribing = false, voiceError = error, voiceLevel = 0f) }
    }

    fun updateVoiceLevel(level: Float) {
        if (_state.value.isRecording) _state.update { it.copy(voiceLevel = level.coerceIn(0f, 1f)) }
    }

    fun evaluateAnswer() {
        val current = _state.value
        val question = current.question ?: return
        if (!current.canEvaluate || evaluationJob?.isActive == true) return
        val request = EvaluateQuestionRequest(question.id, current.answer, current.grade)
        _state.update { it.copy(page = PracticePage.EVALUATING, answerError = null, voiceError = null) }
        evaluationJob = viewModelScope.launch {
            val result = evaluate(request)
            currentCoroutineContext().ensureActive()
            when (result) {
                is EvaluationResult.Success -> {
                    refreshPending = true
                    _state.update { it.copy(page = PracticePage.REVIEW, result = result.evaluation) }
                }
                is EvaluationResult.LimitReached -> _state.update { it.copy(page = PracticePage.LIMIT) }
                is EvaluationResult.Error -> _state.update {
                    it.copy(page = PracticePage.ANSWERING, answerError = result.message)
                }
                EvaluationResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
            }
            if (result != EvaluationResult.SessionExpired) {
                viewModelScope.launch {
                    if (evaluate.refreshUsage() == ContentResult.SessionExpired) {
                        _state.update { it.copy(sessionExpired = true) }
                    }
                }
            }
            if (result is EvaluationResult.Error) _events.emit(UiEvent.Toast(result.message))
        }
    }

    /** Refresh progress once per successful attempt, including after configuration changes. */
    fun takeContentRefresh(): Boolean = refreshPending.also { refreshPending = false }

    fun back() {
        val current = _state.value
        if (current.isBusy) return
        if (current.page == PracticePage.ANSWERING) {
            savedState["practicePage"] = PracticePage.GRADE.name
            _state.update { it.copy(page = PracticePage.GRADE, isRecording = false, isTranscribing = false) }
        } else close()
    }

    fun close() {
        if (!_state.value.isOpen || _state.value.isBusy) return
        savedState.remove<String>("practiceQuestion")
        savedState.remove<String>("practiceAnswer")
        savedState.remove<String>("practicePage")
        _state.update { it.copy(previousPage = it.page, page = PracticePage.CLOSED,
            isRecording = false, isTranscribing = false) }
    }

    fun stopSessionWork() { viewModelScope.coroutineContext.cancelChildren() }
}
