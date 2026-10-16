package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.oniolla.interviewer.domain.ManageAccountUseCase
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
        var failPath: String? = null
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
                        path == "/me/password/change" && rejectCurrentPassword &&
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
                            status = 400
                            """{"error":{"code":"INVALID","message":"Server validation failed"}}"""
                        }
                        path == "/me" -> profile()
                        path == "/me/usage" -> """{"plan":"FREE","answerReveals":{"used":12,"limit":20,"remaining":8},
                            "aiEvaluations":{"used":8,"reserved":0,"limit":20,"remaining":12,"periodEndsAt":null,"resetAt":null},
                            "questions":{"unlimited":true,"limitPerTopic":null}}"""
                        path == "/me/name" -> { name = payload(request).get("name").asString; profile() }
                        path == "/me/email/change/request" || path == "/me/email/change/resend" -> { status = 204; "" }
                        path == "/me/email/change/confirm" -> {
                            email = payload(request).get("newEmail").asString
                            credentials()
                        }
                        path == "/me/password/change" -> credentials()
                        path == "/me/password/set" -> { providers = providers + "PASSWORD"; credentials() }
                        path == "/auth/refresh" -> """{"accessToken":"refreshed","refreshToken":"refreshed-refresh","expiresIn":900}"""
                        else -> error("Unexpected request $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(status).message("Test").body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            repository = AuthRepository(tokens, api, SessionRefreshCoordinator(tokens, api))
            manage = ManageAccountUseCase(repository)
        }
        private fun profile() = """{"id":"user","email":"$email","displayName":"$name","emailVerified":true,
            "specialization":"ANDROID","createdAt":"","updatedAt":"","onboardingCompleted":true,
            "linkedProviders":${Gson().toJson(providers)},"preparationLanguage":"en"}"""
        private fun credentials() = """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
        fun payload(request: Request): JsonObject =
            Gson().fromJson(Buffer().also { request.body!!.writeTo(it) }.readUtf8(), JsonObject::class.java)
        fun calls(path: String) = requests.count { it.url.encodedPath == path }
        fun request(path: String) = requests.last { it.url.encodedPath == path }
        fun vm(handle: SavedStateHandle = SavedStateHandle()) =
            AccountViewModel(manage, handle).also { stores += ViewModelStore().apply { put("account", it) } }
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
        val f = Fixture().apply { failPath = "/me/email/change/request" }
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "invalid")
        vm.submit()
        assertNotNull(vm.state.value.errors[AccountField.EMAIL])
        assertEquals(0, f.calls("/me/email/change/request"))
        vm.update(AccountField.EMAIL, "new@example.com")
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
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        assertFalse(vm.back())
        assertEquals(AccountPage.CODE, vm.state.value.page)
        assertEquals("new@example.com", vm.state.value.email)
        assertEquals("POST", f.request("/me/email/change/request").method)
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
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        vm.update(AccountField.CODE, "123456")
        vm.resend()
        assertTrue(vm.state.value.isResending)
        vm.submit()
        vm.changeEmailAddress()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.CODE, vm.state.value.page)
        assertEquals("", vm.state.value.code)
        assertEquals("new@example.com", f.payload(f.request("/me/email/change/resend")).get("newEmail").asString)
        assertEquals(0, f.calls("/me/email/change/confirm"))
    }

    @Test fun codeValidationClipboardAndConfirmRotateBothTokensAndRefreshMe() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
        vm.submit()
        vm.state.first { it.page == AccountPage.CODE }
        vm.pasteCode("not a code")
        vm.submit()
        assertNotNull(vm.state.value.errors[AccountField.CODE])
        assertEquals(0, f.calls("/me/email/change/confirm"))
        vm.pasteCode(" 615806 ")
        assertEquals("615806", vm.state.value.code)
        vm.submit()
        finished(vm)
        assertEquals("new-access", f.tokens.accessToken)
        assertEquals("new-refresh", f.tokens.refreshToken)
        assertEquals(1L, f.tokens.sessionVersion)
        assertEquals("new@example.com", (vm.state.value.profile as ContentViewState.Ready).content.email)
        val payload = f.payload(f.request("/me/email/change/confirm"))
        assertEquals("615806", payload.get("code").asString)
        assertEquals("new@example.com", payload.get("newEmail").asString)
        assertEquals("Bearer new-access", f.request("/me").header("Authorization"))
    }

    @Test fun failedConfirmationKeepsCodePageAndCredentials() = runTest(dispatcher) {
        val f = Fixture().apply { failPath = "/me/email/change/confirm" }
        val vm = f.vm()
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
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
        assertEquals("", vm.state.value.currentPassword)
        assertEquals("", vm.state.value.newPassword)
        assertEquals("", vm.state.value.confirmPassword)
        assertEquals(0, f.calls("/me/password/change"))
    }

    @Test fun passwordChangeUsesCurrentAndNewAndClearsSecretsAfterSuccess() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        open(vm)
        password(vm)
        vm.submit()
        finished(vm)
        val body = f.payload(f.request("/me/password/change"))
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
        finished(vm)
        val body = f.payload(f.request("/me/password/set"))
        assertEquals(setOf("password"), body.keySet())
        assertEquals("newpassword", body.get("password").asString)
        assertEquals(0, f.calls("/me/password/change"))
        assertEquals("new-refresh", f.tokens.refreshToken)
        assertTrue(vm.state.value.hasPassword)
    }

    @Test fun failedPasswordSetupClearsSecretsAndDoesNotRestoreThem() = runTest(dispatcher) {
        val f = Fixture().apply { providers = listOf("GOOGLE"); failPath = "/me/password/set" }
        val handle = SavedStateHandle()
        val vm = f.vm(handle)
        open(vm)
        password(vm, "")
        vm.submit()
        assertEquals("", vm.state.value.newPassword)
        assertEquals("", vm.state.value.confirmPassword)
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.PASSWORD, vm.state.value.page)
        assertEquals("", vm.state.value.newPassword)
        assertEquals("", vm.state.value.currentPassword)
        assertEquals("newpassword", f.payload(f.request("/me/password/set")).get("password").asString)
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
            assertEquals("", vm.state.value.newPassword)
            assertEquals(1, f.calls("/me/password/change"))
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
        assertEquals(1, f.calls("/me/password/change"))
        assertEquals("new-access", f.tokens.accessToken)
        assertFalse(vm.state.value.sessionExpired)
        assertEquals(AccountPage.OVERVIEW, vm.state.value.page)
    }

    @Test fun expiredCredentialsRefreshBeforePasswordMutationAndFinalTokensWin() = runTest(dispatcher) {
        val f = Fixture().apply { unauthorizedPath = "/me/password/change" }
        val vm = f.vm()
        open(vm)
        password(vm)
        vm.submit()
        finished(vm)
        assertEquals(2, f.calls("/me/password/change"))
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals("Bearer refreshed", f.request("/me/password/change").header("Authorization"))
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
        assertEquals(1, f.calls("/me/password/change"))
        assertEquals(1, f.calls("/me"))
        assertEquals("", vm.state.value.currentPassword)
        assertEquals("", vm.state.value.newPassword)
        assertEquals("", vm.state.value.confirmPassword)
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
            unauthorizedPath = "/me/password/change"
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
        assertEquals(2, f.calls("/me/password/change"))
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
        assertEquals(1, f.calls("/me/password/change"))
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
        assertEquals(1, f.calls("/me/password/change"))
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
        assertEquals(2, f.calls("/me/password/change"))
        assertEquals(1, f.calls("/me"))
    }

    @Test fun invalidRefreshDuringPasswordChangeStillExpiresTheSession() = runTest(dispatcher) {
        val f = Fixture().apply {
            unauthorizedPath = "/me/password/change"
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
        assertEquals(1, f.calls("/me/password/change"))
    }

    @Test fun failedPasswordMutationClearsSecretsAndDoesNotReplaceTokens() = runTest(dispatcher) {
        val f = Fixture().apply { failPath = "/me/password/change" }
        val vm = f.vm()
        open(vm)
        password(vm)
        vm.submit()
        vm.state.first { !it.isSaving }
        assertEquals(AccountPage.PASSWORD, vm.state.value.page)
        assertEquals("", vm.state.value.currentPassword)
        assertEquals("", vm.state.value.newPassword)
        assertEquals("", vm.state.value.confirmPassword)
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
            try { f.repository.setPassword("newpassword") }
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

    @Test fun pendingEmailVerificationRestoresWithoutPersistingPasswordsOrTokens() = runTest(dispatcher) {
        val f = Fixture()
        val handle = SavedStateHandle()
        val vm = f.vm(handle)
        open(vm)
        vm.edit(AccountPage.EMAIL)
        vm.update(AccountField.EMAIL, "new@example.com")
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
