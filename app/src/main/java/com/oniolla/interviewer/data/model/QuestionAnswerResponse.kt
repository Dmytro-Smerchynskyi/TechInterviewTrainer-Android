package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class QuestionAnswerResponse(
    @SerializedName("questionId") val questionId: Long,
    @SerializedName("referenceAnswer") val referenceAnswer: String,
    @SerializedName("language") val language: String?,
    @SerializedName("requestedLanguage") val requestedLanguage: String?
)
