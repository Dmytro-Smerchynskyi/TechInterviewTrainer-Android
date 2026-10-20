package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.payload.RefreshPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRefreshCoordinator internal constructor(
    private val tokenStorage: SessionTokenStorage,
    private val api: Api,
    private val refreshScope: CoroutineScope
) {
    @Inject constructor(tokenStorage: SessionTokenStorage, api: Api) :
        this(tokenStorage, api, CoroutineScope(SupervisorJob() + Dispatchers.IO))

    private data class CallSession(val tokens: SessionSnapshot, val refreshNumber: Long)
    private data class RefreshAttempt(
        val tokens: SessionSnapshot,
        val number: Long,
        val result: Deferred<SessionSnapshot>
    )

    private val mutex = Mutex()
    private var refreshNumber = 0L
    private var lastAttempt: RefreshAttempt? = null

    private suspend fun captureSession() = mutex.withLock {
        val tokens = tokenStorage.snapshot()
        val running = lastAttempt?.takeIf { it.tokens == tokens && !it.result.isCompleted }
        CallSession(tokens, running?.let { it.number - 1 } ?: refreshNumber)
    }

    suspend fun restoreSession(): Boolean {
        val session = captureSession()
        if (session.tokens.refreshToken.isNullOrBlank()) {
            expireSession(session.tokens)
            return false
        }
        val restored = refresh(session)
        currentCoroutineContext().ensureActive()
        ensureSession(restored)
        return true
    }

    suspend fun <T> authorizedApiCall(call: suspend (SessionSnapshot) -> T): T {
        val session = captureSession()
        try {
            return callOnce(session.tokens, call)
        } catch (error: ApiException.Unauthorized) {
            ensureSession(session.tokens)
            if (error.customError.code != "UNAUTHORIZED") {
                expireSession(session.tokens)
                throw error
            }
        }

        val renewed = refresh(session)
        currentCoroutineContext().ensureActive()
        try {
            return callOnce(renewed, call)
        } catch (error: ApiException.Unauthorized) {
            expireSession(renewed)
            throw error
        }
    }

    private suspend fun <T> callOnce(tokens: SessionSnapshot, call: suspend (SessionSnapshot) -> T): T {
        ensureSession(tokens)
        return try {
            apiCall { call(tokens) }.also { ensureSession(tokens) }
        } catch (error: ApiException) {
            ensureSession(tokens)
            // Rejected proof of identity belongs to this action, not to the bearer session.
            // Apply before both the initial-call and post-refresh Unauthorized handlers.
            if (error is ApiException.Unauthorized && error.customError.code in
                setOf("REAUTHENTICATION_REQUIRED", "REAUTHENTICATION_FAILED")) {
                throw ApiException.ReauthenticationRequired(error.customError)
            }
            throw error
        }
    }

    private suspend fun refresh(session: CallSession): SessionSnapshot {
        val attempt = mutex.withLock {
            val current = tokenStorage.snapshot()
            ensureSession(session.tokens)
            if (current != session.tokens && !current.accessToken.isNullOrBlank()) {
                return current
            }
            if (current.refreshToken.isNullOrBlank()) {
                expireSession(current)
                throw ApiException.Unauthorized(CustomError("UNAUTHORIZED", "Session expired"))
            }
            val previous = lastAttempt
            // Requests started before this attempt share its result, including a failed result.
            // A new request after a transient failure may start a fresh attempt.
            if (previous != null && previous.tokens == current &&
                (!previous.result.isCompleted || previous.number > session.refreshNumber)) {
                previous
            } else {
                RefreshAttempt(current, ++refreshNumber,
                    refreshScope.async(start = CoroutineStart.LAZY) { performRefresh(current) })
                    .also { lastAttempt = it }
            }
        }
        return try {
            attempt.result.await().also { ensureSession(it) }
        } catch (error: ApiException) {
            if (error is ApiException.Unauthorized) {
                val current = tokenStorage.snapshot()
                // A rejected refresh cleared this version. A subsequent login owns a newer version.
                if (current.version != attempt.tokens.version + 1 ||
                    current.accessToken != null || current.refreshToken != null) {
                    throw CancellationException("Session changed after refresh")
                }
            } else {
                ensureSession(attempt.tokens)
            }
            throw error
        }
    }

    private suspend fun performRefresh(expected: SessionSnapshot): SessionSnapshot {
        ensureSession(expected)
        val response = try {
            // This raw call never enters authorizedApiCall.
            apiCall { api.refresh(RefreshPayload(requireNotNull(expected.refreshToken))) }
        } catch (error: ApiException) {
            ensureSession(expected)
            if (error is ApiException.Unauthorized) expireSession(expected)
            throw error
        }
        currentCoroutineContext().ensureActive()
        ensureSession(expected)
        if (response.accessToken.isBlank() || response.refreshToken.isBlank()) {
            throw ApiException.Unknown(CustomError(message = "Invalid refresh response"))
        }
        return tokenStorage.updateIfCurrent(expected, response.accessToken, response.refreshToken)
            ?: throw CancellationException("Session changed during refresh")
    }

    private fun ensureSession(expected: SessionSnapshot) {
        if (tokenStorage.snapshot().version != expected.version) {
            throw CancellationException("Session changed")
        }
    }

    private fun expireSession(expected: SessionSnapshot) {
        ensureSession(expected)
        if (!tokenStorage.clearIfCurrent(expected)) {
            throw CancellationException("Session credentials changed")
        }
    }
}
