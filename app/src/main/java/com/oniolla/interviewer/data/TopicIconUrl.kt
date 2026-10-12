package com.oniolla.interviewer.data

import okhttp3.HttpUrl.Companion.toHttpUrl

internal const val API_BASE_URL = "http://192.168.0.117:8080/"

internal fun resolveTopicIconUrl(value: String?, baseUrl: String = API_BASE_URL): String? {
    val url = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val apiUrl = baseUrl.toHttpUrl()
    val resolved = apiUrl.resolve(url) ?: return null
    // Local backend exports localhost URLs; on Android that is the device itself.
    return if (resolved.host in setOf("localhost", "127.0.0.1", "::1")) {
        resolved.newBuilder()
            .scheme(apiUrl.scheme)
            .host(apiUrl.host)
            .port(apiUrl.port)
            .build()
            .toString()
    } else resolved.toString()
}
