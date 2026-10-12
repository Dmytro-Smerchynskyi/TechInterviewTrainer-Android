package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

class LoginResponse(
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("expiresIn") val expiresIn: Long = 900
)