package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

data class SpecializationPayload(
    @SerializedName("specialization") val specialization: String
)
