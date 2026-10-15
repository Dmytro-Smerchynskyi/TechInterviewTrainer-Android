package com.oniolla.interviewer.ui.home

import com.oniolla.interviewer.data.model.PracticeGrade
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.data.model.QuestionEvaluationResponse

enum class PracticePage { CLOSED, GRADE, ANSWERING, EVALUATING, REVIEW, LIMIT }
enum class AnswerMode { VOICE, TEXT }

data class PracticeViewState(
    val page: PracticePage = PracticePage.CLOSED,
    val previousPage: PracticePage = PracticePage.GRADE,
    val question: QuestionResponse? = null,
    val grade: PracticeGrade = PracticeGrade.MIDDLE,
    val mode: AnswerMode = AnswerMode.VOICE,
    val answer: String = "",
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val recordingStartedAt: Long? = null,
    val voiceLevel: Float = 0f,
    val voiceError: String? = null,
    val answerError: String? = null,
    val reviewsRemaining: Int? = null,
    val result: QuestionEvaluationResponse? = null,
    val sessionExpired: Boolean = false
) {
    val isOpen get() = page != PracticePage.CLOSED
    val isBusy get() = page == PracticePage.EVALUATING
    val canEvaluate get() = page == PracticePage.ANSWERING && !isRecording && !isTranscribing &&
        answer.isNotBlank() && answer.length <= 10_000
}
