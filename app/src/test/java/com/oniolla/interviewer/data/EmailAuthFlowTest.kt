package com.oniolla.interviewer.data

import com.google.gson.JsonParser
import com.google.gson.Gson
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.domain.SelectSpecializationUseCase
import com.oniolla.interviewer.domain.SelectPreparationLanguageUseCase
import com.oniolla.interviewer.domain.toAuthState
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.SignupPayload
import com.oniolla.interviewer.data.payload.VerifyEmailPayload
import com.oniolla.interviewer.domain.LoginUseCase
import com.oniolla.interviewer.domain.ResendVerificationCodeUseCase
import com.oniolla.interviewer.domain.ResolveAuthStateUseCase
import com.oniolla.interviewer.domain.RestoreSessionUseCase
import com.oniolla.interviewer.domain.VerifyEmailUseCase
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import com.oniolla.interviewer.ui.isEmailCodeValid
import com.oniolla.interviewer.ui.normalizeEmailCode
import com.oniolla.interviewer.ui.toUi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class EmailAuthFlowTest {
    private class MemoryTokens : SessionTokenStorage {
        override var sessionVersion: Long = 0
        override var accessToken: String? = null
        var refreshReadFailure: Exception? = null
        override var refreshToken: String? = null
            get() {
                refreshReadFailure?.let { throw it }
                return field
            }
    }

    private class Fixture {
        val tokens = MemoryTokens()
        val requests = mutableListOf<Pair<String, String>>()
        var emailVerified = true
        var onboardingCompleted = true
        var specialization: String? = "ANDROID"
        var preparationLanguage: String? = "en"
        var specializationStatus = 200
        var completeOnSpecializationSave = true
        var preparationLanguageStatus = 200
        var completeOnLanguageSave = true
        var ignoreLanguageSave = false
        var refreshStatus = 200
        var refreshErrorCode = "INVALID_REFRESH_TOKEN"
        var loginStatus = 200
        var meStatus = 200
        var resendStatus = 200
        var verifyStatus = 200
        var networkFailurePath: String? = null
        var badRefreshBody = false
        val tokensUsedForMe = mutableListOf<String?>()
        val repository: AuthRepository
        val resend: ResendVerificationCodeUseCase
        val resolve: ResolveAuthStateUseCase

        init {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val requestBody = buffer.readUtf8()
                requests.add(path to requestBody)
                if (path == networkFailurePath) throw IOException("Offline")
                if (path == "/me") tokensUsedForMe.add(tokens.accessToken)
                if (path == "/auth/refresh") assertEquals("POST", request.method)
                val status = when (path) {
                    "/auth/refresh" -> refreshStatus
                    "/auth/login" -> loginStatus
                    "/me" -> meStatus
                    "/me/specialization" -> specializationStatus
                    "/me/preparation-language" -> preparationLanguageStatus
                    "/auth/email/resend-code" -> resendStatus
                    "/auth/email/verify" -> verifyStatus
                    else -> 200
                }
                if (path == "/me/specialization" && status < 400) {
                    assertEquals("PUT", request.method)
                    specialization = JsonParser.parseString(requestBody).asJsonObject["specialization"].asString
                    onboardingCompleted = completeOnSpecializationSave
                }
                if (path == "/me/preparation-language" && status < 400) {
                    assertEquals("PUT", request.method)
                    val payload = JsonParser.parseString(requestBody).asJsonObject
                    assertEquals(setOf("language"), payload.keySet())
                    assertEquals("en", payload["language"].asString)
                    if (!ignoreLanguageSave) preparationLanguage = payload["language"].asString
                    onboardingCompleted = completeOnLanguageSave
                }
                val body = if (status >= 400) {
                    val code = when (path) {
                        "/auth/login", "/me" -> "EMAIL_NOT_VERIFIED"
                        "/auth/refresh" -> refreshErrorCode
                        else -> "INVALID_CODE"
                    }
                    """{"error":{"code":"$code","message":"Request failed"}}"""
                } else when (path) {
                    "/me", "/me/specialization", "/me/preparation-language" -> profileResponseBody()
                    "/auth/refresh" -> if (badRefreshBody) "invalid JSON" else """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
                    "/auth/login", "/auth/email/verify" -> """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
                    else -> """{"message":"Email sent"}"""
                }
                Response.Builder()
                    .request(request).protocol(Protocol.HTTP_1_1)
                    .code(status).message("Test response")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
            val api = Retrofit.Builder().baseUrl("https://example.test/")
                .client(client).addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build().create(Api::class.java)
            repository = AuthRepository(tokens, api, SessionRefreshCoordinator(tokens, api))
            resend = ResendVerificationCodeUseCase(repository)
            resolve = ResolveAuthStateUseCase(repository, resend)
        }

        private fun profileResponseBody(): String {
            val specializationJson = Gson().toJson(specialization)
            val languageJson = Gson().toJson(preparationLanguage)
            return """{"id":"user-id","email":"developer@example.com","displayName":"Developer","emailVerified":$emailVerified,"specialization":$specializationJson,"createdAt":"2026-10-12T11:20:59.310Z","updatedAt":"2026-10-12T11:20:59.310Z","onboardingCompleted":$onboardingCompleted,"linkedProviders":["PASSWORD"],"preparationLanguage":$languageJson}"""
        }

        fun paths() = requests.map { it.first }
    }

    @Test
    fun verifiedUserWithCompletedOnboardingIsAuthorized() = runBlocking {
        val fixture = Fixture()
        assertSame(AuthState.Authorized, fixture.resolve(Unit))
        assertEquals(listOf("/me"), fixture.paths())
        val request = fixture.requests.single()
        assertEquals("", request.second)
    }

    @Test
    fun verifiedUserWithIncompleteOnboardingDoesNotAuthorize() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false }
        assertSame(AuthState.OnboardingRequired, fixture.resolve(Unit))
        assertEquals(listOf("/me"), fixture.paths())
        assertSame(AuthViewState.OnboardingRequired, AuthState.OnboardingRequired.toUi())
    }

    @Test
    fun unverifiedUserGetsCodeBeforeConfirmationEvenIfOnboardingIsComplete() = runBlocking {
        val fixture = Fixture().apply { emailVerified = false }
        val state = fixture.resolve(Unit) as AuthState.VerificationEmailSent
        assertEquals(listOf("/me", "/auth/email/resend-code"), fixture.paths())
        assertEquals("developer@example.com", state.email)
        assertEquals("Email sent", state.message)
        val viewState = state.toUi() as AuthViewState.Unauthorized.EmailConfirmation
        assertEquals(state.email, viewState.email)
        assertFalse(viewState.isLoading)
        assertEquals("", viewState.code)
    }

    @Test
    fun unverifiedUserWithIncompleteOnboardingAlsoGetsConfirmation() = runBlocking {
        val fixture = Fixture().apply { emailVerified = false; onboardingCompleted = false }
        assertTrue(fixture.resolve(Unit) is AuthState.VerificationEmailSent)
    }

    @Test
    fun failedInitialCodeDeliveryKeepsConfirmationAvailableForResend() = runBlocking {
        val fixture = Fixture().apply { emailVerified = false; resendStatus = 500 }
        val state = fixture.resolve(Unit) as AuthState.EmailVerificationPending
        assertEquals("developer@example.com", state.email)
        assertEquals("Request failed", state.message)
        val viewState = state.toUi() as AuthViewState.Unauthorized.EmailConfirmation
        assertEquals("Request failed", viewState.codeError)
        assertFalse(viewState.isLoading)
    }

    @Test
    fun forbiddenLoginResendsCodeAndOpensConfirmation() = runBlocking {
        val fixture = Fixture().apply { loginStatus = 403 }
        val useCase = LoginUseCase(fixture.repository, fixture.resend, fixture.resolve)
        val state = useCase(LoginPayload("developer@example.com", "password")) as AuthState.VerificationEmailSent
        assertEquals("developer@example.com", state.email)
        assertEquals(listOf("/auth/login", "/auth/email/resend-code"), fixture.paths())
        assertNull(fixture.tokens.accessToken)
    }

    @Test
    fun successfulLoginChecksProfileAndSavesRotatedTokens() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false }
        val useCase = LoginUseCase(fixture.repository, fixture.resend, fixture.resolve)
        assertSame(AuthState.OnboardingRequired, useCase(LoginPayload("developer@example.com", "password")))
        assertEquals("new-access", fixture.tokens.accessToken)
        assertEquals("new-refresh", fixture.tokens.refreshToken)
        assertEquals(listOf("/auth/login", "/me"), fixture.paths())
    }

    @Test
    fun verifyPostsEmailAndCodeSavesTokensAndChecksProfile() = runBlocking {
        val fixture = Fixture()
        val useCase = VerifyEmailUseCase(fixture.repository, fixture.resolve)
        assertSame(AuthState.Authorized, useCase(VerifyEmailPayload("developer@example.com", "012345")))
        assertEquals(listOf("/auth/email/verify", "/me"), fixture.paths())
        val payload = JsonParser.parseString(fixture.requests.first().second).asJsonObject
        assertEquals(setOf("email", "code"), payload.keySet())
        assertEquals("developer@example.com", payload["email"].asString)
        assertEquals("012345", payload["code"].asString)
        assertEquals("new-access", fixture.tokens.accessToken)
        assertEquals("new-refresh", fixture.tokens.refreshToken)
    }

    @Test
    fun verificationRoutesToOnboardingWhenNeeded() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false }
        assertSame(AuthState.OnboardingRequired,
            VerifyEmailUseCase(fixture.repository, fixture.resolve)(VerifyEmailPayload("developer@example.com", "123456")))
    }

    @Test
    fun invalidCodeDoesNotSaveTokensOrFetchProfile() = runBlocking {
        val fixture = Fixture().apply { verifyStatus = 400 }
        val state = VerifyEmailUseCase(fixture.repository, fixture.resolve)(
            VerifyEmailPayload("developer@example.com", "123456")
        ) as AuthState.EmailVerificationFailed
        assertEquals("Request failed", state.message)
        assertEquals(listOf("/auth/email/verify"), fixture.paths())
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
    }

    @Test
    fun networkFailureIsHandledWithoutLeavingLoadingState() = runBlocking {
        val fixture = Fixture().apply { networkFailurePath = "/auth/email/verify" }
        val state = VerifyEmailUseCase(fixture.repository, fixture.resolve)(
            VerifyEmailPayload("developer@example.com", "123456")
        ) as AuthState.EmailVerificationFailed
        assertEquals("Something unexpected happened", state.message)
    }

    @Test
    fun forbiddenMeWithoutEmailReturnsToLogin() = runBlocking {
        val fixture = Fixture().apply {
            meStatus = 403
            tokens.accessToken = "access"
            tokens.refreshToken = "refresh"
        }
        assertSame(AuthState.Unauthorized, fixture.resolve(Unit))
        assertEquals(listOf("/me"), fixture.paths())
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
    }

    @Test
    fun successfulSignupReturnsResponseWithoutCreatingSession() = runBlocking {
        val fixture = Fixture()
        val response = fixture.repository.signup(
            SignupPayload("developer@example.com", "password", "Developer")
        ).blockingGet()
        assertEquals("Email sent", response.message)
        assertEquals(listOf("/auth/register"), fixture.paths())
        val payload = JsonParser.parseString(fixture.requests.single().second).asJsonObject
        assertEquals("developer@example.com", payload["email"].asString)
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
    }

    @Test
    fun changingEmailCanClearTheWholeSession() {
        val fixture = Fixture()
        fixture.tokens.accessToken = "access"
        fixture.tokens.refreshToken = "refresh"
        fixture.repository.clearSession()
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
    }

    @Test
    fun startupWithoutRefreshTokenGoesToLoginWithoutNetworkCalls() = runBlocking {
        val fixture = Fixture().apply { tokens.accessToken = "stale-access" }
        val states = RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList()
        assertEquals(listOf(AuthState.Loading(0f), AuthState.Unauthorized), states)
        assertTrue(fixture.requests.isEmpty())
        assertNull(fixture.tokens.accessToken)
    }

    @Test
    fun startupWithBlankRefreshTokenAlsoSkipsNetworkCalls() = runBlocking {
        val fixture = Fixture().apply { tokens.refreshToken = "   " }
        assertSame(AuthState.Unauthorized,
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertTrue(fixture.requests.isEmpty())
        assertNull(fixture.tokens.refreshToken)
    }

    @Test
    fun startupRefreshesBeforeMeAndStoresTheRotatedTokens() = runBlocking {
        val fixture = Fixture().apply { tokens.refreshToken = "stored-refresh" }
        val states = RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList()
        assertEquals(listOf(
            AuthState.Loading(0f), AuthState.Loading(0.5f), AuthState.Loading(1f), AuthState.Authorized
        ), states)
        assertEquals(listOf("/auth/refresh", "/me"), fixture.paths())
        val payload = JsonParser.parseString(fixture.requests.first().second).asJsonObject
        assertEquals(setOf("refreshToken"), payload.keySet())
        assertEquals("stored-refresh", payload["refreshToken"].asString)
        assertEquals(listOf("new-access"), fixture.tokensUsedForMe)
        assertEquals("new-access", fixture.tokens.accessToken)
        assertEquals("new-refresh", fixture.tokens.refreshToken)
    }

    @Test
    fun startupWithUnverifiedEmailResendsCodeAndOpensConfirmation() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            emailVerified = false
            onboardingCompleted = false
        }
        val result = RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last()
            as AuthState.VerificationEmailSent
        assertEquals("developer@example.com", result.email)
        assertEquals(listOf("/auth/refresh", "/me", "/auth/email/resend-code"), fixture.paths())
        assertTrue(result.toUi() is AuthViewState.Unauthorized.EmailConfirmation)
    }

    @Test
    fun startupWithVerifiedEmailButIncompleteOnboardingDoesNotAuthorize() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            onboardingCompleted = false
        }
        assertSame(AuthState.OnboardingRequired,
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh", "/me"), fixture.paths())
    }

    @Test
    fun rejectedRefreshTokenIsClearedAndUserReturnsToLogin() = runBlocking {
        val fixture = Fixture().apply {
            tokens.accessToken = "old-access"
            tokens.refreshToken = "revoked-refresh"
            refreshStatus = 401
        }
        assertSame(AuthState.Unauthorized,
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh"), fixture.paths())
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
    }

    @Test
    fun unauthorizedMeAfterRefreshClearsSessionAndReturnsToLogin() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            meStatus = 401
        }
        assertSame(AuthState.Unauthorized,
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh", "/me"), fixture.paths())
        assertNull(fixture.tokens.refreshToken)
    }

    @Test
    fun refreshNetworkErrorKeepsRefreshTokenForRetry() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            networkFailurePath = "/auth/refresh"
        }
        assertEquals(AuthState.Error("Something unexpected happened"),
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh"), fixture.paths())
        assertEquals("stored-refresh", fixture.tokens.refreshToken)
    }

    @Test
    fun refreshServerErrorDoesNotCallMeOrClearRefreshToken() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            refreshStatus = 500
        }
        assertEquals(AuthState.Error("Request failed"),
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh"), fixture.paths())
        assertEquals("stored-refresh", fixture.tokens.refreshToken)
    }

    @Test
    fun profileNetworkErrorAfterRefreshKeepsRotatedTokenForRetry() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            networkFailurePath = "/me"
        }
        assertEquals(AuthState.Error("Something unexpected happened"),
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh", "/me"), fixture.paths())
        assertEquals("new-refresh", fixture.tokens.refreshToken)
    }

    @Test
    fun startupCodeDeliveryFailureKeepsResendAvailable() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            emailVerified = false
            resendStatus = 500
        }
        val result = RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last()
            as AuthState.EmailVerificationPending
        assertEquals("developer@example.com", result.email)
        assertFalse((result.toUi() as AuthViewState.Unauthorized.EmailConfirmation).isLoading)
        assertEquals("Request failed", result.message)
    }

    @Test
    fun malformedRefreshResponseDoesNotLeaveAppLoading() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            badRefreshBody = true
        }
        assertTrue(RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last() is AuthState.Error)
        assertEquals(listOf("/auth/refresh"), fixture.paths())
        assertEquals("stored-refresh", fixture.tokens.refreshToken)
    }

    @Test
    fun refreshForbiddenErrorWithoutEmailDoesNotSendCode() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            refreshStatus = 403
        }
        assertEquals(AuthState.Error("Request failed"),
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh"), fixture.paths())
        assertEquals("stored-refresh", fixture.tokens.refreshToken)
    }

    @Test
    fun refreshEmailNotVerifiedWithoutKnownEmailReturnsToLogin() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            refreshStatus = 403
            refreshErrorCode = "EMAIL_NOT_VERIFIED"
        }
        assertSame(AuthState.Unauthorized,
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh"), fixture.paths())
        assertNull(fixture.tokens.refreshToken)
    }

    @Test
    fun cancelledStartupDoesNotBecomeAnErrorOrClearSession() = runBlocking {
        val cancellation = CancellationException("Cancelled")
        val fixture = Fixture().apply {
            tokens.refreshToken = "stored-refresh"
            tokens.refreshReadFailure = cancellation
        }
        val states = mutableListOf<AuthState>()
        try {
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList(states)
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
        assertEquals(listOf(AuthState.Loading(0f)), states)
        assertTrue(fixture.requests.isEmpty())
        fixture.tokens.refreshReadFailure = null
        assertEquals("stored-refresh", fixture.tokens.refreshToken)
    }

    @Test
    fun incompleteOnboardingWithoutSpecializationOpensSelectionWithAndroidDefault() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false; specialization = null }
        val result = fixture.resolve(Unit)
        assertSame(AuthState.SpecializationSelectionRequired, result)
        assertEquals(Specialization.ANDROID,
            (result.toUi() as AuthViewState.SpecializationSelection).selectedSpecialization)
        assertEquals(listOf("/me"), fixture.paths())
    }

    @Test
    fun missingSpecializationTakesPriorityOverMissingLanguage() = runBlocking {
        val fixture = Fixture().apply {
            onboardingCompleted = false
            specialization = null
            preparationLanguage = null
        }
        assertSame(AuthState.SpecializationSelectionRequired, fixture.resolve(Unit))
        assertEquals(listOf("/me"), fixture.paths())
    }

    @Test
    fun missingLanguageIsDetectedWhenSpecializationAlreadyExists() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false; preparationLanguage = null }
        assertEquals(AuthState.PreparationLanguageSelectionRequired(Specialization.ANDROID), fixture.repository.getMe().toAuthState())
    }

    @Test
    fun completedOnboardingAuthorizesWithoutPromptingForMissingFields() = runBlocking {
        val fixture = Fixture().apply { specialization = null; preparationLanguage = null }
        assertSame(AuthState.Authorized, fixture.resolve(Unit))
        assertEquals(listOf("/me"), fixture.paths())
    }

    @Test
    fun unverifiedEmailIsHandledBeforeSpecializationSelection() = runBlocking {
        val fixture = Fixture().apply {
            emailVerified = false
            onboardingCompleted = false
            specialization = null
        }
        assertTrue(fixture.resolve(Unit) is AuthState.VerificationEmailSent)
        assertEquals(listOf("/me", "/auth/email/resend-code"), fixture.paths())
    }

    @Test
    fun loginWithMissingSpecializationOpensSelection() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false; specialization = null }
        val useCase = LoginUseCase(fixture.repository, fixture.resend, fixture.resolve)
        assertSame(AuthState.SpecializationSelectionRequired,
            useCase(LoginPayload("developer@example.com", "password")))
        assertEquals(listOf("/auth/login", "/me"), fixture.paths())
    }

    @Test
    fun startupWithMissingSpecializationOpensSelectionAfterRefresh() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "refresh"
            onboardingCompleted = false
            specialization = null
        }
        assertSame(AuthState.SpecializationSelectionRequired,
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh", "/me"), fixture.paths())
    }

    @Test
    fun allEightSpecializationsCanBeSavedWithTheirExactApiCodes() = runBlocking {
        assertEquals(listOf("ANDROID", "IOS", "DOTNET", "FRONTEND", "BACKEND", "QA", "DATA_ML", "DEVOPS"),
            Specialization.entries.map { it.code })
        Specialization.entries.forEach { specialization ->
            val fixture = Fixture().apply { onboardingCompleted = false; this.specialization = null }
            assertEquals(AuthState.PreparationLanguageSelectionRequired(specialization, "en"),
                SelectSpecializationUseCase(fixture.repository, fixture.resolve)(specialization))
            assertEquals(listOf("/me/specialization"), fixture.paths())
            val payload = JsonParser.parseString(fixture.requests.single().second).asJsonObject
            assertEquals(setOf("specialization"), payload.keySet())
            assertEquals(specialization.code, payload["specialization"].asString)
        }
    }

    @Test
    fun specializationSaveDoesNotAuthorizeIfServerOnboardingFlagIsFalse() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false; completeOnSpecializationSave = false }
        assertEquals(AuthState.PreparationLanguageSelectionRequired(Specialization.QA, "en"),
            SelectSpecializationUseCase(fixture.repository, fixture.resolve)(Specialization.QA))
        assertEquals(listOf("/me/specialization"), fixture.paths())
    }

    @Test
    fun specializationHttpErrorDoesNotAuthorizeOrIssueFurtherRequests() = runBlocking {
        val fixture = Fixture().apply { specializationStatus = 500 }
        assertEquals(AuthState.Error("Request failed"),
            SelectSpecializationUseCase(fixture.repository, fixture.resolve)(Specialization.FRONTEND))
        assertEquals(listOf("/me/specialization"), fixture.paths())
    }

    @Test
    fun specializationNetworkErrorIsHandled() = runBlocking {
        val fixture = Fixture().apply { networkFailurePath = "/me/specialization" }
        assertEquals(AuthState.Error("Something unexpected happened"),
            SelectSpecializationUseCase(fixture.repository, fixture.resolve)(Specialization.DEVOPS))
    }

    @Test
    fun missingLanguageOpensSelectionWithoutSendingAnAutomaticPut() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false; preparationLanguage = null }
        assertEquals(AuthState.PreparationLanguageSelectionRequired(Specialization.ANDROID), fixture.resolve(Unit))
        assertEquals(listOf("/me"), fixture.paths())
        assertNull(fixture.preparationLanguage)
    }

    @Test
    fun specializationAndLanguageAreSavedInSeparateExplicitStepsForAllEightChoices() = runBlocking {
        Specialization.entries.forEach { choice ->
            val fixture = Fixture().apply {
                onboardingCompleted = false
                specialization = null
                preparationLanguage = null
                completeOnSpecializationSave = false
            }
            assertEquals(AuthState.PreparationLanguageSelectionRequired(choice),
                SelectSpecializationUseCase(fixture.repository, fixture.resolve)(choice))
            assertEquals(listOf("/me/specialization"), fixture.paths())
            assertNull(fixture.preparationLanguage)
            assertSame(AuthState.Authorized,
                SelectPreparationLanguageUseCase(fixture.repository, fixture.resolve)("en"))
            assertEquals(listOf("/me/specialization", "/me/preparation-language"), fixture.paths())
            assertEquals(choice.code, fixture.specialization)
            assertEquals("en", fixture.preparationLanguage)
        }
    }

    @Test
    fun startupOpensLanguageSelectionAfterRefreshingAndFetchingProfile() = runBlocking {
        val fixture = Fixture().apply {
            tokens.refreshToken = "refresh"
            onboardingCompleted = false
            preparationLanguage = null
        }
        assertEquals(AuthState.PreparationLanguageSelectionRequired(Specialization.ANDROID),
            RestoreSessionUseCase(fixture.repository, fixture.resolve)(Unit).toList().last())
        assertEquals(listOf("/auth/refresh", "/me"), fixture.paths())
    }

    @Test
    fun loginOpensLanguageSelectionWithoutSavingALanguage() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false; preparationLanguage = null }
        assertEquals(AuthState.PreparationLanguageSelectionRequired(Specialization.ANDROID),
            LoginUseCase(fixture.repository, fixture.resend, fixture.resolve)(
                LoginPayload("developer@example.com", "password")))
        assertEquals(listOf("/auth/login", "/me"), fixture.paths())
    }

    @Test
    fun verificationOpensLanguageSelectionWithoutSavingALanguage() = runBlocking {
        val fixture = Fixture().apply { onboardingCompleted = false; preparationLanguage = null }
        assertEquals(AuthState.PreparationLanguageSelectionRequired(Specialization.ANDROID),
            VerifyEmailUseCase(fixture.repository, fixture.resolve)(
                VerifyEmailPayload("developer@example.com", "123456")))
        assertEquals(listOf("/auth/email/verify", "/me"), fixture.paths())
    }

    @Test
    fun existingPreparationLanguageIsNotOverwritten() = runBlocking {
        val fixture = Fixture().apply {
            onboardingCompleted = false
            preparationLanguage = "de"
        }
        assertSame(AuthState.OnboardingRequired, fixture.resolve(Unit))
        assertEquals(listOf("/me"), fixture.paths())
        assertEquals("de", fixture.preparationLanguage)
    }

    @Test
    fun languageSaveDoesNotAuthorizeWhileServerOnboardingFlagIsFalse() = runBlocking {
        val fixture = Fixture().apply {
            onboardingCompleted = false
            preparationLanguage = null
            completeOnLanguageSave = false
        }
        assertSame(AuthState.OnboardingRequired,
            SelectPreparationLanguageUseCase(fixture.repository, fixture.resolve)("en"))
        assertEquals(listOf("/me/preparation-language"), fixture.paths())
        assertEquals("en", fixture.preparationLanguage)
    }

    @Test
    fun languageHttpFailureIsRetriedOnlyThroughAnExplicitLanguageSave() = runBlocking {
        val fixture = Fixture().apply {
            onboardingCompleted = false
            specialization = null
            preparationLanguage = null
            completeOnSpecializationSave = false
            preparationLanguageStatus = 500
        }
        assertEquals(AuthState.PreparationLanguageSelectionRequired(Specialization.IOS),
            SelectSpecializationUseCase(fixture.repository, fixture.resolve)(Specialization.IOS))
        assertEquals(listOf("/me/specialization"), fixture.paths())
        assertEquals(AuthState.Error("Request failed"),
            SelectPreparationLanguageUseCase(fixture.repository, fixture.resolve)("en"))
        assertEquals(AuthState.PreparationLanguageSelectionRequired(Specialization.IOS), fixture.resolve(Unit))
        fixture.preparationLanguageStatus = 200
        assertSame(AuthState.Authorized,
            SelectPreparationLanguageUseCase(fixture.repository, fixture.resolve)("en"))
        assertEquals(listOf("/me/specialization", "/me/preparation-language", "/me",
            "/me/preparation-language"), fixture.paths())
    }

    @Test
    fun languageNetworkFailurePreservesSessionForRetry() = runBlocking {
        val fixture = Fixture().apply {
            onboardingCompleted = false
            preparationLanguage = null
            tokens.refreshToken = "refresh"
            networkFailurePath = "/me/preparation-language"
        }
        assertEquals(AuthState.Error("Something unexpected happened"),
            SelectPreparationLanguageUseCase(fixture.repository, fixture.resolve)("en"))
        assertEquals("refresh", fixture.tokens.refreshToken)
        assertEquals(listOf("/me/preparation-language"), fixture.paths())
    }

    @Test
    fun unchangedLanguageResponseProducesErrorWithoutRepeatingTheRequest() = runBlocking {
        val fixture = Fixture().apply {
            onboardingCompleted = false
            preparationLanguage = null
            ignoreLanguageSave = true
            completeOnLanguageSave = false
        }
        assertEquals(AuthState.Error("Preparation language was not saved"),
            SelectPreparationLanguageUseCase(fixture.repository, fixture.resolve)("en"))
        assertEquals(listOf("/me/preparation-language"), fixture.paths())
    }

    @Test
    fun unverifiedEmailDoesNotTriggerAutomaticLanguageSelection() = runBlocking {
        val fixture = Fixture().apply {
            emailVerified = false
            onboardingCompleted = false
            preparationLanguage = null
        }
        assertTrue(fixture.resolve(Unit) is AuthState.VerificationEmailSent)
        assertEquals(listOf("/me", "/auth/email/resend-code"), fixture.paths())
    }

    @Test
    fun codeInputSupportsPasteLeadingZeroesAndOnlySixAsciiDigits() {
        assertEquals("012345", normalizeEmailCode("01 23-45"))
        assertEquals("123456", normalizeEmailCode("123456789"))
        assertEquals("", normalizeEmailCode("abcdef"))
        assertTrue(isEmailCodeValid("012345"))
        assertFalse(isEmailCodeValid("12345"))
        assertFalse(isEmailCodeValid("1234567"))
        assertFalse(isEmailCodeValid("１２３４５６"))
        assertFalse(isEmailCodeValid("123a56"))
    }
}
