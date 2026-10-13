package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class PreparationLanguage(
    @SerializedName("code") val code: String,
    @SerializedName("name") val name: String,
    @SerializedName("nativeName") val nativeName: String,
    @SerializedName("contentAvailable") val contentAvailable: Boolean
)

data class PreparationLanguagesResponse(
    @SerializedName("items") val items: List<PreparationLanguage> = emptyList()
)
