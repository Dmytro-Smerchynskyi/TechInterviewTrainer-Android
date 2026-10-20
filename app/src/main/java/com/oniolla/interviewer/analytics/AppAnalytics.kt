package com.oniolla.interviewer.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AnalyticsEvent(val eventName: String) {
    AI_EVALUATION_STARTED("ai_evaluation_started"),
    PAYWALL_OPENED("paywall_opened"),
    ANSWER_REVEALED("answer_revealed"),
    ANSWER_OPENED("answer_opened"),
    INTERVIEW_STARTED("interview_started"),
    INTERVIEW_COMPLETED("interview_completed")
}

interface AppAnalytics {
    fun track(event: AnalyticsEvent, parameters: Map<String, String> = emptyMap())

    object None : AppAnalytics {
        override fun track(event: AnalyticsEvent, parameters: Map<String, String>) = Unit
    }
}

@Singleton
class FirebaseAppAnalytics @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppAnalytics {
    override fun track(event: AnalyticsEvent, parameters: Map<String, String>) {
        // Callers pass only fixed categories, never user content or account credentials.
        val bundle = Bundle().apply { parameters.forEach { (key, value) -> putString(key, value) } }
        FirebaseAnalytics.getInstance(context).logEvent(event.eventName, bundle)
    }
}
