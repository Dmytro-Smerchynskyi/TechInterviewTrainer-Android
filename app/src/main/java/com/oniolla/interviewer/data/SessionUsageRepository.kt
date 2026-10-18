package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.DashboardUsage
import com.oniolla.interviewer.data.model.SubscriptionResponse
import com.oniolla.interviewer.data.payload.VerifyGooglePurchasePayload
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
    private data class Entry(val sessionVersion: Long, val usage: DashboardUsage,
        val subscription: SubscriptionResponse? = null)
    private val entries = MutableStateFlow<Entry?>(null)
    private val refreshMutex = Mutex()
    private var revision = 0L

    private fun entry(): Entry? = entries.value?.takeIf { it.sessionVersion == tokens.snapshot().version }
    fun current(): DashboardUsage? = synchronized(tokens) { entry()?.usage }
    fun currentSubscription(): SubscriptionResponse? = synchronized(tokens) { entry()?.subscription }

    fun observe() = entries.map { synchronized(tokens) { entry()?.usage } }
    fun observeSubscription() = entries.map { synchronized(tokens) { entry()?.subscription } }
    fun revision(): Long = synchronized(tokens) { revision }

    fun cacheDashboard(expectedRevision: Long, session: SessionSnapshot, usage: DashboardUsage?): DashboardUsage? =
        synchronized(tokens) {
            ensureSession(session)
            // A dashboard started before a subscription refresh must not restore spent credits.
            if (expectedRevision == revision && usage != null) {
                val subscription = entry()?.subscription?.takeIf { it.plan == usage.plan }?.copy(
                    aiEvaluations = usage.aiEvaluations, answerReveals = usage.answerReveals, questions = usage.questions)
                revision++
                entries.value = Entry(session.version, usage, subscription)
            }
            current() ?: usage
        }

    suspend fun get(): DashboardUsage = current() ?: refreshMutex.withLock { current() ?: fetch().usage }
    suspend fun refresh(): DashboardUsage = refreshMutex.withLock { fetch().usage }
    suspend fun subscription(forceRefresh: Boolean = false): SubscriptionResponse = refreshMutex.withLock {
        if (forceRefresh) fetch().subscription!! else currentSubscription() ?: fetch().subscription!!
    }

    suspend fun verifyGooglePurchase(payload: VerifyGooglePurchasePayload, accountId: String,
        sessionVersion: Long): SubscriptionResponse = refreshMutex.withLock {
        val (session, response) = sessionRefresh.authorizedApiCall { session ->
            if (session.version != sessionVersion) throw CancellationException("Billing session changed")
            session to api.verifyGooglePurchase(payload, session)
        }
        currentCoroutineContext().ensureActive()
        check(response.billingAccountId == accountId) { "Purchase verification returned another billing account" }
        cacheSubscription(session, response)
        response
    }

    private suspend fun fetch(): Entry {
        val (session, subscription) = sessionRefresh.authorizedApiCall { it to api.subscription(it) }
        currentCoroutineContext().ensureActive()
        return cacheSubscription(session, subscription)
    }

    private fun cacheSubscription(session: SessionSnapshot, response: SubscriptionResponse): Entry =
        synchronized(tokens) {
            ensureSession(session)
            revision++
            Entry(session.version, response.usage(entry()?.usage?.takeIf { it.plan == response.plan }?.questions), response).also { entries.value = it }
        }

    fun clear() = synchronized(tokens) {
        revision++
        entries.value = null
    }

    private fun ensureSession(session: SessionSnapshot) {
        if (tokens.snapshot().version != session.version) throw CancellationException("Session changed while loading subscription")
    }
}
