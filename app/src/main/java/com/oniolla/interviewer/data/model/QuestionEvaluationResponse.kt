package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

enum class PracticeGrade { JUNIOR, MIDDLE, SENIOR }

data class EvaluationKeyPoint(
    @SerializedName("id") val id: String? = null,
    @SerializedName("description") val description: String,
    @SerializedName("coveragePercent") val coveragePercent: Double = 0.0,
    @SerializedName("comment") val comment: String? = null,
    @SerializedName("status") val status: String? = null
)

data class QuestionEvaluationResponse(
    @SerializedName("id") val id: String,
    @SerializedName("questionId") val questionId: Long? = null,
    @SerializedName("context") val context: String? = null,
    @SerializedName("interviewId") val interviewId: String? = null,
    @SerializedName("interviewQuestionId") val interviewQuestionId: String? = null,
    @SerializedName("userAnswer") val userAnswer: String? = null,
    @SerializedName("grade") val grade: PracticeGrade? = null,
    @SerializedName("score") val score: Int? = null,
    @SerializedName("overallScore") val overallScore: Int? = null,
    @SerializedName("overallFeedback") val overallFeedback: String? = null,
    @SerializedName("strengths") val strengths: List<String> = emptyList(),
    @SerializedName("improvements") val improvements: List<String> = emptyList(),
    @SerializedName("keyPoints") val keyPoints: List<EvaluationKeyPoint> = emptyList(),
    @SerializedName("referenceAnswerAvailable") val referenceAnswerAvailable: Boolean = false,
    @SerializedName("referenceAnswer") val referenceAnswer: String? = null,
    @SerializedName("referenceAnswerLanguage") val referenceAnswerLanguage: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null
) {
    val displayedScore get() = (score ?: overallScore ?: 0).coerceIn(0, 100)
}
