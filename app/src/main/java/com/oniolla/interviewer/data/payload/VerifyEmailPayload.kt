package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

data class VerifyEmailPayload(
    @SerializedName("email") val email: String,
    @SerializedName("code") val code: String
)
