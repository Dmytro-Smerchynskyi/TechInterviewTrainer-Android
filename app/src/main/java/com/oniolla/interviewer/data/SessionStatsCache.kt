package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.StatsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionStatsCache @Inject constructor() {
    private data class Entry(val sessionVersion: Long, val response: StatsResponse)
    private val entries = MutableStateFlow<Entry?>(null)
    private var generation = 0L

    @Synchronized fun generation(): Long = generation
    @Synchronized fun update(expectedGeneration: Long, sessionVersion: Long, response: StatsResponse) {
        if (expectedGeneration == generation) entries.value = Entry(sessionVersion, response)
    }
    fun observe(sessionVersion: Long) = entries.map { it?.takeIf { entry -> entry.sessionVersion == sessionVersion }?.response }
    @Synchronized fun clear() {
        generation++
        entries.value = null
    }
}
