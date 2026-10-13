package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class TopicVisual(
    @SerializedName("iconUrl") val iconUrl: String? = null,
    @SerializedName("iconVersion") val iconVersion: Int = 0,
    @SerializedName("darkColor") val darkColor: String? = null,
    @SerializedName("lightColor") val lightColor: String? = null
)

data class TopicResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("specialization") val specialization: String,
    @SerializedName("slug") val slug: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("sortOrder") val sortOrder: Int = 0,
    @SerializedName("active") val active: Boolean = true,
    @SerializedName("visual") val visual: TopicVisual? = null,
    @SerializedName("answered") val answered: Int = 0,
    @SerializedName("averageScore") val averageScore: Double? = null,
    @SerializedName("mastery") val mastery: Double? = null
)
