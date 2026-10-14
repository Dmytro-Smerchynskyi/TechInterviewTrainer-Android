package com.oniolla.interviewer.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.VerifyEmailPayload
import com.oniolla.interviewer.domain.*
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
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SessionProfileTest {
    private class Tokens : SessionTokenStorage {
        @get:Synchronized @set:Synchronized override var sessionVersion: Long = 0
        @get:Synchronized @set:Synchronized override var accessToken: String? = "access"
        @get:Synchronized @set:Synchronized override var refreshToken: String? = "refresh"
    }

    private class Fixture {
        val tokens = Tokens()
        val requests = CopyOnWriteArrayList<Request>()
        @Volatile var name = "Developer"
        @Volatile var specialization = "ANDROID"
        @Volatile var language = "en"
        @Volatile var meStatus = 200
        @Volatile var saveStatus = 200
        @Volatile var beforeMe: (() -> Unit)? = null
        val auth: AuthRepository
        val dashboard: GetDashboardUseCase
        val resolve: ResolveAuthStateUseCase
        val resend: ResendVerificationCodeUseCase

        init {
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, "https://api.example.test/"))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests.add(request)
                    val path = request.url.encodedPath
                    val status = when (path) {
                        "/me" -> meStatus
                        "/me/specialization", "/me/preparation-language" -> saveStatus
                        else -> 200
                    }
                    if (status == 200 && request.method == "PUT") {
                        val body = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
                        if (path == "/me/specialization") specialization = body["specialization"].asString
                        if (path == "/me/preparation-language") language = body["language"].asString
                    }
                    val body = if (status >= 400) """{"error":{"code":"FAILED","message":"Profile failed"}}"""
                    else when (path) {
                        "/me", "/me/specialization", "/me/preparation-language" -> """{
                            "id":"user","email":"developer@example.com","displayName":${Gson().toJson(name)},
                            "emailVerified":true,"specialization":"$specialization","createdAt":"","updatedAt":"",
                            "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"$language"}"""
                        "/dashboard" -> """{"readiness":{"score":0,"answered":0,"practicedTopics":0,"totalTopics":0,
                            "gradePerformance":[]},"dueCount":0,"todayReview":[],"weakTopics":[]}"""
                        "/auth/refresh", "/auth/login", "/auth/email/verify" ->
                            """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
                        else -> error("Unexpected endpoint $path")
                    }
                    if (path == "/me") beforeMe?.invoke()
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(status).message("Test response")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            val api = Retrofit.Builder().baseUrl("https://api.example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            val coordinator = SessionRefreshCoordinator(tokens, api)
            val usageRepository = SessionUsageRepository(api, coordinator, tokens)
            auth = AuthRepository(tokens, api, coordinator, usageRepository = usageRepository)
            dashboard = GetDashboardUseCase(HomeRepository(api, MemoryTopicCache(), coordinator, usageRepository = usageRepository), auth)
            resend = ResendVerificationCodeUseCase(auth)
            resolve = ResolveAuthStateUseCase(auth, resend)
        }

        fun paths() = requests.map { it.url.encodedPath }
        fun meCalls() = paths().count { it == "/me" }
        suspend fun homeProfile() = (dashboard(Unit) as ContentResult.Success).content.profile
    }

    private suspend fun failure(block: suspend () -> Unit): Exception = try {
        block()
        throw AssertionError("Expected failure")
    } catch (error: Exception) { error }

    @Test fun restoredSessionAndHomeUseOneMeRequest() = runTest {
        val fixture = Fixture()
        assertSame(AuthState.Authorized, RestoreSessionUseCase(fixture.auth, fixture.resolve)(Unit).toList().last())
        assertEquals("Developer", fixture.homeProfile().displayName)
        assertEquals(listOf("/auth/refresh", "/me", "/dashboard"), fixture.paths())
    }

    @Test fun loginAndHomeUseOneMeRequest() = runTest {
        val fixture = Fixture()
        assertSame(AuthState.Authorized, LoginUseCase(fixture.auth, fixture.resend, fixture.resolve)(
            LoginPayload("developer@example.com", "password")))
        fixture.homeProfile()
        assertEquals(listOf("/auth/login", "/me", "/dashboard"), fixture.paths())
    }

    @Test fun emailConfirmationAndHomeUseOneMeRequest() = runTest {
        val fixture = Fixture()
        assertSame(AuthState.Authorized, VerifyEmailUseCase(fixture.auth, fixture.resolve)(
            VerifyEmailPayload("developer@example.com", "123456")))
        fixture.homeProfile()
        assertEquals(listOf("/auth/email/verify", "/me", "/dashboard"), fixture.paths())
    }

    @Test fun missingProfileFallsBackToMeOnceAndRepeatedDashboardLoadsReuseIt() = runTest {
        val fixture = Fixture()
        val first = fixture.homeProfile()
        assertSame(first, fixture.homeProfile())
        assertEquals(1, fixture.meCalls())
        assertEquals(2, fixture.paths().count { it == "/dashboard" })
    }

    @Test fun onboardingResponsesUpdateTheProfileUsedByHomeWithoutAnotherMe() = runTest {
        val fixture = Fixture()
        fixture.auth.getMe()
        fixture.auth.selectSpecialization(Specialization.IOS)
        fixture.name = "Updated Developer"
        val saved = fixture.auth.selectPreparationLanguage("uk")
        val home = fixture.homeProfile()
        assertSame(saved, home)
        assertEquals("IOS", home.specialization)
        assertEquals("uk", home.preparationLanguage)
        assertEquals("Updated Developer", home.displayName)
        assertEquals(1, fixture.meCalls())
    }

    @Test fun logoutAndNewLoginCannotReuseThePreviousProfile() = runTest {
        val fixture = Fixture()
        fixture.auth.getMe()
        fixture.auth.clearSession()
        fixture.name = "Other User"
        fixture.auth.login(LoginPayload("other@example.com", "password"))
        assertEquals("Other User", fixture.homeProfile().displayName)
        assertEquals(2, fixture.meCalls())
    }

    @Test fun sessionChangesOutsideAuthRepositoryAlsoInvalidateTheProfile() = runTest {
        val fixture = Fixture()
        fixture.auth.getMe()
        fixture.tokens.clear()
        fixture.tokens.saveSession("other-access", "other-refresh")
        fixture.name = "Other User"
        assertEquals("Other User", fixture.auth.getSessionProfile().displayName)
        assertEquals(2, fixture.meCalls())
    }

    @Test fun tokenRotationKeepsTheProfileForTheSameSession() = runTest {
        val fixture = Fixture()
        val profile = fixture.auth.getMe()
        fixture.auth.refreshSession()
        assertSame(profile, fixture.auth.getSessionProfile())
        assertEquals("new-access", fixture.tokens.accessToken)
        assertEquals(1, fixture.meCalls())
    }

    @Test fun explicitProfileReloadStillFetchesMeAndUpdatesTheSessionCache() = runTest {
        val fixture = Fixture()
        fixture.auth.getMe()
        fixture.name = "Renamed Developer"
        val refreshed = fixture.auth.getMe()
        assertSame(refreshed, fixture.auth.getSessionProfile())
        assertEquals("Renamed Developer", fixture.homeProfile().displayName)
        assertEquals(2, fixture.meCalls())
    }

    @Test fun failedProfileLoadIsNotCachedAndRetryCanRecover() = runTest {
        val fixture = Fixture().apply { meStatus = 500 }
        assertTrue(failure { fixture.auth.getSessionProfile() } is ApiException.ServerError)
        fixture.meStatus = 200
        fixture.auth.getSessionProfile()
        fixture.auth.getSessionProfile()
        assertEquals(2, fixture.meCalls())
    }

    @Test fun concurrentCacheMissesShareOneProfileFetch() = runTest {
        val fixture = Fixture()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture.beforeMe = { started.complete(Unit); check(release.await(10, TimeUnit.SECONDS)) }
        val first = async { fixture.auth.getSessionProfile() }
        started.await()
        val second = async { fixture.auth.getSessionProfile() }
        try { runCurrent() } finally { release.countDown() }
        assertSame(first.await(), second.await())
        assertEquals(1, fixture.meCalls())
    }

    @Test fun anOldProfileResponseCannotPopulateTheCacheAfterNewLogin() = runTest {
        val fixture = Fixture()
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture.beforeMe = { started.complete(Unit); check(release.await(10, TimeUnit.SECONDS)) }
        val old = async { failure { fixture.auth.getMe() } }
        started.await()
        fixture.name = "Other User"
        fixture.auth.login(LoginPayload("other@example.com", "password"))
        fixture.beforeMe = null
        release.countDown()
        assertTrue(old.await() is CancellationException)
        assertEquals("Other User", fixture.auth.getSessionProfile().displayName)
        fixture.auth.getSessionProfile()
        assertEquals(2, fixture.meCalls())
    }

    @Test fun aFailedOnboardingUpdateKeepsTheLastSuccessfulProfile() = runTest {
        val fixture = Fixture()
        val profile = fixture.auth.getMe()
        fixture.saveStatus = 500
        assertTrue(failure { fixture.auth.selectPreparationLanguage("uk") } is ApiException.ServerError)
        assertSame(profile, fixture.auth.getSessionProfile())
        assertEquals(1, fixture.meCalls())
    }
}
