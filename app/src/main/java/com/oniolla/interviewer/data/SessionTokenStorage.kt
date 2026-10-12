package com.oniolla.interviewer.data

interface SessionTokenStorage {
    var accessToken: String?
    var refreshToken: String?
    fun clear()
}
