package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class InterviewHistoryResponse(
    @SerializedName("items") val items: List<LastInterview> = emptyList(),
    @SerializedName("nextCursor") val nextCursor: String? = null
)

data class InterviewResponse(
    @SerializedName("id") val id: String,
    @SerializedName("specialization") val specialization: String,
    @SerializedName("grade") val grade: String,
    @SerializedName("status") val status: String,
    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("finishedAt") val finishedAt: String? = null,
    @SerializedName("overallScore") val overallScore: Double? = null,
    @SerializedName("questions") val questions: List<InterviewQuestion> = emptyList()
) {
    val inProgress get() = status == "IN_PROGRESS"
}

data class InterviewQuestion(
    @SerializedName("position") val position: Int,
    @SerializedName("questionId") val questionId: Long,
    @SerializedName("question") val question: String? = null,
    @SerializedName("answerRevealed") val answerRevealed: Boolean = false,
    @SerializedName("language") val language: String? = null,
    @SerializedName("requestedLanguage") val requestedLanguage: String? = null,
    @SerializedName("answer") val answer: String? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("evaluation") val evaluation: QuestionEvaluationResponse? = null
)

data class CurrentInterviewQuestion(
    @SerializedName("position") val position: Int? = null,
    @SerializedName("question") val question: QuestionResponse? = null
)
