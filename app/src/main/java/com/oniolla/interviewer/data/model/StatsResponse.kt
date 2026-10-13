package com.oniolla.interviewer.data.model

import com.google.gson.annotations.SerializedName

data class StatsResponse(
    @SerializedName("topics") val topics: List<WeakTopic> = emptyList(),
    @SerializedName("weakTopics") val weakTopics: List<WeakTopic> = emptyList(),
    @SerializedName("weakKeyPoints") val weakKeyPoints: List<WeakKeyPoint> = emptyList(),
    @SerializedName("readiness") val readiness: Readiness = Readiness(),
    @SerializedName("serverTime") val serverTime: String? = null,
    @SerializedName("timeZone") val timeZone: String = "UTC",
    @SerializedName("weekStart") val weekStart: String? = null,
    @SerializedName("weekEnd") val weekEnd: String? = null,
    @SerializedName("summary") val summary: StatsSummary = StatsSummary(),
    @SerializedName("studyProgress") val studyProgress: List<StudyProgressDay> = emptyList()
)

data class StatsSummary(
    @SerializedName("answered") val answered: Int = 0,
    @SerializedName("streakDays") val streakDays: Int = 0,
    @SerializedName("dueCount") val dueCount: Int = 0,
    @SerializedName("averageStudySeconds") val averageStudySeconds: Double? = null
)

data class StudyProgressDay(
    @SerializedName("date") val date: String,
    @SerializedName("answered") val answered: Int = 0
)

data class WeakKeyPoint(
    @SerializedName("description") val description: String,
    @SerializedName("topicId") val topicId: Long,
    @SerializedName("averageCoverage") val averageCoverage: Double,
    @SerializedName("attempts") val attempts: Int
)
