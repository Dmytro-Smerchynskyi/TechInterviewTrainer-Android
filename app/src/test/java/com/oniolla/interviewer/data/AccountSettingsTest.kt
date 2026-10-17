package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.AuthViewModel
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import com.oniolla.interviewer.ui.home.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    private class Tokens : SessionTokenStorage {
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
        override var sessionVersion = 1L
    }

    private inner class Fixture {
        val tokens = Tokens()
        val requests = CopyOnWriteArrayList<Request>()
        var name = "Developer"
        var email = "developer@example.com"
        var providers = listOf("PASSWORD")
        var emailVerified = true
        var registrationPassword = "signup-password"
        var verificationAttempts = 0
        var failPath: String? = null
        var failStatus = 400
        var failCode = "INVALID"
        var failMessage = "Server validation failed"
        var unauthorizedPath: String? = null
        var rejectCurrentPassword = false
        var rejectRefresh = false
        var passwordErrorCode = "WRONG_PASSWORD"
        var passwordErrorStatus = 401
        var beforeMutation: (() -> Unit)? = null
        val repository: AuthRepository
        val manage: ManageAccountUseCase

        init {
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, "https://example.test/"))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val path = request.url.encodedPath
                    if (path != "/me" && path != "/me/usage" && path != "/auth/refresh") beforeMutation?.invoke()
                    var status = 200
                    val body = when {
                        path in setOf("/auth/password/change", "/auth/email/change/request",
                            "/auth/email/change/resend") && rejectCurrentPassword &&
                            (path != unauthorizedPath || request.header("Authorization") == "Bearer refreshed") -> {
                            status = passwordErrorStatus
                            """{"error":{"code":"$passwordErrorCode","message":"Current password is incorrect."}}"""
                        }
                        path == "/auth/refresh" && rejectRefresh -> {
                            status = 401
                            """{"error":{"code":"UNAUTHORIZED","message":"Session expired"}}"""
                        }
                        path == unauthorizedPath -> {
                            unauthorizedPath = null
                            status = 401
                            """{"error":{"code":"UNAUTHORIZED","message":"Expired"}}"""
                        }
                        path == failPath -> {
                            status = failStatus
                            """{"error":{"code":"$failCode","message":"$failMessage"}}"""
                        }
                        path == "/auth/email/verify" -> {
                            val input = payload(request)
                            val password = input.get("password")?.asString
                            when {
                                password.isNullOrEmpty() -> {
                                    status = 400
                                    """{"error":{"code":"VALIDATION_ERROR","message":"Password is required"}}"""
                                }
                                else -> {
                                    verificationAttempts++
                                    if (password != registrationPassword || input.get("code").asString != "123456") {
                                        status = 400
                                        """{"error":{"code":"INVALID_VERIFICATION_CODE","message":"Invalid verification code or password"}}"""
                                    } else {
                                        emailVerified = true
                                        credentials()
                                    }
                                }
                            }
                        }
                        path == "/auth/email/resend-code" -> """{"message":"Sent"}"""
                        path == "/me" -> profile()
                        path == "/me/usage" -> """{"plan":"FREE","answerReveals":{"used":12,"limit":20,"remaining":8},
                            "aiEvaluations":{"used":8,"reserved":0,"limit":20,"remaining":12,"periodEndsAt":null,"resetAt":null},
                            "questions":{"unlimited":true,"limitPerTopic":null}}"""
                        path == "/me/name" -> { name = payload(request).get("name").asString; profile() }
                        path == "/auth/email/change/request" || path == "/auth/email/change/resend" -> { status = 204; "" }
                        path == "/auth/email/change/confirm" -> {
                            email = payload(request).get("newEmail").asString
                            credentials()
                        }
                        path == "/auth/login" -> credentials()
                        path == "/auth/register" -> {
                            registrationPassword = payload(request).get("password").asString
                            emailVerified = false
                            """{"message":"Sent"}"""
                        }
                        path == "/auth/password/reset" -> { status = 204; "" }
                        path == "/auth/password/change" -> credentials()
                        path == "/auth/password/set" -> { providers = providers + "PASSWORD"; credentials() }
                        path == "/auth/refresh" -> """{"accessToken":"refreshed","refreshToken":"refreshed-refresh","expiresIn":900}"""
                        else -> error("Unexpected request $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(status).message("Test").body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory.create())
                .build().create(Api::class.java)
            repository = AuthRepository(tokens, api, SessionRefreshCoordinator(tokens, api))
            manage = ManageAccountUseCase(repository)
        }
        private fun profile() = """{"id":"user","email":"$email","displayName":"$name","emailVerified":$emailVerified,
            "specialization":"ANDROID","createdAt":"","updatedAt":"","onboardingCompleted":true,
            "linkedProviders":${Gson().toJson(providers)},"preparationLanguage":"en"}"""
        private fun credentials() = """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
        fun payload(request: Request): JsonObject =
            Gson().fromJson(Buffer().also { request.body!!.writeTo(it) }.readUtf8(), JsonObject::class.java)
        fun calls(path: String) = requests.count { it.url.encodedPath == path }
        fun request(path: String) = requests.last { it.url.encodedPath == path }
        fun authVm(): AuthViewModel {
            val resend = ResendVerificationCodeUseCase(repository)
            val resolve = ResolveAuthStateUseCase(repository, resend)
            return AuthViewModel(RestoreSessionUseCase(repository, resolve),
                LoginUseCase(repository, resend, resolve), SignupUseCase(repository),
                ForgotPasswordUseCase(repository), VerifyEmailUseCase(repository, resolve),
                resend, ClearSessionUseCase(repository), SelectSpecializationUseCase(repository, resolve),
                GetPreparationLanguagesUseCase(repository), SelectPreparationLanguageUseCase(repository, resolve),
                GoogleSignInUseCase(repository, resolve), ResetPasswordUseCase(repository))
                .also { stores += ViewModelStore().apply { put("auth", it) } }
        }

        fun vm(handle: SavedStateHandle = SavedStateHandle()) =
            AccountViewModel(manage, handle).also { stores += ViewModelStore().apply { put("account", it) } }
    }

    private fun resetPassword(vm: AuthViewModel, password: String = "new-password") {
        vm.passwordText.value = password
        vm.resetConfirmationText.value = password
    }

    @Test fun passwordResetPostsExactPayloadWithoutBearerAndReturnsToLogin() = runTest(dispatcher) {
        val f = Fixture()
        f.tokens.clear()
        val vm = f.authVm()
        vm.openPasswordReset("opaque-reset-token")
        resetPassword(vm)
        vm.submitPasswordReset()
        assertTrue((vm.stateFlow.value as AuthViewState.PasswordReset).isLoading)
        vm.stateFlow.first { it is AuthViewState.PasswordResetFinished }
        assertEquals(AuthViewState.PasswordResetFinished(false), vm.stateFlow.value)
        val request = f.request("/auth/password/reset")
        assertEquals("POST", request.method)
        assertNull(request.header("Authorization"))
        val body = f.payload(request)
        assertEquals(setOf("token", "password"), body.keySet())
        assertEquals("opaque-reset-token", body["token"].asString)
        assertEquals("new-password", body["password"].asString)
        assertEquals("", vm.passwordText.value)
        assertEquals("", vm.resetConfirmationText.value)
        assertNull(f.tokens.refreshToken)
        assertEquals(0, f.calls("/auth/refresh"))
        vm.leavePasswordReset()
        assertTrue(vm.stateFlow.value is AuthViewState.Unauthorized.Login)
    }

    @Test fun passwordResetKeepsExistingSessionAndReturnsToApp() = runTest(dispatcher) {
        val f = Fixture()
        val session = f.tokens.snapshot()
        val vm = f.authVm()
        vm.openPasswordReset("reset-token")
        resetPassword(vm)
        vm.submitPasswordReset()
        vm.stateFlow.first { it is AuthViewState.PasswordResetFinished }
        assertNull(f.request("/auth/password/reset").header("Authorization"))
        assertEquals(session, f.tokens.snapshot())
        assertEquals(0, f.calls("/auth/refresh"))
        vm.leavePasswordReset()
        assertEquals(AuthViewState.Authorized, vm.stateFlow.value)
    }

    @Test fun passwordResetInvalidExpiredOrUsedTokenLeavesFormAndPreservesSession() = runTest(dispatcher) {
        for (signedIn in listOf(false, true)) {
            val f = Fixture().apply {
                failPath = "/auth/password/reset"
                failCode = "VALIDATION_ERROR"
                failMessage = "Reset token is invalid or expired."
                if (!signedIn) tokens.clear()
            }
            val session = f.tokens.snapshot()
            val vm = f.authVm()
            vm.openPasswordReset("invalid-or-expired")
            resetPassword(vm)
            vm.submitPasswordReset()
            vm.stateFlow.first { it is AuthViewState.PasswordResetFinished }
            assertEquals(AuthViewState.PasswordResetFinished(true), vm.stateFlow.value)
            assertEquals("", vm.passwordText.value)
            assertEquals("", vm.resetConfirmationText.value)
            assertEquals(session, f.tokens.snapshot())
            assertEquals(1, f.calls("/auth/password/reset"))
            vm.leavePasswordReset()
            assertEquals(signedIn, vm.stateFlow.value is AuthViewState.Authorized)
        }
    }

    @Test fun passwordResetValidationAndServerErrorsKeepInputsAndAllowRetry() = runTest(dispatcher) {
        val f = Fixture().apply { tokens.clear() }
        val vm = f.authVm()
        vm.openPasswordReset("reset-token")
        resetPassword(vm, "short")
        vm.submitPasswordReset()
        assertNotNull((vm.stateFlow.value as AuthViewState.PasswordReset).passwordError)
        assertEquals("short", vm.passwordText.value)
        assertEquals(0, f.calls("/auth/password/reset"))
        resetPassword(vm)
        vm.resetConfirmationText.value = "different-password"
        vm.submitPasswordReset()
        assertNotNull((vm.stateFlow.value as AuthViewState.PasswordReset).confirmationError)
        assertEquals(0, f.calls("/auth/password/reset"))
        resetPassword(vm)
        f.failPath = "/auth/password/reset"
        f.failCode = "VALIDATION_ERROR"
        f.failMessage = "Some other validation error"
        vm.submitPasswordReset()
        vm.stateFlow.first { it is AuthViewState.PasswordReset && !it.isLoading }
        assertEquals("Some other validation error", (vm.stateFlow.value as AuthViewState.PasswordReset).error)
        assertEquals("new-password", vm.passwordText.value)
        assertEquals("new-password", vm.resetConfirmationText.value)
        f.failPath = null
        vm.submitPasswordReset()
        vm.stateFlow.first { it is AuthViewState.PasswordResetFinished }
        assertEquals(2, f.calls("/auth/password/reset"))
    }

    @Test fun passwordResetUnauthorizedDoesNotRefreshOrLogOutAnExistingSession() = runTest(dispatcher) {
        val f = Fixture().apply {
            failPath = "/auth/password/reset"
            failStatus = 401
            failCode = "UNAUTHORIZED"
        }
        val session = f.tokens.snapshot()
        val vm = f.authVm()
        vm.openPasswordReset("reset-token")
        resetPassword(vm)
        vm.submitPasswordReset()
        vm.stateFlow.first { it is AuthViewState.PasswordReset && !it.isLoading }
        assertNotNull((vm.stateFlow.value as AuthViewState.PasswordReset).error)
        assertEquals("new-password", vm.passwordText.value)
        assertEquals(session, f.tokens.snapshot())
        assertEquals(0, f.calls("/auth/refresh"))
        assertEquals(1, f.calls("/auth/password/reset"))
    }

    @Test fun passwordResetBlocksDuplicateSubmissionsAndBackDuringRequest() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply {
            tokens.clear()
            beforeMutation = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        }
        val vm = f.authVm()
        vm.openPasswordReset("reset-token")
        resetPassword(vm)
        vm.submitPasswordReset()
        try {
            started.await()
            vm.submitPasswordReset()
            vm.closePasswordReset()
            assertTrue((vm.stateFlow.value as AuthViewState.PasswordReset).isLoading)
            assertEquals(1, f.calls("/auth/password/reset"))
        } finally { release.countDown() }
        vm.stateFlow.first { it is AuthViewState.PasswordResetFinished }
    }

    @Test fun newResetLinkCancelsOldResponseAndDoesNotCloseNewForm() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply {
            tokens.clear()
            beforeMutation = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        }
        val vm = f.authVm()
        vm.openPasswordReset("old-token")
        resetPassword(vm)
        vm.submitPasswordReset()
        try {
            started.await()
            vm.openPasswordReset("new-token")
            assertEquals("", vm.passwordText.value)
            assertEquals("", vm.resetConfirmationText.value)
            resetPassword(vm, "replacement-password")
        } finally { release.countDown() }
        // Complete a new request too: it must use the replacement token and password.
        f.beforeMutation = null
        vm.submitPasswordReset()
        vm.stateFlow.first { it is AuthViewState.PasswordResetFinished }
        val body = f.payload(f.request("/auth/password/reset"))
        assertEquals("new-token", body["token"].asString)
        assertEquals("replacement-password", body["password"].asString)
    }

    @Test fun passwordResetMissingTokenAndBackRouteUsingCurrentSessionAndClearSecrets() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.authVm()
        vm.openPasswordReset(null)
        assertEquals(AuthViewState.PasswordResetFinished(true), vm.stateFlow.value)
        vm.leavePasswordReset()
        assertEquals(AuthViewState.Authorized, vm.stateFlow.value)
        vm.openPasswordReset("token")
        resetPassword(vm)
        f.tokens.clear()
        vm.closePasswordReset()
        assertEquals("", vm.passwordText.value)
        assertEquals("", vm.resetConfirmationText.value)
        vm.leavePasswordReset()
        assertTrue(vm.stateFlow.value is AuthViewState.Unauthorized.Login)
        assertEquals(0, f.calls("/auth/password/reset"))
    }

    @Test fun passwordResetSurvivesRotationOnlyInViewModelMemory() = runTest(dispatcher) {
        val f = Fixture().apply { tokens.clear() }
        val controller = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = f.authVm() as T
        }
        try {
            val vm = androidx.lifecycle.ViewModelProvider(controller.get(), factory)[AuthViewModel::class.java]
            vm.openPasswordReset("rotation-token")
            resetPassword(vm)
            val configuration = android.content.res.Configuration(controller.get().resources.configuration)
            configuration.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            controller.configurationChange(configuration)
            val recreated = androidx.lifecycle.ViewModelProvider(controller.get(), factory)[AuthViewModel::class.java]
            assertSame(vm, recreated)
            assertEquals("new-password", recreated.passwordText.value)
            assertEquals("new-password", recreated.resetConfirmationText.value)
            assertEquals("", f.authVm().passwordText.value)
            recreated.submitPasswordReset()
            recreated.stateFlow.first { it is AuthViewState.PasswordResetFinished }
            assertEquals("rotation-token", f.payload(f.request("/auth/password/reset"))["token"].asString)
        } finally { controller.pause().stop().destroy() }
    }

    private suspend fun open(vm: AccountViewModel) {
        vm.open(null)
        vm.state.first { it.profile is ContentViewState.Ready }
    }
    private suspend fun finished(vm: AccountViewModel) {
        vm.state.first { it.page == AccountPage.OVERVIEW && it.changed && !it.isSaving }
    }
    private fun password(vm: AccountViewModel, current: String = "currentpass", next: String = "newpassword") {
        vm.edit(AccountPage.PASSWORD)
        vm.update(AccountField.CURRENT_PASSWORD, current)
        vm.update(AccountField.NEW_PASSWORD, next)
        vm.update(AccountField.CONFIRM_PASSWORD, next)
    }

    @Test fun authPasswordSurvivesActivityRecreationButNeverRestoresInANewViewModel() = runTest(dispatcher) {
        val f = Fixture()
        f.tokens.clear()
        val controller = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = f.authVm() as T
        }
        try {
            val vm = androidx.lifecycle.ViewModelProvider(controller.get(), factory)[AuthViewModel::class.java]
            vm.stateFlow.first { it is AuthViewState.Unauthorized.Login }
            vm.passwordText.value = "login-password"
            val configuration = android.content.res.Configuration(controller.get().resources.configuration)
            configuration.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            controller.configurationChange(configuration)
            val recreated = androidx.lifecycle.ViewModelProvider(controller.get(), factory)[AuthViewModel::class.java]
            assertSame(vm, recreated)
            assertEquals("login-password", recreated.passwordText.value)
            recreated.showSignup()
            assertEquals("", recreated.passwordText.value)
            recreated.passwordText.value = "signup-password"
            configuration.orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT
            controller.configurationChange(configuration)
            val signup = androidx.lifecycle.ViewModelProvider(controller.get(), factory)[AuthViewModel::class.java]
            assertSame(vm, signup)
            assertEquals("signup-password", signup.passwordText.value)
            assertEquals("", f.authVm().passwordText.value)
            signup.showLogin()
            assertEquals("", signup.passwordText.value)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun loginKeepsPasswordAfterValidationAndServerErrorsAndClearsOnSuccess() = runTest(dispatcher) {
        val f = Fixture()
        f.tokens.clear()
        val vm = f.authVm()
        vm.stateFlow.first { it is AuthViewState.Unauthorized.Login }
        vm.passwordText.value = "short"
        vm.login("not-email", vm.passwordText.value)
        assertEquals("short", vm.passwordText.value)
        assertEquals(0, f.calls("/auth/login"))
        vm.passwordText.value = "login-password"
        f.failPath = "/auth/login"
        vm.login("developer@example.com", vm.passwordText.value)
        vm.stateFlow.first { it is AuthViewState.Unauthorized.Login && !it.isLoading }
        assertEquals("login-password", vm.passwordText.value)
        assertEquals(1, f.calls("/auth/login"))
        assertTrue(vm.beginGoogleSignIn())
        vm.googleSignInCancelled()
        assertEquals("login-password", vm.passwordText.value)
        f.failPath = null
        vm.login("developer@example.com", vm.passwordText.value)
        vm.stateFlow.first { it is AuthViewState.Authorized }
        assertEquals("", vm.passwordText.value)
    }

    @Test fun signupKeepsPasswordUntilVerificationSucceeds() = runTest(dispatcher) {
        io.reactivex.rxjava3.android.plugins.RxAndroidPlugins.setMainThreadSchedulerHandler {
            io.reactivex.rxjava3.schedulers.Schedulers.trampoline()
        }
        try {
            val f = Fixture()
            f.tokens.clear()
            val vm = f.authVm()
            vm.stateFlow.first { it is AuthViewState.Unauthorized.Login }
            vm.showSignup()
            vm.passwordText.value = "short"
            vm.signup("", "not-email", vm.passwordText.value)
            assertEquals("short", vm.passwordText.value)
            assertEquals(0, f.calls("/auth/register"))
            vm.passwordText.value = "signup-password"
            f.failPath = "/auth/register"
            vm.signup("Developer", "developer@example.com", vm.passwordText.value)
            vm.stateFlow.first { it is AuthViewState.Unauthorized.SignUp && !it.isLoading }
            assertEquals("signup-password", vm.passwordText.value)
            assertEquals(1, f.calls("/auth/register"))
            assertTrue(vm.beginGoogleSignIn())
            vm.googleSignInFailed("Google unavailable")
            assertEquals("signup-password", vm.passwordText.value)
            f.failPath = null
            vm.signup("Developer", "developer@example.com", vm.passwordText.value)
            vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation }
            assertEquals("signup-password", vm.passwordText.value)
            vm.updateEmailCode("123456")
            vm.confirmEmail()
            vm.stateFlow.first { it is AuthViewState.Authorized }
            assertEquals("", vm.passwordText.value)
        } finally {
            io.reactivex.rxjava3.android.plugins.RxAndroidPlugins.reset()
        }
    }

    @Test fun signupConflictUsesCustomErrorCodeAndShowsEmailFieldErrorWithoutClearingPassword() = runTest(dispatcher) {
        io.reactivex.rxjava3.android.plugins.RxAndroidPlugins.setMainThreadSchedulerHandler {
            io.reactivex.rxjava3.schedulers.Schedulers.trampoline()
        }
        try {
            for (message in listOf("Email is already in use.", "")) {
                val f = Fixture().apply {
                    failPath = "/auth/register"
                    failStatus = 409
                    failCode = "EMAIL_ALREADY_IN_USE"
                    failMessage = message
                }
                f.tokens.clear()
                val vm = f.authVm()
                vm.stateFlow.first { it is AuthViewState.Unauthorized.Login }
                vm.showSignup()
                vm.passwordText.value = "signup-password"
                vm.signup("Developer", "used@example.com", vm.passwordText.value)
                val state = vm.stateFlow.first {
                    it is AuthViewState.Unauthorized.SignUp && !it.isLoading && it.emailError != null
                } as AuthViewState.Unauthorized.SignUp
                assertEquals(message.ifBlank { "This email is already in use." }, state.emailError)
                assertNull(state.passwordError)
                assertNull(state.nameError)
                assertEquals("signup-password", vm.passwordText.value)
                assertEquals(1, f.calls("/auth/register"))
                assertEquals(0, f.calls("/auth/email/resend-code"))
                assertEquals(0, f.calls("/auth/refresh"))
                vm.clearEmailError()
                assertNull((vm.stateFlow.value as AuthViewState.Unauthorized.SignUp).emailError)
                f.failPath = null
                vm.signup("Developer", "available@example.com", vm.passwordText.value)
                vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation }
                assertEquals("signup-password", vm.passwordText.value)
                vm.updateEmailCode("123456")
                vm.confirmEmail()
                vm.stateFlow.first { it is AuthViewState.Authorized }
                assertEquals("", vm.passwordText.value)
                assertEquals(2, f.calls("/auth/register"))
            }
        } finally {
            io.reactivex.rxjava3.android.plugins.RxAndroidPlugins.reset()
        }
    }

    @Test fun registrationPasswordSurvivesVerificationErrorsResendAndRotationUntilSuccess() = runTest(dispatcher) {
        io.reactivex.rxjava3.android.plugins.RxAndroidPlugins.setMainThreadSchedulerHandler {
            io.reactivex.rxjava3.schedulers.Schedulers.trampoline()
        }
        val f = Fixture()
        f.tokens.clear()
        val controller = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup()
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = f.authVm() as T
        }
        try {
            val vm = androidx.lifecycle.ViewModelProvider(controller.get(), factory)[AuthViewModel::class.java]
            vm.stateFlow.first { it is AuthViewState.Unauthorized.Login }
            vm.showSignup()
            vm.passwordText.value = "registration-password"
            vm.signup("Developer", "developer@example.com", vm.passwordText.value)
            val confirmation = vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation } as AuthViewState.Unauthorized.EmailConfirmation
            assertFalse(confirmation.requiresPassword)
            assertEquals("registration-password", vm.passwordText.value)
            val configuration = android.content.res.Configuration(controller.get().resources.configuration)
            configuration.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE
            controller.configurationChange(configuration)
            val recreated = androidx.lifecycle.ViewModelProvider(controller.get(), factory)[AuthViewModel::class.java]
            assertSame(vm, recreated)
            assertEquals("registration-password", recreated.passwordText.value)
            assertEquals("", f.authVm().passwordText.value)
            vm.updateEmailCode("123")
            vm.confirmEmail()
            assertEquals(0, f.calls("/auth/email/verify"))
            assertEquals("registration-password", vm.passwordText.value)
            vm.updateEmailCode("000000")
            vm.confirmEmail()
            val rejected = vm.stateFlow.first {
                it is AuthViewState.Unauthorized.EmailConfirmation && !it.isLoading && it.codeError != null
            } as AuthViewState.Unauthorized.EmailConfirmation
            assertEquals("Invalid verification code or password", rejected.codeError)
            assertEquals(1, f.verificationAttempts)
            assertEquals("registration-password", vm.passwordText.value)
            vm.resendEmailCode()
            vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation && !it.isLoading }
            assertEquals("registration-password", vm.passwordText.value)
            vm.updateEmailCode("123456")
            vm.confirmEmail()
            vm.stateFlow.first { it is AuthViewState.Authorized }
            assertEquals("", vm.passwordText.value)
            val request = f.request("/auth/email/verify")
            val body = f.payload(request)
            assertEquals(setOf("email", "code", "password"), body.keySet())
            assertEquals("registration-password", body.get("password").asString)
            assertEquals("developer@example.com", body.get("email").asString)
            assertEquals("123456", body.get("code").asString)
            assertNull(request.header("Authorization"))
            assertEquals(2, f.verificationAttempts)
            assertEquals("new-refresh", f.tokens.refreshToken)
        } finally {
            controller.pause().stop().destroy()
            io.reactivex.rxjava3.android.plugins.RxAndroidPlugins.reset()
        }
    }

    @Test fun loginVerificationCarriesTheEnteredPasswordAndLeavingConfirmationClearsIt() = runTest(dispatcher) {
        val f = Fixture().apply {
            failPath = "/auth/login"
            failStatus = 403
            failCode = "EMAIL_NOT_VERIFIED"
        }
        f.tokens.clear()
        val vm = f.authVm()
        vm.stateFlow.first { it is AuthViewState.Unauthorized.Login }
        vm.login("developer@example.com", "signup-password")
        val state = vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation } as AuthViewState.Unauthorized.EmailConfirmation
        assertFalse(state.requiresPassword)
        assertEquals("signup-password", vm.passwordText.value)
        vm.changeEmailAddress()
        vm.stateFlow.first { it is AuthViewState.Unauthorized.Login }
        assertEquals("", vm.passwordText.value)
    }

    @Test fun startupVerificationRequestsPasswordLocallyAndNeverSendsAnEmptyPassword() = runTest(dispatcher) {
        val f = Fixture().apply { emailVerified = false }
        val vm = f.authVm()
        val state = vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation } as AuthViewState.Unauthorized.EmailConfirmation
        assertTrue(state.requiresPassword)
        assertEquals("", vm.passwordText.value)
        vm.updateEmailCode("123456")
        vm.confirmEmail()
        assertNotNull((vm.stateFlow.value as AuthViewState.Unauthorized.EmailConfirmation).passwordError)
        assertEquals(0, f.calls("/auth/email/verify"))
        assertEquals(0, f.verificationAttempts)
        vm.resendEmailCode()
        val resent = vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation && !it.isLoading } as AuthViewState.Unauthorized.EmailConfirmation
        assertTrue(resent.requiresPassword)
        vm.passwordText.value = "wrong-password"
        vm.clearPasswordError()
        vm.updateEmailCode("123456")
        vm.confirmEmail()
        vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation && !it.isLoading && it.codeError != null }
        assertEquals("wrong-password", vm.passwordText.value)
        assertEquals(1, f.verificationAttempts)
        assertEquals(1, f.calls("/auth/email/verify"))
        vm.passwordText.value = "signup-password"
        vm.clearPasswordError()
        vm.updateEmailCode("123456")
        vm.confirmEmail()
        vm.stateFlow.first { it is AuthViewState.Authorized }
        assertEquals("", vm.passwordText.value)
    }

    @Test fun rejectedGoogleProofKeepsSessionAndAllowsFreshRetryForRequestResendAndSetup() = runTest(dispatcher) {
        for (path in listOf("/auth/email/change/request", "/auth/email/change/resend", "/auth/password/set")) {
            for (expiredBearer in listOf(false, true)) {
                val f = Fixture().apply { providers = listOf("GOOGLE") }
                val vm = f.vm()
                open(vm)
                if (path == "/auth/password/set") password(vm, "")
                else {
                    vm.edit(AccountPage.EMAIL)
                    vm.update(AccountField.EMAIL, "new@example.com")
                    if (path.endsWith("/resend")) {
                        vm.submit()
                        vm.googleTokenReceived(requireNotNull(vm.state.value.googleRequestId), "initial-proof")
                        vm.state.first { it.page == AccountPage.CODE && !it.isSaving }
                    }
                }
                val session = f.tokens.snapshot()
                f.failPath = path
                f.failStatus = 401
                f.failCode = "REAUTHENTICATION_REQUIRED"
                f.failMessage = "Sign in with your linked provider again to confirm this action."
                if (expiredBearer) f.unauthorizedPath = path
                if (path.endsWith("/resend")) vm.resend() else vm.submit()
                val requestId = requireNotNull(vm.state.value.googleRequestId)
                vm.googleTokenReceived(requestId, "rejected-proof")
                vm.state.first { !it.isSaving }
                assertFalse(vm.state.value.sessionExpired)
                assertFalse(vm.state.value.changed)
                assertEquals(f.failMessage, vm.state.value.googleError)
                assertNull(vm.state.value.googleRequestId)
                assertEquals("", vm.state.value.currentPassword)
                assertEquals(if (path == "/auth/password/set") "newpassword" else "", vm.state.value.newPassword)
                assertEquals(if (path == "/auth/password/set") "newpassword" else "", vm.state.value.confirmPassword)
                assertEquals(if (expiredBearer) 1 else 0, f.calls("/auth/refresh"))
                if (expiredBearer) {
                    assertEquals("refreshed", f.tokens.accessToken)
                    assertEquals("refreshed-refresh", f.tokens.refreshToken)
                    assertEquals(session.version, f.tokens.sessionVersion)
                } else assertEquals(session, f.tokens.snapshot())
                val callsBeforeRetry = f.calls(path)
                f.failPath = null
                if (path == "/auth/password/set") password(vm, "")
                if (path.endsWith("/resend")) vm.resend() else vm.submit()
                val retryId = requireNotNull(vm.state.value.googleRequestId)
                assertNotEquals(requestId, retryId)
                assertEquals(callsBeforeRetry, f.calls(path))
                vm.googleTokenReceived(retryId, "fresh-retry-proof")
                vm.state.first { !it.isSaving }
                assertNull(vm.state.value.googleError)
                assertFalse(vm.state.value.sessionExpired)
                assertEquals(callsBeforeRetry + 1, f.calls(path))
                assertEquals("fresh-retry-proof", f.payload(f.request(path)).get("idToken").asString)
                assertEquals(0, f.calls("/auth/google"))
            }
        }
    }

    @Test fun providersComeFromMeAndAccountDoesNotRequestUsage() = runTest(dispatcher) {
        val f = Fixture()
        f.repository.getMe()
        val vm = f.vm()
        open(vm)
        assertTrue(vm.state.value.hasPassword)
        vm.open(null)
        runCurrent()
        assertEquals(1, f.calls("/me"))
        assertEquals(0, f.calls("/me/usage"))
    }

    @Test fun nameValidationPreventsCallAndSuccessfulSaveUpdatesCachedMe() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.NAME)
        vm.update(AccountField.NAME, " ")
        vm.submit()
        assertNotNull(vm.state.value.errors[AccountField.NAME])
        assertEquals(0, f.calls("/me/name"))
        vm.update(AccountField.NAME, " New Developer ")
        vm.submit()
        finished(vm)
        assertEquals("New Developer", f.repository.getSessionProfile().displayName)
        assertEquals("PUT", f.request("/me/name").method)
        assertEquals("New Developer", f.payload(f.request("/me/name")).get("name").asString)
        assertEquals(1, f.calls("/me"))
    }

    @Test fun emailValidationAndFailedRequestKeepEditor() = runTest(dispatcher) {
        val f = Fixture().apply { failPath = "/auth/email/change/request" }
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "invalid")
        vm.submit()
        assertNotNull(vm.state.value.errors[AccountField.EMAIL])
        assertEquals(0, f.calls("/auth/email/change/request"))
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.submit()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.EMAIL, vm.state.value.page)
        assertFalse(vm.state.value.changed)
        assertEquals("access", f.tokens.accessToken)
    }

    @Test fun emailRequestOpensCodeOnlyAfterSuccessAndNoBackButChangeAddressReturnsToEditor() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, " new@example.com ")
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        assertFalse(vm.back())
        assertEquals(AccountPage.CODE, vm.state.value.page)
        assertEquals("new@example.com", vm.state.value.email)
        assertEquals("POST", f.request("/auth/email/change/request").method)
        vm.changeEmailAddress()
        assertEquals(AccountPage.EMAIL, vm.state.value.page)
        assertEquals("new@example.com", vm.state.value.email)
    }

    @Test fun resendUsesNewEmailAndBlocksConfirmAndNavigationUntilItFinishes() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        vm.update(AccountField.CODE, "123456")
        vm.resend()
        assertTrue(vm.state.value.showResendPasswordDialog)
        assertEquals(0, f.calls("/auth/email/change/resend"))
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.resend()
        assertTrue(vm.state.value.isResending)
        vm.submit()
        vm.changeEmailAddress()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.CODE, vm.state.value.page)
        assertEquals("", vm.state.value.code)
        assertEquals("new@example.com", f.payload(f.request("/auth/email/change/resend")).get("newEmail").asString)
        assertEquals(0, f.calls("/auth/email/change/confirm"))
    }

    @Test fun codeValidationClipboardAndConfirmRotateBothTokensAndRefreshMe() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        vm.pasteCode("not a code")
        vm.submit()
        assertNotNull(vm.state.value.errors[AccountField.CODE])
        assertEquals(0, f.calls("/auth/email/change/confirm"))
        vm.pasteCode(" 615806 ")
        assertEquals("615806", vm.state.value.code)
        vm.submit()
        finished(vm)
        assertEquals("new-access", f.tokens.accessToken)
        assertEquals("new-refresh", f.tokens.refreshToken)
        assertEquals(1L, f.tokens.sessionVersion)
        assertEquals("new@example.com", (vm.state.value.profile as ContentViewState.Ready).content.email)
        val payload = f.payload(f.request("/auth/email/change/confirm"))
        assertEquals("615806", payload.get("code").asString)
        assertEquals("new@example.com", payload.get("newEmail").asString)
        assertEquals("Bearer new-access", f.request("/me").header("Authorization"))
    }

    @Test fun failedConfirmationKeepsCodePageAndCredentials() = runTest(dispatcher) {
        val f = Fixture().apply { failPath = "/auth/email/change/confirm" }
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        vm.update(AccountField.CODE, "123456")
        vm.submit()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.CODE, vm.state.value.page)
        assertEquals("Server validation failed", vm.state.value.errors[AccountField.CODE])
        assertEquals("access", f.tokens.accessToken)
        assertEquals("refresh", f.tokens.refreshToken)
    }

    @Test fun passwordValidationChecksLengthCurrentAndConfirmationWithoutCallingApi() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        password(vm, "", "short")
        vm.submit()
        assertEquals(setOf(AccountField.CURRENT_PASSWORD, AccountField.NEW_PASSWORD, AccountField.CONFIRM_PASSWORD),
            vm.state.value.errors.keys)
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.update(AccountField.NEW_PASSWORD, "newpassword")
        vm.update(AccountField.CONFIRM_PASSWORD, "otherpassword")
        vm.submit()
        assertEquals("Passwords do not match", vm.state.value.errors[AccountField.CONFIRM_PASSWORD])
        assertEquals("currentpass", vm.state.value.currentPassword)
        assertEquals("newpassword", vm.state.value.newPassword)
        assertEquals("otherpassword", vm.state.value.confirmPassword)
        assertEquals(0, f.calls("/auth/password/change"))
    }

    @Test fun passwordChangeUsesCurrentAndNewAndClearsSecretsAfterSuccess() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        password(vm)
        vm.submit()
        finished(vm)
        val body = f.payload(f.request("/auth/password/change"))
        assertEquals(setOf("currentPassword", "newPassword"), body.keySet())
        assertEquals("currentpass", body.get("currentPassword").asString)
        assertEquals("newpassword", body.get("newPassword").asString)
        assertEquals("new-access", f.tokens.accessToken)
        assertEquals("new-refresh", f.tokens.refreshToken)
        assertEquals("", vm.state.value.currentPassword)
        assertEquals("", vm.state.value.newPassword)
        assertEquals("", vm.state.value.confirmPassword)
    }

    @Test fun externalProviderUsesSetPasswordAndDoesNotValidateOrSendCurrentPassword() = runTest(dispatcher) {
        val f = Fixture().apply { providers = listOf("GOOGLE") }
        val vm = f.vm()
        open(vm)
        assertFalse(vm.state.value.hasPassword)
        password(vm, "")
        vm.submit()
        assertTrue(vm.state.value.isSaving)
        val requestId = requireNotNull(vm.state.value.googleRequestId)
        assertEquals(0, f.calls("/auth/password/set"))
        vm.googleTokenReceived(requestId, "fresh-google-token")
        finished(vm)
        val body = f.payload(f.request("/auth/password/set"))
        assertEquals(setOf("password", "idToken"), body.keySet())
        assertEquals("fresh-google-token", body.get("idToken").asString)
        assertEquals("newpassword", body.get("password").asString)
        assertEquals(0, f.calls("/auth/password/change"))
        assertEquals("new-refresh", f.tokens.refreshToken)
        assertTrue(vm.state.value.hasPassword)
    }

    @Test fun failedPasswordSetupKeepsSecretsOnlyInMemoryAndDoesNotRestoreThem() = runTest(dispatcher) {
        val f = Fixture().apply { providers = listOf("GOOGLE"); failPath = "/auth/password/set" }
        val handle = SavedStateHandle()
        val vm = f.vm(handle)
        open(vm)
        password(vm, "")
        vm.submit()
        assertEquals("newpassword", vm.state.value.newPassword)
        assertEquals("newpassword", vm.state.value.confirmPassword)
        vm.googleTokenReceived(requireNotNull(vm.state.value.googleRequestId), "fresh-google-token")
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.PASSWORD, vm.state.value.page)
        assertEquals("newpassword", vm.state.value.newPassword)
        assertEquals("", vm.state.value.currentPassword)
        assertEquals("newpassword", f.payload(f.request("/auth/password/set")).get("password").asString)
        assertFalse(handle.keys().any { it.contains("password", ignoreCase = true) })
        val restored = f.vm(handle)
        open(restored)
        assertEquals("", restored.state.value.currentPassword)
        assertEquals("", restored.state.value.newPassword)
        assertEquals("", restored.state.value.confirmPassword)
    }

    @Test fun duplicateSubmitBackAndEditsAreBlockedDuringSave() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply { beforeMutation = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) } }
        val vm = f.vm()
        open(vm)
        password(vm)
        try {
            vm.submit()
            started.await()
            vm.submit()
            assertFalse(vm.back())
            vm.update(AccountField.NEW_PASSWORD, "changedvalue")
            vm.edit(AccountPage.EMAIL)
            assertEquals(AccountPage.PASSWORD, vm.state.value.page)
            assertEquals("newpassword", vm.state.value.newPassword)
            assertEquals(1, f.calls("/auth/password/change"))
        } finally { release.countDown() }
        finished(vm)
    }

    @Test fun unauthorizedUsageRefreshesThenRetriesWithoutExposingErrorState() = runTest(dispatcher) {
        val f = Fixture().apply { unauthorizedPath = "/me/usage" }
        val usage = f.repository.accountUsage()
        assertEquals(12, usage.aiEvaluations!!.remaining)
        assertEquals(2, f.calls("/me/usage"))
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals("Bearer refreshed", f.request("/me/usage").header("Authorization"))
    }

    @Test fun meFailureAfterPasswordMutationDoesNotRepeatSuccessfulMutation() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        f.failPath = "/me"
        password(vm)
        vm.submit()
        finished(vm)
        assertEquals(1, f.calls("/auth/password/change"))
        assertEquals("new-access", f.tokens.accessToken)
        assertFalse(vm.state.value.sessionExpired)
        assertEquals(AccountPage.OVERVIEW, vm.state.value.page)
    }

    @Test fun expiredCredentialsRefreshBeforePasswordMutationAndFinalTokensWin() = runTest(dispatcher) {
        val f = Fixture().apply { unauthorizedPath = "/auth/password/change" }
        val vm = f.vm()
        open(vm)
        password(vm)
        vm.submit()
        finished(vm)
        assertEquals(2, f.calls("/auth/password/change"))
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals("Bearer refreshed", f.request("/auth/password/change").header("Authorization"))
        assertEquals("new-access", f.tokens.accessToken)
        assertEquals("new-refresh", f.tokens.refreshToken)
        assertEquals(1L, f.tokens.sessionVersion)
    }

    @Test fun incorrectCurrentPasswordIsAnInlineErrorAndDoesNotExpireOrRefreshSession() = runTest(dispatcher) {
        val f = Fixture().apply { rejectCurrentPassword = true }
        val vm = f.vm()
        open(vm)
        val snapshot = f.tokens.snapshot()
        password(vm, current = "wrongpassword")
        vm.submit()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.PASSWORD, vm.state.value.page)
        assertEquals("Current password is incorrect.", vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertFalse(vm.state.value.sessionExpired)
        assertFalse(vm.state.value.changed)
        assertEquals(snapshot, f.tokens.snapshot())
        assertEquals(0, f.calls("/auth/refresh"))
        assertEquals(1, f.calls("/auth/password/change"))
        assertEquals(1, f.calls("/me"))
        assertEquals("wrongpassword", vm.state.value.currentPassword)
        assertEquals("newpassword", vm.state.value.newPassword)
        assertEquals("newpassword", vm.state.value.confirmPassword)
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.update(AccountField.NEW_PASSWORD, "newpassword")
        vm.update(AccountField.CONFIRM_PASSWORD, "newpassword")
        assertNull(vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        f.rejectCurrentPassword = false
        vm.submit()
        finished(vm)
    }

    @Test fun expiredTokenThenIncorrectPasswordKeepsTheRefreshedSessionAndShowsFieldError() = runTest(dispatcher) {
        val f = Fixture().apply {
            unauthorizedPath = "/auth/password/change"
            rejectCurrentPassword = true
        }
        val vm = f.vm()
        open(vm)
        password(vm, current = "wrongpassword")
        vm.submit()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.PASSWORD, vm.state.value.page)
        assertEquals("Current password is incorrect.", vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertFalse(vm.state.value.sessionExpired)
        assertEquals("refreshed", f.tokens.accessToken)
        assertEquals("refreshed-refresh", f.tokens.refreshToken)
        assertEquals(1L, f.tokens.sessionVersion)
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(2, f.calls("/auth/password/change"))
        assertEquals(1, f.calls("/me"))
    }

    @Test fun wrongPasswordWithBadRequestStatusIsAlsoAnInlineError() = runTest(dispatcher) {
        val f = Fixture().apply {
            rejectCurrentPassword = true
            passwordErrorStatus = 400
        }
        val vm = f.vm()
        open(vm)
        val snapshot = f.tokens.snapshot()
        password(vm, current = "wrongpassword")
        vm.submit()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.PASSWORD, vm.state.value.page)
        assertEquals("Current password is incorrect.", vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertFalse(vm.state.value.sessionExpired)
        assertEquals(snapshot, f.tokens.snapshot())
        assertEquals(0, f.calls("/auth/refresh"))
        assertEquals(1, f.calls("/auth/password/change"))
        assertEquals(1, f.calls("/me"))
    }

    @Test fun anotherUnauthorizedCodeIsNotMistakenForWrongPassword() = runTest(dispatcher) {
        val f = Fixture().apply {
            rejectCurrentPassword = true
            passwordErrorCode = "ACCOUNT_DISABLED"
        }
        val vm = f.vm()
        open(vm)
        password(vm)
        vm.submit()
        vm.state.first { !it.isSaving }
        assertTrue(vm.state.value.sessionExpired)
        assertNull(vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertNull(f.tokens.accessToken)
        assertNull(f.tokens.refreshToken)
        assertEquals(0, f.calls("/auth/refresh"))
        assertEquals(1, f.calls("/auth/password/change"))
        assertEquals(1, f.calls("/me"))
    }

    @Test fun repeatedUnauthorizedAfterRefreshExpiresSessionWithoutAProfileProbe() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        f.rejectCurrentPassword = true
        f.passwordErrorCode = "UNAUTHORIZED"
        password(vm)
        vm.submit()
        vm.state.first { !it.isSaving }
        assertTrue(vm.state.value.sessionExpired)
        assertNull(vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertNull(f.tokens.accessToken)
        assertNull(f.tokens.refreshToken)
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(2, f.calls("/auth/password/change"))
        assertEquals(1, f.calls("/me"))
    }

    @Test fun invalidRefreshDuringPasswordChangeStillExpiresTheSession() = runTest(dispatcher) {
        val f = Fixture().apply {
            unauthorizedPath = "/auth/password/change"
            rejectRefresh = true
        }
        val vm = f.vm()
        open(vm)
        password(vm)
        vm.submit()
        vm.state.first { !it.isSaving }
        assertTrue(vm.state.value.sessionExpired)
        assertNull(vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertNull(f.tokens.accessToken)
        assertNull(f.tokens.refreshToken)
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(1, f.calls("/auth/password/change"))
    }

    @Test fun failedPasswordMutationKeepsSecretsAndDoesNotReplaceTokens() = runTest(dispatcher) {
        val f = Fixture().apply { failPath = "/auth/password/change" }
        val vm = f.vm()
        open(vm)
        password(vm)
        vm.submit()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.PASSWORD, vm.state.value.page)
        assertEquals("currentpass", vm.state.value.currentPassword)
        assertEquals("newpassword", vm.state.value.newPassword)
        assertEquals("newpassword", vm.state.value.confirmPassword)
        assertFalse(vm.state.value.changed)
        assertEquals("access", f.tokens.accessToken)
        assertEquals("refresh", f.tokens.refreshToken)
    }

    @Test fun lateCredentialsResponseCannotOverwriteAnotherSession() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply { beforeMutation = {
            started.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
        } }
        var cancelled = false
        val job = launch {
            try { f.repository.setPassword("newpassword", "fresh-google-token") }
            catch (_: CancellationException) { cancelled = true }
        }
        try {
            started.await()
            f.repository.clearSession()
            f.tokens.saveSession("other-access", "other-refresh")
        } finally { release.countDown() }
        job.join()
        assertTrue(cancelled)
        assertEquals("other-access", f.tokens.accessToken)
        assertEquals("other-refresh", f.tokens.refreshToken)
    }

    @Test fun googleEmailRequestAndEachResendRequireTheirOwnCredentialAndKeepSession() = runTest(dispatcher) {
        val f = Fixture().apply { providers = listOf("GOOGLE") }
        val handle = SavedStateHandle()
        val vm = f.vm(handle)
        open(vm)
        val session = f.tokens.snapshot()
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.submit()
        val firstId = requireNotNull(vm.state.value.googleRequestId)
        assertTrue(vm.state.value.isSaving)
        assertFalse(vm.back())
        vm.submit()
        assertEquals(firstId, vm.state.value.googleRequestId)
        assertEquals(0, f.calls("/auth/email/change/request"))
        vm.googleTokenReceived(firstId, "fresh-request-token")
        vm.state.first { it.page == AccountPage.CODE && !it.isSaving }
        val request = f.request("/auth/email/change/request")
        assertEquals("POST", request.method)
        assertEquals("Bearer access", request.header("Authorization"))
        val body = f.payload(request)
        assertEquals(setOf("newEmail", "idToken"), body.keySet())
        assertEquals("new@example.com", body.get("newEmail").asString)
        assertEquals("fresh-request-token", body.get("idToken").asString)
        assertEquals(session, f.tokens.snapshot())
        vm.update(AccountField.CODE, "123456")
        vm.resend()
        val resendId = requireNotNull(vm.state.value.googleRequestId)
        assertNotEquals(firstId, resendId)
        assertTrue(vm.state.value.isResending)
        vm.googleTokenReceived(firstId, "stale-request-token")
        assertEquals(resendId, vm.state.value.googleRequestId)
        assertEquals(0, f.calls("/auth/email/change/resend"))
        vm.googleTokenReceived(resendId, "fresh-resend-token")
        vm.state.first { !it.isSaving }
        val resend = f.payload(f.request("/auth/email/change/resend"))
        assertEquals(setOf("newEmail", "idToken"), resend.keySet())
        assertEquals("fresh-resend-token", resend.get("idToken").asString)
        assertEquals("", vm.state.value.code)
        assertEquals(session, f.tokens.snapshot())
        assertEquals(0, f.calls("/auth/google"))
        assertEquals(setOf("accountPage", "pendingNewEmail"), handle.keys())
        vm.resend()
        val nextId = requireNotNull(vm.state.value.googleRequestId)
        assertNotEquals(resendId, nextId)
        vm.googleReauthenticationCancelled(nextId)
        assertFalse(vm.state.value.isSaving)
        assertEquals(1, f.calls("/auth/email/change/resend"))
    }

    @Test fun cancelledOrFailedGoogleSetupDoesNotCallApiAndLateResultsAreIgnored() = runTest(dispatcher) {
        val f = Fixture().apply { providers = listOf("GOOGLE") }
        val handle = SavedStateHandle()
        val vm = f.vm(handle)
        open(vm)
        password(vm, "")
        vm.submit()
        val first = requireNotNull(vm.state.value.googleRequestId)
        assertEquals("newpassword", vm.state.value.newPassword)
        vm.googleReauthenticationCancelled(first)
        assertFalse(vm.state.value.isSaving)
        assertNull(vm.state.value.googleRequestId)
        assertEquals("newpassword", vm.state.value.confirmPassword)
        vm.googleTokenReceived(first, "late-token")
        assertEquals(0, f.calls("/auth/password/set"))
        password(vm, "")
        vm.submit()
        val second = requireNotNull(vm.state.value.googleRequestId)
        vm.googleReauthenticationCancelled(first)
        assertEquals(second, vm.state.value.googleRequestId)
        vm.googleReauthenticationFailed(second, "Google unavailable")
        assertFalse(vm.state.value.isSaving)
        assertEquals("Google unavailable", vm.state.value.googleError)
        vm.googleTokenReceived(second, "late-token")
        assertEquals(0, f.calls("/auth/password/set"))
        assertFalse(handle.keys().any { it.contains("password", true) || it.contains("token", true) })
    }

    @Test fun emailCurrentPasswordErrorsNeverRefreshOrExpireTheSessionAndResendAsksAgain() = runTest(dispatcher) {
        val f = Fixture().apply { rejectCurrentPassword = true }
        val vm = f.vm()
        open(vm)
        val session = f.tokens.snapshot()
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.submit()
        assertNotNull(vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertEquals(0, f.calls("/auth/email/change/request"))
        vm.update(AccountField.CURRENT_PASSWORD, "wrongpassword")
        vm.submit()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.EMAIL, vm.state.value.page)
        assertEquals("Current password is incorrect.", vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertEquals("wrongpassword", vm.state.value.currentPassword)
        assertFalse(vm.state.value.sessionExpired)
        assertEquals(session, f.tokens.snapshot())
        assertEquals(0, f.calls("/auth/refresh"))
        f.rejectCurrentPassword = false
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        val body = f.payload(f.request("/auth/email/change/request"))
        assertEquals(setOf("newEmail", "currentPassword"), body.keySet())
        assertEquals("currentpass", body.get("currentPassword").asString)
        vm.resend()
        assertTrue(vm.state.value.showResendPasswordDialog)
        assertEquals("", vm.state.value.currentPassword)
        assertEquals(0, f.calls("/auth/email/change/resend"))
        f.rejectCurrentPassword = true
        vm.update(AccountField.CURRENT_PASSWORD, "wrongpassword")
        vm.resend()
        vm.state.first { !it.isSaving }
        assertTrue(vm.state.value.showResendPasswordDialog)
        assertEquals("wrongpassword", vm.state.value.currentPassword)
        assertEquals("Current password is incorrect.", vm.state.value.errors[AccountField.CURRENT_PASSWORD])
        assertNull(vm.state.value.errors[AccountField.CODE])
        assertEquals(session, f.tokens.snapshot())
        assertEquals(0, f.calls("/auth/refresh"))
        vm.dismissResendPassword()
        assertFalse(vm.state.value.showResendPasswordDialog)
        assertEquals("", vm.state.value.currentPassword)
    }

    @Test fun authenticatedEmailEndpointRefreshesExpiredBearerAndRetriesWithOriginalProof() = runTest(dispatcher) {
        val f = Fixture().apply { unauthorizedPath = "/auth/email/change/request" }
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        assertEquals(2, f.calls("/auth/email/change/request"))
        assertEquals(1, f.calls("/auth/refresh"))
        val request = f.request("/auth/email/change/request")
        assertEquals("Bearer refreshed", request.header("Authorization"))
        assertEquals("currentpass", f.payload(request).get("currentPassword").asString)
        assertEquals("", vm.state.value.currentPassword)
    }

    @Test fun pendingEmailVerificationRestoresWithoutPersistingPasswordsOrTokens() = runTest(dispatcher) {
        val f = Fixture()
        val handle = SavedStateHandle()
        val vm = f.vm(handle)
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.update(AccountField.CURRENT_PASSWORD, "currentpass")
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        val restored = f.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        open(restored)
        assertEquals(AccountPage.CODE, restored.state.value.page)
        assertEquals("new@example.com", restored.state.value.email)
        assertEquals(setOf("accountPage", "pendingNewEmail"), handle.keys())
        assertFalse(restored.back())
    }
}
