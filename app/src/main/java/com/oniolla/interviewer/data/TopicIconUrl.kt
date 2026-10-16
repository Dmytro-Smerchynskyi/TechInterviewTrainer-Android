package com.oniolla.interviewer.data

import com.oniolla.interviewer.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal val API_BASE_URL = BuildConfig.API_BASE_URL

internal fun resolveTopicIconUrl(
    value: String?,
    baseUrl: String = API_BASE_URL,
    rewriteLoopback: Boolean = BuildConfig.DEBUG
): String? {
    val url = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val apiUrl = baseUrl.toHttpUrlOrNull()
    val resolved = url.toHttpUrlOrNull() ?: apiUrl?.resolve(url) ?: return null
    // Only the local debug backend needs its loopback origin rewritten.
    return if (rewriteLoopback && apiUrl != null && resolved.host in setOf("localhost", "127.0.0.1", "::1")) {
        resolved.newBuilder()
            .scheme(apiUrl.scheme)
            .host(apiUrl.host)
            .port(apiUrl.port)
            .build()
            .toString()
    } else resolved.toString()
}
