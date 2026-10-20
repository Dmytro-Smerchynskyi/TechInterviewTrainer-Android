package com.oniolla.interviewer.data

import com.oniolla.interviewer.analytics.AnalyticsEvent
import com.oniolla.interviewer.analytics.AppAnalytics

internal class RecordingAnalytics : AppAnalytics {
    data class Event(val type: AnalyticsEvent, val parameters: Map<String, String>)
    val events = mutableListOf<Event>()

    override fun track(event: AnalyticsEvent, parameters: Map<String, String>) {
        events += Event(event, parameters.toMap())
    }

    fun count(event: AnalyticsEvent) = events.count { it.type == event }
}
