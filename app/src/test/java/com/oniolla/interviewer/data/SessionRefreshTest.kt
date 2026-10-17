package com.oniolla.interviewer.data

import com.google.gson.JsonParser
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.SignupPayload
import com.oniolla.interviewer.data.payload.VerifyEmailPayload
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.ResendVerificationCodeUseCase
import com.oniolla.interviewer.domain.ResolveAuthStateUseCase
import com.oniolla.interviewer.domain.RestoreSessionUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRefreshTest {
    private class Tokens : SessionTokenStorage {
        @get:Synchronized @set:Synchronized
        override var sessionVersion: Long = 0
        @get:Synchronized @set:Synchronized
        override var accessToken: String? = "old-access"
        @get:Synchronized @set:Synchronized
        override var refreshToken: String? = "old-refresh"
        var afterClear: (() -> Unit)? = null
        override fun clear() {
            super<SessionTokenStorage>.clear()
            afterClear?.invoke()
        }
    }

    private class Fixture(scope: CoroutineScope) {
        val tokens = Tokens()
        val requests = CopyOnWriteArrayList<Request>()
        val refreshBodies = CopyOnWriteArrayList<String>()
        val refreshCalls = AtomicInteger()
        @Volatile var refreshStatus = 200
        @Volatile var refreshBody = """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
        @Volatile var refreshNetworkFailure = false
        @Volatile var protectedAlwaysReject = false
        @Volatile var initialErrorCode = "UNAUTHORIZED"
        @Volatile var publicStatus = 200
        @Volatile var beforeRequest: ((Request) -> Unit)? = null
        val api: Api
        val coordinator: SessionRefreshCoordinator
        val auth: AuthRepository
        val home: HomeRepository

        init {
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, "https://api.example.test/"))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests.add(request)
                    val path = request.url.encodedPath
                    if (path == "/auth/refresh") {
                        refreshCalls.incrementAndGet()
                        refreshBodies.add(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                    }
                    beforeRequest?.invoke(request)
                    val public = path.startsWith("/auth/")
                    val status = when {
                        path == "/auth/refresh" -> {
                            if (refreshNetworkFailure) throw IOException("Offline")
                            refreshStatus
                        }
                        public -> publicStatus
                        protectedAlwaysReject || request.header("Authorization") == "Bearer old-access" -> 401
                        else -> 200
                    }
                    val body = if (status >= 400) {
                        val code = if (public || request.header("Authorization") != "Bearer old-access") "UNAUTHORIZED" else initialErrorCode
                        """{"error":{"code":"$code","message":"Request rejected"}}"""
                    } else when (path) {
                        "/auth/refresh" -> refreshBody
                        "/auth/login", "/auth/email/verify" -> refreshBody
                        "/auth/register", "/auth/password/forgot", "/auth/email/resend-code" -> """{"message":"Sent"}"""
                        "/me", "/me/specialization", "/me/preparation-language" -> """{
                            "id":"user","email":"developer@example.com","displayName":"Developer",
                            "emailVerified":true,"specialization":"ANDROID","createdAt":"","updatedAt":"",
                            "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"en"}"""
                        "/dashboard" -> """{"readiness":{"score":10,"answered":0,"practicedTopics":0,"totalTopics":0,
                            "gradePerformance":[]},"dueCount":0,"todayReview":[],"weakTopics":[]}"""
                        "/topics" -> "[]"
                        "/questions" -> """{"items":[],"nextCursor":null,"topics":[],"access":null}"""
                        "/preparation-languages" -> """{"items":[]}"""
                        else -> error("Unexpected endpoint $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(status).message("Test response")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            api = Retrofit.Builder().baseUrl("https://api.example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build().create(Api::class.java)
            coordinator = SessionRefreshCoordinator(tokens, api,
                CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job])))
            val usageRepository = SessionUsageRepository(api, coordinator, tokens)
            auth = AuthRepository(tokens, api, coordinator, usageRepository = usageRepository)
            home = HomeRepository(api, MemoryTopicCache(), coordinator, usageRepository = usageRepository)
        }

        fun protectedRequests() = requests.filterNot { it.url.encodedPath.startsWith("/auth/") }
    }

    private suspend fun failure(block: suspend () -> Unit): Throwable = try {
        block()
        throw AssertionError("Expected failure")
    } catch (error: Exception) { error }

    private fun block(latch: CountDownLatch) {
        check(latch.await(10, TimeUnit.SECONDS)) { "Test gate timed out" }
    }

    @Test fun unauthorizedRefreshUsesOneRawPostRotatesBothTokensAndRetriesWithNewBearer() = runTest {
        val fixture = Fixture(backgroundScope)
        val originalVersion = fixture.tokens.sessionVersion
        assertEquals("Developer", fixture.auth.getMe().displayName)
        assertEquals(1, fixture.refreshCalls.get())
        assertEquals(listOf("Bearer old-access", "Bearer new-access"),
            fixture.protectedRequests().map { it.header("Authorization") })
        val refresh = fixture.requests.single { it.url.encodedPath == "/auth/refresh" }
        assertEquals("POST", refresh.method)
        assertNull(refresh.header("Authorization"))
        assertNull(refresh.url.query)
        val payload = JsonParser.parseString(fixture.refreshBodies.single()).asJsonObject
        assertEquals(setOf("refreshToken"), payload.keySet())
        assertEquals("old-refresh", payload["refreshToken"].asString)
        assertEquals(SessionSnapshot("new-access", "new-refresh", originalVersion), fixture.tokens.snapshot())
    }

    @Test fun refreshReturningTheSameUnauthorizedDoesNotLoopAndExpiresTheSession() = runTest {
        val fixture = Fixture(backgroundScope).apply { refreshStatus = 401 }
        val result = GetDashboardUseCase(fixture.home, fixture.auth)(Unit)
        assertSame(ContentResult.SessionExpired, result)
        assertEquals(1, fixture.refreshCalls.get())
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
        assertTrue(fixture.protectedRequests().size <= 2)
    }

    @Test fun retriedProtectedRequestCanFailOnlyOnceWithoutASecondRefresh() = runTest {
        val fixture = Fixture(backgroundScope).apply { protectedAlwaysReject = true }
        assertTrue(failure { fixture.auth.getMe() } is ApiException.Unauthorized)
        assertEquals(1, fixture.refreshCalls.get())
        assertEquals(2, fixture.protectedRequests().size)
        assertNull(fixture.tokens.refreshToken)
    }

    @Test fun parallelRequestsShareOneRefreshAndThenRetryTheirOwnRequests() = runTest {
        val fixture = Fixture(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture.beforeRequest = { request ->
            if (request.url.encodedPath == "/auth/refresh") {
                started.complete(Unit)
                block(release)
            }
        }
        val me = async { fixture.auth.getMe() }
        started.await()
        val dashboard = async { fixture.home.getDashboard() }
        val topics = async { fixture.home.getTopics() }
        val questions = async { fixture.home.getQuestions(7, "flow", "opaque+/=") }
        try {
            runCurrent()
        } finally { release.countDown() }
        me.await(); dashboard.await(); topics.await(); questions.await()
        assertEquals(1, fixture.refreshCalls.get())
        assertEquals("new-refresh", fixture.tokens.refreshToken)
        val pages = fixture.requests.filter { it.url.encodedPath == "/questions" }
        pages.forEach {
            assertEquals("7", it.url.queryParameter("topicId"))
            assertEquals("flow", it.url.queryParameter("search"))
            assertEquals("opaque+/=", it.url.queryParameter("cursor"))
            assertEquals("20", it.url.queryParameter("limit"))
        }
    }

    @Test fun aLateOldUnauthorizedUsesTheAlreadyRotatedTokensWithoutAnotherRefresh() = runTest {
        val fixture = Fixture(backgroundScope)
        val oldStarted = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture.beforeRequest = { request ->
            if (request.url.encodedPath == "/topics" && request.header("Authorization") == "Bearer old-access") {
                oldStarted.complete(Unit)
                block(release)
            }
        }
        val topics = async { fixture.home.getTopics() }
        oldStarted.await()
        try {
            fixture.auth.getMe()
            assertEquals("new-access", fixture.tokens.accessToken)
        } finally { release.countDown() }
        topics.await()
        assertEquals(1, fixture.refreshCalls.get())
        assertEquals(listOf("Bearer old-access", "Bearer new-access"),
            fixture.requests.filter { it.url.encodedPath == "/topics" }.map { it.header("Authorization") })
    }

    @Test fun aRequestStartedDuringRefreshSharesItsFailureEvenWhenIts401ArrivesLater() = runTest {
        val fixture = Fixture(backgroundScope).apply { refreshStatus = 500 }
        val refreshStarted = CompletableDeferred<Unit>()
        val topicStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CountDownLatch(1)
        val releaseTopic = CountDownLatch(1)
        fixture.beforeRequest = { request ->
            when (request.url.encodedPath) {
                "/auth/refresh" -> { refreshStarted.complete(Unit); block(releaseRefresh) }
                "/topics" -> { topicStarted.complete(Unit); block(releaseTopic) }
            }
        }
        val me = async { failure { fixture.auth.getMe() } }
        refreshStarted.await()
        val topics = async { failure { fixture.home.getTopics() } }
        topicStarted.await()
        try {
            releaseRefresh.countDown()
            assertTrue(me.await() is ApiException.ServerError)
            releaseTopic.countDown()
            assertTrue(topics.await() is ApiException.ServerError)
        } finally { releaseRefresh.countDown(); releaseTopic.countDown() }
        assertEquals(1, fixture.refreshCalls.get())
        assertEquals("old-refresh", fixture.tokens.refreshToken)
        fixture.beforeRequest = null
        fixture.refreshStatus = 200
        fixture.auth.getMe()
        assertEquals(2, fixture.refreshCalls.get())
    }

    @Test fun refreshNetworkFailurePreservesSessionAndANewUserRetryCanRecover() = runTest {
        val fixture = Fixture(backgroundScope).apply { refreshNetworkFailure = true }
        val snapshot = fixture.tokens.snapshot()
        assertTrue(failure { fixture.auth.getMe() } is ApiException.NetworkError)
        assertEquals(snapshot, fixture.tokens.snapshot())
        assertEquals(1, fixture.protectedRequests().size)
        fixture.refreshNetworkFailure = false
        fixture.auth.getMe()
        assertEquals(2, fixture.refreshCalls.get())
        assertEquals("new-refresh", fixture.tokens.refreshToken)
    }

    @Test fun serverFailureDuringRefreshIsAContentErrorRatherThanSessionExpiry() = runTest {
        val fixture = Fixture(backgroundScope).apply { refreshStatus = 503 }
        val snapshot = fixture.tokens.snapshot()
        assertEquals(ContentResult.Error("Request rejected"), GetDashboardUseCase(fixture.home, fixture.auth)(Unit))
        assertEquals(snapshot, fixture.tokens.snapshot())
        assertEquals(1, fixture.refreshCalls.get())
    }

    @Test fun malformedOrBlankSuccessResponsesCannotReplaceTheStoredTokens() = runTest {
        for (body in listOf("invalid JSON", """{"accessToken":"","refreshToken":"new-refresh"}""",
            """{"accessToken":"new-access","refreshToken":""}""")) {
            val fixture = Fixture(backgroundScope).apply { refreshBody = body }
            val snapshot = fixture.tokens.snapshot()
            assertFalse(failure { fixture.auth.getMe() } is ApiException.Unauthorized)
            assertEquals(snapshot, fixture.tokens.snapshot())
            assertEquals(1, fixture.refreshCalls.get())
        }
    }

    @Test fun unauthorizedWithoutTheRefreshCodeDoesNotRefresh() = runTest {
        val fixture = Fixture(backgroundScope).apply { initialErrorCode = "ACCOUNT_DISABLED" }
        assertTrue(failure { fixture.auth.getMe() } is ApiException.Unauthorized)
        assertEquals(0, fixture.refreshCalls.get())
        assertEquals(1, fixture.protectedRequests().size)
    }

    @Test fun missingRefreshTokenExpiresWithoutCallingRefresh() = runTest {
        val fixture = Fixture(backgroundScope).apply { tokens.refreshToken = null }
        assertTrue(failure { fixture.auth.getMe() } is ApiException.Unauthorized)
        assertEquals(0, fixture.refreshCalls.get())
        assertNull(fixture.tokens.accessToken)
    }

    @Test fun publicUnauthorizedEndpointsNeverTriggerAutomaticRefreshOrSendBearer() = runTest {
        val fixture = Fixture(backgroundScope).apply { publicStatus = 401 }
        val snapshot = fixture.tokens.snapshot()
        assertTrue(failure { fixture.auth.login(LoginPayload("developer@example.com", "password")) } is ApiException.Unauthorized)
        assertTrue(failure { fixture.auth.verifyEmail(VerifyEmailPayload("developer@example.com", "123456", "password123")) } is ApiException.Unauthorized)
        assertTrue(failure { fixture.auth.forgotPassword("developer@example.com") } is ApiException.Unauthorized)
        assertTrue(failure { fixture.auth.sendVerificationEmail("developer@example.com") } is ApiException.Unauthorized)
        assertEquals(snapshot, fixture.tokens.snapshot())
        failure { fixture.auth.signup(SignupPayload("developer@example.com", "password", "Developer")).blockingGet() }
        assertEquals(0, fixture.refreshCalls.get())
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
        assertEquals(snapshot.version + 1, fixture.tokens.sessionVersion)
        assertTrue(fixture.requests.all { it.header("Authorization") == null })
    }

    @Test fun bootstrapRefreshUsesTheSameCoordinatorAndSkipsMeOnRejection() = runTest {
        val fixture = Fixture(backgroundScope).apply { refreshStatus = 401 }
        val resolve = ResolveAuthStateUseCase(fixture.auth, ResendVerificationCodeUseCase(fixture.auth))
        assertSame(AuthState.Unauthorized, RestoreSessionUseCase(fixture.auth, resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh"), fixture.requests.map { it.url.encodedPath })
        assertNull(fixture.tokens.refreshToken)
    }

    @Test fun onboardingProtectedCallsAlsoRefreshAndKeepTheirPayloads() = runTest {
        for (operation in 0..2) {
            val fixture = Fixture(backgroundScope)
            when (operation) {
                0 -> fixture.auth.getPreparationLanguages()
                1 -> fixture.auth.selectSpecialization(Specialization.ANDROID)
                2 -> fixture.auth.selectPreparationLanguage("en")
            }
            assertEquals(1, fixture.refreshCalls.get())
            val calls = fixture.protectedRequests()
            assertEquals(2, calls.size)
            assertEquals(calls.first().method, calls.last().method)
            assertEquals(calls.first().url, calls.last().url)
            fun body(request: Request) = request.body?.let { Buffer().also(it::writeTo).readUtf8() }
            assertEquals(body(calls.first()), body(calls.last()))
        }
    }

    @Test fun loggingOutDuringRefreshCannotRestoreTheOldSession() = runTest {
        val fixture = Fixture(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture.beforeRequest = { request ->
            if (request.url.encodedPath == "/auth/refresh") { started.complete(Unit); block(release) }
        }
        val call = async { failure { fixture.auth.getMe() } }
        started.await()
        fixture.auth.clearSession()
        release.countDown()
        assertTrue(call.await() is CancellationException)
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
    }

    @Test fun aLateRefreshSuccessOrUnauthorizedCannotOverwriteOrClearANewLogin() = runTest {
        for (status in listOf(200, 401)) {
            val fixture = Fixture(backgroundScope).apply { refreshStatus = status }
            val started = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            fixture.beforeRequest = { request ->
                if (request.url.encodedPath == "/auth/refresh") { started.complete(Unit); block(release) }
            }
            val call = async { failure { fixture.auth.getMe() } }
            started.await()
            val newSession = fixture.tokens.saveSession("other-user-access", "other-user-refresh")
            release.countDown()
            assertTrue(call.await() is CancellationException)
            assertEquals(newSession, fixture.tokens.snapshot())
        }
    }

    @Test fun evenReusingTheSameTokenStringsAfterLogoutCountsAsANewSession() = runTest {
        val fixture = Fixture(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture.beforeRequest = { request ->
            if (request.url.encodedPath == "/auth/refresh") { started.complete(Unit); block(release) }
        }
        val call = async { failure { fixture.auth.getMe() } }
        started.await()
        fixture.tokens.clear()
        val newSession = fixture.tokens.saveSession("old-access", "old-refresh")
        release.countDown()
        assertTrue(call.await() is CancellationException)
        assertEquals(newSession, fixture.tokens.snapshot())
    }

    @Test fun cancellingOneWaiterDoesNotCancelTheSharedRefreshForAnotherRequest() = runTest {
        val fixture = Fixture(backgroundScope)
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture.beforeRequest = { request ->
            if (request.url.encodedPath == "/auth/refresh") { started.complete(Unit); block(release) }
        }
        val cancelled = async { fixture.auth.getMe() }
        started.await()
        val remaining = async { fixture.home.getTopics() }
        runCurrent()
        cancelled.cancelAndJoin()
        release.countDown()
        remaining.await()
        assertEquals(1, fixture.refreshCalls.get())
        assertEquals("new-refresh", fixture.tokens.refreshToken)
    }

    @Test fun aRejectedRefreshCannotReportSessionExpiryAfterANewLoginHasAlreadyWon() = runTest {
        val fixture = Fixture(backgroundScope).apply { refreshStatus = 401 }
        fixture.tokens.afterClear = {
            fixture.tokens.saveSession("other-user-access", "other-user-refresh")
        }
        assertTrue(failure { fixture.auth.getMe() } is CancellationException)
        assertEquals("other-user-access", fixture.tokens.accessToken)
        assertEquals("other-user-refresh", fixture.tokens.refreshToken)
    }

    @Test fun unauthorizedCodeWithoutAMessageStillTriggersRefreshAndMalformedErrorsKeepTheirHttpStatus() = runTest {
        val body = """{"error":{"code":"UNAUTHORIZED"}}"""
        val response = retrofit2.Response.error<Any>(401, body.toResponseBody("application/json".toMediaType()))
        val error = failure { apiCall<Unit> { throw retrofit2.HttpException(response) } } as ApiException.Unauthorized
        assertEquals("UNAUTHORIZED", error.customError.code)
        assertNull(error.customError.message)
        for (invalid in listOf("<html>Unauthorized</html>", """{"error":null}""", "[]")) {
            val invalidResponse = retrofit2.Response.error<Any>(401, invalid.toResponseBody("application/json".toMediaType()))
            val invalidError = failure { apiCall<Unit> { throw retrofit2.HttpException(invalidResponse) } }
            assertTrue(invalidError is ApiException.Unauthorized)
        }
    }

    @Test fun failedRequestUsesItsCapturedBearerEvenIfStorageChangesBeforeDispatch() = runTest {
        val fixture = Fixture(backgroundScope)
        val old = fixture.tokens.snapshot()
        fixture.tokens.updateIfCurrent(old, "new-access", "new-refresh")
        assertTrue(failure { apiCall { fixture.api.me(old) } } is ApiException.Unauthorized)
        assertEquals("Bearer old-access", fixture.requests.single().header("Authorization"))
        assertEquals("new-access", fixture.tokens.accessToken)
    }
}
