package com.oniolla.interviewer.data

data class SessionSnapshot(
    val accessToken: String?,
    val refreshToken: String?,
    val version: Long
)

interface SessionTokenStorage {
    var accessToken: String?
    var refreshToken: String?
    var sessionVersion: Long

    fun snapshot(): SessionSnapshot = synchronized(this) {
        SessionSnapshot(accessToken, refreshToken, sessionVersion)
    }

    fun saveSession(accessToken: String, refreshToken: String): SessionSnapshot = synchronized(this) {
        this.refreshToken = refreshToken
        this.accessToken = accessToken
        sessionVersion++
        snapshot()
    }

    fun updateIfCurrent(expected: SessionSnapshot, accessToken: String, refreshToken: String): SessionSnapshot? =
        synchronized(this) {
            if (snapshot() != expected) return@synchronized null
            this.refreshToken = refreshToken
            this.accessToken = accessToken
            snapshot()
        }

    fun clear() {
        synchronized(this) {
            refreshToken = null
            accessToken = null
            sessionVersion++
        }
    }

    fun clearIfCurrent(expected: SessionSnapshot): Boolean = synchronized(this) {
        if (snapshot() != expected) return@synchronized false
        clear()
        true
    }
}
