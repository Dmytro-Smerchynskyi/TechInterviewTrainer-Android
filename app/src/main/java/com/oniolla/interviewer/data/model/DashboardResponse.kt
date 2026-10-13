package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class DashboardResponse(
    @SerializedName("readiness") val readiness: Readiness = Readiness(),
    @SerializedName("dueCount") val dueCount: Int = 0,
    @SerializedName("todayReview") val todayReview: List<QuestionResponse> = emptyList(),
    @SerializedName("weakTopics") val weakTopics: List<WeakTopic> = emptyList(),
    @SerializedName("lastInterview") val lastInterview: LastInterview? = null,
    @SerializedName("usage") val usage: DashboardUsage? = null
)

data class Readiness(
    @SerializedName("score") val score: Double = 0.0,
    @SerializedName("answered") val answered: Int = 0,
    @SerializedName("practicedTopics") val practicedTopics: Int = 0,
    @SerializedName("totalTopics") val totalTopics: Int = 0,
    @SerializedName("gradePerformance") val gradePerformance: List<GradePerformance> = emptyList(),
    @SerializedName("label") val label: String? = null,
    @SerializedName("previousWeekScore") val previousWeekScore: Double? = null,
    @SerializedName("changeSinceLastWeekPoints") val changeSinceLastWeekPoints: Double? = null,
    @SerializedName("comparedAt") val comparedAt: String? = null
)

data class GradePerformance(
    @SerializedName("grade") val grade: String,
    @SerializedName("answered") val answered: Int,
    @SerializedName("averageScore") val averageScore: Double
)

data class WeakTopic(
    @SerializedName("topicId") val topicId: Long,
    @SerializedName("name") val name: String,
    @SerializedName("answered") val answered: Int = 0,
    @SerializedName("averageScore") val averageScore: Double = 0.0,
    @SerializedName("mastery") val mastery: Double = 0.0,
    @SerializedName("lastActivityAt") val lastActivityAt: String? = null,
    @SerializedName("visual") val visual: TopicVisual? = null
)

data class LastInterview(
    @SerializedName("id") val id: String,
    @SerializedName("grade") val grade: String,
    @SerializedName("status") val status: String,
    @SerializedName("overallScore") val overallScore: Double?,
    @SerializedName("startedAt") val startedAt: String?,
    @SerializedName("finishedAt") val finishedAt: String?,
    @SerializedName("createdAt") val createdAt: String?
)

data class DashboardUsage(
    @SerializedName("plan") val plan: String,
    @SerializedName("answerReveals") val answerReveals: AnswerRevealsUsage?,
    @SerializedName("aiEvaluations") val aiEvaluations: AiEvaluationsUsage?,
    @SerializedName("questions") val questions: QuestionsUsage?
)

data class AnswerRevealsUsage(
    @SerializedName("used") val used: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("remaining") val remaining: Int
)

data class AiEvaluationsUsage(
    @SerializedName("used") val used: Int,
    @SerializedName("reserved") val reserved: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("remaining") val remaining: Int,
    @SerializedName("periodEndsAt") val periodEndsAt: String?,
    @SerializedName("resetAt") val resetAt: String?
)

data class QuestionsUsage(
    @SerializedName("unlimited") val unlimited: Boolean,
    @SerializedName("limitPerTopic") val limitPerTopic: Int?
)
