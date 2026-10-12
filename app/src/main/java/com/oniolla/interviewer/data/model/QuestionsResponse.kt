package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class QuestionTopicAccess(
    @SerializedName("topicId") val topicId: Long,
    @SerializedName("totalCount") val totalCount: Int,
    @SerializedName("visibleCount") val visibleCount: Int,
    @SerializedName("lockedCount") val lockedCount: Int,
    @SerializedName("limitedByPlan") val limitedByPlan: Boolean
)

data class QuestionsResponse(
    @SerializedName("items") val items: List<QuestionResponse> = emptyList(),
    @SerializedName("nextCursor") val nextCursor: String? = null,
    @SerializedName("topics") val topics: List<QuestionTopicAccess> = emptyList(),
    @SerializedName("access") val access: QuestionTopicAccess? = null
)
