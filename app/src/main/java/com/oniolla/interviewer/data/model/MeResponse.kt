package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class MeResponse(
    @SerializedName("id") val id: String,
    @SerializedName("email") val email: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("emailVerified") val emailVerified: Boolean,
    @SerializedName("specialization") val specialization: String?,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String,
    @SerializedName("onboardingCompleted") val onboardingCompleted: Boolean,
    @SerializedName("linkedProviders") val linkedProviders: List<String>,
    @SerializedName("preparationLanguage") val preparationLanguage: String?
)
