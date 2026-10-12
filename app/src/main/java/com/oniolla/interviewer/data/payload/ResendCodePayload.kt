package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

class ResendCodePayload(
    @SerializedName("email") val email: String
)