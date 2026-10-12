package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

class SignupPayload(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("name") val firstName: String,
    @SerializedName("registrationToken") val lastName: String? = null
)