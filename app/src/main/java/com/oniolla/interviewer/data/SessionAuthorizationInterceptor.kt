package com.oniolla.interviewer.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

internal class SessionAuthorizationInterceptor(
    private val tokenStorage: SessionTokenStorage,
    baseUrl: String = API_BASE_URL
) : Interceptor {
    private val apiUrl = baseUrl.toHttpUrl()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        val request = original.newBuilder()
        if (url.scheme == apiUrl.scheme && url.host == apiUrl.host && url.port == apiUrl.port) {
            tokenStorage.accessToken?.takeIf { it.isNotBlank() }?.let {
                request.header("Authorization", "Bearer $it")
            }
        }
        return chain.proceed(request.build())
    }
}
