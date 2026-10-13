package com.oniolla.interviewer.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class TopicIconNetworkTest {
    private class Tokens : SessionTokenStorage {
        override var sessionVersion: Long = 0
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
    }

    @Test fun relativeIconPathsResolveAgainstTheApiAndKeepVersionQueries() {
        val base = "https://api.example.test/"
        assertEquals("https://api.example.test/icons/kotlin.svg?v=3",
            resolveTopicIconUrl("/icons/kotlin.svg?v=3", base))
        assertEquals("https://api.example.test/icons/flow.svg",
            resolveTopicIconUrl(" icons/flow.svg ", base))
        assertEquals("https://cdn.example.test/kotlin.png",
            resolveTopicIconUrl("https://cdn.example.test/kotlin.png", base))
    }

    @Test fun backendLoopbackUrlsUseTheReachableApiOriginAndPreserveTheAssetPath() {
        val base = "http://192.168.0.117:8080/"
        listOf("localhost", "127.0.0.1", "[::1]").forEach { host ->
            assertEquals("http://192.168.0.117:8080/assets/topics/android/kotlin-v1.svg?v=2",
                resolveTopicIconUrl("http://$host:8080/assets/topics/android/kotlin-v1.svg?v=2", base))
        }
        assertEquals("https://api.example.test:8443/assets/kotlin.svg",
            resolveTopicIconUrl("http://localhost:8080/assets/kotlin.svg", "https://api.example.test:8443/"))
    }

    @Test fun absoluteCdnUrlsKeepTheirOwnOriginAndPath() {
        val url = "https://cdn.example.test:8443/assets/topics/kotlin.svg?v=3"
        assertEquals(url, resolveTopicIconUrl(url, "http://192.168.0.117:8080/"))
    }

    @Test fun missingOrUnsupportedIconUrlsUseTheFallback() {
        val base = "https://api.example.test/"
        listOf(null, "", "  ", "ftp://example.test/icon.svg", "preview://figma/code").forEach {
            assertNull(resolveTopicIconUrl(it, base))
        }
    }

    @Test fun protectedIconsReceiveTheCurrentAccessTokenAfterRefresh() {
        val tokens = Tokens()
        val requests = mutableListOf<Request>()
        val client = client(tokens, requests)
        val url = resolveTopicIconUrl("/icons/kotlin.svg", "https://api.example.test/")!!
        client.newCall(Request.Builder().url(url).build()).execute().close()
        tokens.accessToken = "refreshed-access"
        client.newCall(Request.Builder().url(url).build()).execute().close()
        tokens.clear()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        assertEquals(listOf("Bearer access", "Bearer refreshed-access", null),
            requests.map { it.header("Authorization") })
        assertTrue(requests.all { it.method == "GET" && it.body == null })
    }

    @Test fun externalIconServersDoNotReceiveTheSessionToken() {
        val requests = mutableListOf<Request>()
        val client = client(Tokens(), requests)
        listOf("https://cdn.example.test/icon.svg", "http://api.example.test/icon.svg",
            "https://api.example.test:8443/icon.svg").forEach {
            client.newCall(Request.Builder().url(it).build()).execute().close()
        }
        assertTrue(requests.all { it.header("Authorization") == null })
    }

    private fun client(tokens: Tokens, requests: MutableList<Request>): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(SessionAuthorizationInterceptor(tokens, "https://api.example.test/"))
            .addInterceptor(Interceptor { chain ->
                requests.add(chain.request())
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("<svg/>".toResponseBody()).build()
            })
            .build()
}
