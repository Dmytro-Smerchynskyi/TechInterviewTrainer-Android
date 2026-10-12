package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

data class RefreshPayload(
    @SerializedName("refreshToken") val refreshToken: String
)
