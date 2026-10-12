package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

class ForgotPasswordPayload(
    @SerializedName("email") val email: String
)
