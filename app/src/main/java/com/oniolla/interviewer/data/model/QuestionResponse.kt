package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class QuestionTopic(
    @SerializedName("id") val id: Long,
    @SerializedName("slug") val slug: String,
    @SerializedName("name") val name: String,
    @SerializedName("visual") val visual: TopicVisual? = null
)

data class QuestionProgress(
    @SerializedName("questionId") val questionId: Long,
    @SerializedName("attempts") val attempts: Int,
    @SerializedName("lastScore") val lastScore: Double,
    @SerializedName("masteryScore") val masteryScore: Double,
    @SerializedName("lastReviewedAt") val lastReviewedAt: String?,
    @SerializedName("nextReviewAt") val nextReviewAt: String?,
    @SerializedName("mastered") val mastered: Boolean,
    @SerializedName("updatedAt") val updatedAt: String?
)

data class QuestionResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("question") val question: String,
    @SerializedName("specialization") val specialization: String,
    @SerializedName("topic") val topic: QuestionTopic,
    @SerializedName("difficulty") val difficulty: String,
    @SerializedName("minGrade") val minGrade: String,
    @SerializedName("maxGrade") val maxGrade: String,
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("saved") val saved: Boolean = false,
    @SerializedName("progress") val progress: QuestionProgress? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("sortOrder") val sortOrder: Int = 0,
    @SerializedName("language") val language: String? = null,
    @SerializedName("requestedLanguage") val requestedLanguage: String? = null,
    @SerializedName("answerRevealed") val answerRevealed: Boolean = false
)
