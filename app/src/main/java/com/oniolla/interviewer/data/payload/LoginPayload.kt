package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

class LoginPayload(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)