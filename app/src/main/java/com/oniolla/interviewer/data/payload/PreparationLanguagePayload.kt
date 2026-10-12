package com.oniolla.interviewer.data.payload

import com.google.gson.annotations.SerializedName

data class PreparationLanguagePayload(
    @SerializedName("language") val language: String
)
