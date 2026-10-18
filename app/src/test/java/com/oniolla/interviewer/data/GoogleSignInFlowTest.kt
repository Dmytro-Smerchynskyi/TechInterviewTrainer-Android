package com.oniolla.interviewer.data

import androidx.lifecycle.ViewModelStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.oniolla.interviewer.data.payload.GoogleSignInPayload
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.AuthViewModel
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleSignInFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    private class Tokens : SessionTokenStorage {
        override var accessToken: String? = null
        override var refreshToken: String? = null
        override var sessionVersion = 0L
    }

    private inner class Fixture {
        val tokens = Tokens()
        val requests = CopyOnWriteArrayList<Request>()
        val bodies = CopyOnWriteArrayList<String>()
        var status = 200
        var emailVerified = true
        var onboarding = true
        var specialization: String? = "ANDROID"
        var language: String? = "en"
        val repository: AuthRepository
        val google: GoogleSignInUseCase
        val resend: ResendVerificationCodeUseCase
        val resolve: ResolveAuthStateUseCase

        init {
            val origin = "https://example.test/"
            val gson = Gson()
            val api = Retrofit.Builder().baseUrl(origin).client(OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, origin))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val path = request.url.encodedPath
                    val code = if (path == "/auth/google") status else 200
                    if (path == "/auth/google") bodies += Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                    val json = if (code >= 400) """{"error":{"code":"GOOGLE_AUTH_FAILED","message":"Google sign-in failed"}}"""
                    else when (path) {
                        "/auth/google", "/auth/refresh" -> """{"accessToken":"google-access","refreshToken":"google-refresh","expiresIn":900}"""
                        "/me" -> """{"id":"google-user","email":"developer@example.com","displayName":"Developer",
                            "emailVerified":$emailVerified,"onboardingCompleted":$onboarding,
                            "specialization":${gson.toJson(specialization)},"preparationLanguage":${gson.toJson(language)},
                            "createdAt":"","updatedAt":"","linkedProviders":["GOOGLE"]}"""
                        "/auth/email/resend-code" -> """{"message":"Sent"}"""
                        "/preparation-languages" -> """{"items":[{"code":"en","name":"English","nativeName":"English","contentAvailable":true}]}"""
                        else -> error("Unexpected endpoint $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                        .body(json.toResponseBody("application/json".toMediaType())).build()
                }.build()).addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create()).build().create(Api::class.java)
            repository = AuthRepository(tokens, api, SessionRefreshCoordinator(tokens, api))
            resend = ResendVerificationCodeUseCase(repository)
            resolve = ResolveAuthStateUseCase(repository, resend)
            google = GoogleSignInUseCase(repository, resolve)
        }

        fun vm(start: Boolean = true) = AuthViewModel(RestoreSessionUseCase(repository, resolve),
            LoginUseCase(repository, resend, resolve), SignupUseCase(repository),
            ForgotPasswordUseCase(repository), VerifyEmailUseCase(repository, resolve),
            resend, ClearSessionUseCase(repository), SelectSpecializationUseCase(repository, resolve),
            GetPreparationLanguagesUseCase(repository), SelectPreparationLanguageUseCase(repository, resolve),
            google, ResetPasswordUseCase(repository)).also {
                stores += ViewModelStore().apply { put("auth", it) }
                if (start) it.start()
            }
        suspend fun ready(vm: AuthViewModel) { vm.stateFlow.first { it is AuthViewState.Unauthorized.Login } }
        fun calls(path: String) = requests.count { it.url.encodedPath == path }
    }

    @Test fun startupWaitsForActivityAndDoesNotRestoreTwice() = runTest(dispatcher) {
        val f = Fixture()
        f.tokens.refreshToken = "saved-refresh"
        val vm = f.vm(start = false)
        runCurrent()
        assertTrue(vm.stateFlow.value is AuthViewState.Loading)
        assertTrue(f.requests.isEmpty())

        vm.start()
        vm.start()
        vm.stateFlow.first { it is AuthViewState.Authorized }
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(1, f.calls("/me"))
        vm.start()
        runCurrent()
        assertEquals(1, f.calls("/auth/refresh"))
    }

    @Test fun deferredStartupDoesNotOverridePasswordResetLink() = runTest(dispatcher) {
        val f = Fixture()
        f.tokens.refreshToken = "saved-refresh"
        val vm = f.vm(start = false)
        vm.openPasswordReset("reset-token")
        vm.start()
        runCurrent()
        assertTrue(vm.stateFlow.value is AuthViewState.PasswordReset)
        assertTrue(f.requests.isEmpty())
    }

    @Test fun googlePayloadSendsExplicitNullWithoutChangingOtherGsonNullHandling() {
        val gson = Gson()
        val tree = gson.fromJson(gson.toJson(GoogleSignInPayload("id-token")), JsonObject::class.java)
        assertEquals("id-token", tree["idToken"].asString)
        assertTrue(tree.has("registrationToken"))
        assertTrue(tree["registrationToken"].isJsonNull)
        assertEquals("{}", gson.toJson(mapOf("other" to null)))
    }

    @Test fun successfulGoogleExchangeSavesTokensAndLoadsProfileWithNewAccessToken() = runTest(dispatcher) {
        val f = Fixture()
        assertEquals(AuthState.Authorized, f.google(GoogleSignInRequest("id-token", false)))
        assertEquals("google-access", f.tokens.accessToken)
        assertEquals("google-refresh", f.tokens.refreshToken)
        val payload = Gson().fromJson(f.bodies.single(), JsonObject::class.java)
        assertEquals("id-token", payload["idToken"].asString)
        assertTrue(payload["registrationToken"].isJsonNull)
        val request = f.requests.single { it.url.encodedPath == "/auth/google" }
        assertEquals("POST", request.method)
        assertNull(request.header("Authorization"))
        assertEquals("Bearer google-access", f.requests.single { it.url.encodedPath == "/me" }.header("Authorization"))
        f.repository.getSessionProfile()
        assertEquals(1, f.calls("/me"))
    }

    @Test fun newGoogleAccountUsesExistingOnboardingResolver() = runTest(dispatcher) {
        val f = Fixture().apply { onboarding = false; specialization = null; language = null }
        assertEquals(AuthState.SpecializationSelectionRequired, f.google(GoogleSignInRequest("id-token", true)))
        assertEquals(0, f.calls("/auth/email/resend-code"))
    }

    @Test fun selectedSpecializationWithoutLanguageOpensLanguageSelection() = runTest(dispatcher) {
        val f = Fixture().apply { onboarding = false; language = null }
        assertTrue(f.google(GoogleSignInRequest("id-token", false)) is AuthState.PreparationLanguageSelectionRequired)
    }

    @Test fun unverifiedEmailUsesExistingVerificationFlow() = runTest(dispatcher) {
        val f = Fixture().apply { emailVerified = false }
        val result = f.google(GoogleSignInRequest("id-token", false))
        assertTrue(result is AuthState.VerificationEmailSent)
        assertEquals(1, f.calls("/auth/email/resend-code"))
    }

    @Test fun cancelledPickerUnlocksSameFormAndDoesNotCallBackend() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.ready(vm)
        vm.showSignup()
        assertTrue(vm.beginGoogleSignIn())
        assertFalse(vm.beginGoogleSignIn())
        vm.showLogin()
        assertTrue(vm.stateFlow.value is AuthViewState.Unauthorized.SignUp)
        vm.googleSignInCancelled()
        val state = vm.stateFlow.value as AuthViewState.Unauthorized.SignUp
        assertFalse(state.isLoading)
        assertFalse(state.isGoogleLoading)
        assertNull(state.googleError)
        assertEquals(0, f.calls("/auth/google"))
    }

    @Test fun providerFailureIsInlineOnLoginAndDoesNotCallBackend() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.ready(vm)
        vm.beginGoogleSignIn()
        vm.googleSignInFailed("Provider unavailable")
        val state = vm.stateFlow.value as AuthViewState.Unauthorized.Login
        assertEquals("Provider unavailable", state.googleError)
        assertFalse(state.isLoading)
        assertEquals(0, f.calls("/auth/google"))
    }

    @Test fun pendingGoogleLoginLocksPasswordLoginAndDuplicateTokenSubmission() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.ready(vm)
        assertTrue(vm.beginGoogleSignIn())
        vm.login("developer@example.com", "password123")
        vm.googleTokenReceived("id-token")
        vm.googleTokenReceived("duplicate")
        vm.googleSignInCancelled()
        vm.stateFlow.first { it == AuthViewState.Authorized }
        assertEquals(1, f.calls("/auth/google"))
        assertEquals(0, f.calls("/auth/login"))
    }

    @Test fun backendErrorUnlocksSignupWithoutDiscardingItsScreen() = runTest(dispatcher) {
        val f = Fixture().apply { status = 401 }
        val vm = f.vm()
        f.ready(vm)
        vm.showSignup()
        vm.beginGoogleSignIn()
        vm.googleTokenReceived("bad-id-token")
        val state = vm.stateFlow.first {
            it is AuthViewState.Unauthorized.SignUp && !it.isLoading && it.googleError != null
        } as AuthViewState.Unauthorized.SignUp
        assertEquals("Google sign-in failed", state.googleError)
        assertNull(f.tokens.accessToken)
        assertEquals(0, f.calls("/auth/refresh"))
        assertEquals(0, f.calls("/me"))
    }

    @Test fun blankCredentialDoesNotCallBackendAndCanRetry() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.ready(vm)
        vm.beginGoogleSignIn()
        vm.googleTokenReceived(" ")
        assertFalse((vm.stateFlow.value as AuthViewState.Unauthorized.Login).isLoading)
        assertEquals(0, f.calls("/auth/google"))
        assertTrue(vm.beginGoogleSignIn())
    }
}
