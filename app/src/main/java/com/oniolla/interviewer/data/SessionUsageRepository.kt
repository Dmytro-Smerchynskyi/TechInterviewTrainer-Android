package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.DashboardUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionUsageRepository @Inject constructor(
    private val api: Api,
    private val sessionRefresh: SessionRefreshCoordinator,
    private val tokens: SessionTokenStorage
) {
    private data class Entry(val sessionVersion: Long, val usage: DashboardUsage)
    private val entries = MutableStateFlow<Entry?>(null)
    private val refreshMutex = Mutex()
    private var revision = 0L

    fun current(): DashboardUsage? = synchronized(tokens) {
        entries.value?.takeIf { it.sessionVersion == tokens.snapshot().version }?.usage
    }

    fun observe() = entries.map { entry ->
        synchronized(tokens) {
            entry?.takeIf { it.sessionVersion == tokens.snapshot().version }?.usage
        }
    }

    fun revision(): Long = synchronized(tokens) { revision }

    fun cacheDashboard(expectedRevision: Long, session: SessionSnapshot, usage: DashboardUsage?): DashboardUsage? =
        synchronized(tokens) {
            ensureSession(session)
            // A dashboard started before a usage refresh must not restore spent credits.
            if (expectedRevision == revision && usage != null) {
                revision++
                entries.value = Entry(session.version, usage)
            }
            current() ?: usage
        }

    suspend fun get(): DashboardUsage = current() ?: refreshMutex.withLock {
        current() ?: fetch()
    }

    suspend fun refresh(): DashboardUsage = refreshMutex.withLock { fetch() }

    private suspend fun fetch(): DashboardUsage {
        val (session, usage) = sessionRefresh.authorizedApiCall { it to api.accountUsage(it) }
        currentCoroutineContext().ensureActive()
        return synchronized(tokens) {
            ensureSession(session)
            revision++
            entries.value = Entry(session.version, usage)
            usage
        }
    }

    fun clear() = synchronized(tokens) {
        revision++
        entries.value = null
    }

    private fun ensureSession(session: SessionSnapshot) {
        if (tokens.snapshot().version != session.version) {
            throw CancellationException("Session changed while loading usage")
        }
    }
}
