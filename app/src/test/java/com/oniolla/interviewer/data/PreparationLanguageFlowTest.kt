package com.oniolla.interviewer.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.AuthViewModel
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import com.oniolla.interviewer.ui.toUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class PreparationLanguageFlowTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class Tokens : SessionTokenStorage {
        override var sessionVersion: Long = 0
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
    }

    private class Fixture {
        val tokens = Tokens()
        val requests = CopyOnWriteArrayList<Request>()
        val bodies = CopyOnWriteArrayList<Pair<String, String>>()
        var specialization: String? = "ANDROID"
        var language: String? = null
        var onboardingCompleted = false
        var emailVerified = true
        var finishOnSpecialization = false
        var finishOnLanguage = true
        var ignoreLanguageSave = false
        var languagesStatus = 200
        var languageSaveStatus = 200
        var languagesBody: String? = null
        var beforeLanguages: (() -> Unit)? = null
        var beforeLanguageSave: (() -> Unit)? = null
        val repository: AuthRepository
        val resend: ResendVerificationCodeUseCase
        val resolve: ResolveAuthStateUseCase

        init {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests.add(request)
                val path = request.url.encodedPath
                val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() }.orEmpty()
                bodies.add(path to body)
                val requestedSpecialization = specialization
                val status = when (path) {
                    "/preparation-languages" -> languagesStatus
                    "/me/preparation-language" -> languageSaveStatus
                    else -> 200
                }
                if (path == "/preparation-languages") beforeLanguages?.invoke()
                if (path == "/auth/email/verify") emailVerified = true
                if (path == "/me/specialization" && status == 200) {
                    specialization = JsonParser.parseString(body).asJsonObject["specialization"].asString
                    onboardingCompleted = finishOnSpecialization
                }
                if (path == "/me/preparation-language" && status == 200) {
                    val payload = JsonParser.parseString(body).asJsonObject
                    assertEquals(setOf("language"), payload.keySet())
                    beforeLanguageSave?.invoke()
                    if (!ignoreLanguageSave) language = payload["language"].asString
                    onboardingCompleted = finishOnLanguage
                }
                val responseBody = if (status >= 400) {
                    """{"error":{"code":"FAILED","message":"Request failed"}}"""
                } else when (path) {
                    "/preparation-languages" -> languagesBody ?: if (requestedSpecialization == "IOS") {
                        """{"items":[{"code":"de","name":"German","nativeName":"Deutsch","contentAvailable":true}]}"""
                    } else {
                        """{"items":[
                            {"code":"uk","name":"Ukrainian","nativeName":"Українська","contentAvailable":true},
                            {"code":"ru","name":"Russian","nativeName":"Русский","contentAvailable":false},
                            {"code":"en","name":"English","nativeName":"English","contentAvailable":true}]}"""
                    }
                    "/auth/refresh", "/auth/login", "/auth/email/verify" ->
                        """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
                    "/auth/email/resend-code" -> """{"message":"Code sent"}"""
                    "/me", "/me/specialization", "/me/preparation-language" -> profile()
                    else -> error("Unexpected endpoint $path")
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(status).message("Test response")
                    .body(responseBody.toResponseBody("application/json".toMediaType())).build()
            }.build()
            val api = Retrofit.Builder().baseUrl("https://api.example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create()).build().create(Api::class.java)
            repository = AuthRepository(tokens, api, SessionRefreshCoordinator(tokens, api))
            resend = ResendVerificationCodeUseCase(repository)
            resolve = ResolveAuthStateUseCase(repository, resend)
        }

        private fun profile() = """{"id":"user","email":"developer@example.com","displayName":"Developer",
            "emailVerified":$emailVerified,"specialization":${Gson().toJson(specialization)},
            "preparationLanguage":${Gson().toJson(language)},"onboardingCompleted":$onboardingCompleted,
            "createdAt":"","updatedAt":"","linkedProviders":["PASSWORD"]}"""

        fun viewModel() = AuthViewModel(
            RestoreSessionUseCase(repository, resolve), LoginUseCase(repository, resend, resolve),
            SignupUseCase(repository), ForgotPasswordUseCase(repository), VerifyEmailUseCase(repository, resolve),
            resend, ClearSessionUseCase(repository), SelectSpecializationUseCase(repository, resolve),
            GetPreparationLanguagesUseCase(repository), SelectPreparationLanguageUseCase(repository, resolve),
            GoogleSignInUseCase(repository, resolve), ResetPasswordUseCase(repository)
        ).also { it.start() }

        fun paths() = requests.map { it.url.encodedPath }
        fun savedLanguages() = bodies.filter { it.first == "/me/preparation-language" }
            .map { JsonParser.parseString(it.second).asJsonObject["language"].asString }
    }

    private fun state(vm: AuthViewModel) = vm.stateFlow.value as AuthViewState.PreparationLanguageSelection

    private suspend fun awaitOptions(vm: AuthViewModel) {
        vm.stateFlow.first {
            val current = it as? AuthViewState.PreparationLanguageSelection
            current != null && !current.isLoadingLanguages
        }
    }

    @Test fun availableLanguagesUseParameterlessGetAndExcludeUnavailableContent() = runBlocking {
        val fixture = Fixture()
        val languages = (GetPreparationLanguagesUseCase(fixture.repository)(Unit) as ContentResult.Success).content
        assertEquals(listOf("uk", "en"), languages.map { it.code })
        assertEquals("Українська", languages.first().nativeName)
        val request = fixture.requests.single()
        assertEquals("/preparation-languages", request.url.encodedPath)
        assertEquals("GET", request.method)
        assertNull(request.url.query)
        assertNull(request.body)
    }

    @Test fun startupRestoresTheSpecializationAndWaitsForAUserLanguageChoice() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        awaitOptions(vm)
        assertEquals(Specialization.ANDROID, state(vm).specialization)
        assertEquals("en", state(vm).selectedLanguage)
        assertEquals(listOf("uk", "en"), state(vm).languages.map { it.code })
        assertEquals(listOf("/auth/refresh", "/me", "/preparation-languages"), fixture.paths())
        assertTrue(fixture.savedLanguages().isEmpty())
        assertNull(fixture.language)
    }

    @Test fun startupWithAnExistingSpecializationStillAllowsGoingBackAndChangingIt() = runTest(dispatcher) {
        val fixture = Fixture().apply { specialization = "IOS" }
        val vm = fixture.viewModel()
        awaitOptions(vm)
        assertEquals("de", state(vm).selectedLanguage)
        vm.backToSpecialization()
        val previous = vm.stateFlow.value as AuthViewState.SpecializationSelection
        assertEquals(Specialization.IOS, previous.selectedSpecialization)
        vm.selectSpecialization(Specialization.ANDROID)
        vm.continueWithSpecialization()
        awaitOptions(vm)
        assertEquals(listOf("uk", "en"), state(vm).languages.map { it.code })
        assertEquals(Specialization.ANDROID, state(vm).specialization)
        assertEquals(2, fixture.paths().count { it == "/preparation-languages" })
        assertTrue(fixture.savedLanguages().isEmpty())
    }

    @Test fun changingSpecializationReloadsItsAvailableLanguages() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        awaitOptions(vm)
        vm.backToSpecialization()
        vm.selectSpecialization(Specialization.IOS)
        vm.continueWithSpecialization()
        awaitOptions(vm)
        assertEquals(listOf("de"), state(vm).languages.map { it.code })
        assertEquals("de", state(vm).selectedLanguage)
        assertEquals(Specialization.IOS, state(vm).specialization)
        assertTrue(fixture.savedLanguages().isEmpty())
    }

    @Test fun specializationSaveAlwaysShowsLanguageChoiceEvenIfItsResponseSaysCompleted() = runTest(dispatcher) {
        val fixture = Fixture().apply { specialization = null; language = "en"; finishOnSpecialization = true }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it is AuthViewState.SpecializationSelection }
        vm.continueWithSpecialization()
        awaitOptions(vm)
        assertTrue(fixture.onboardingCompleted)
        assertTrue(vm.stateFlow.value is AuthViewState.PreparationLanguageSelection)
        assertTrue(fixture.savedLanguages().isEmpty())
    }

    @Test fun currentLanguageIsPreferredWhenItIsAvailableForTheChangedSpecialization() = runTest(dispatcher) {
        val fixture = Fixture().apply { specialization = null; language = "uk" }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it is AuthViewState.SpecializationSelection }
        vm.continueWithSpecialization()
        awaitOptions(vm)
        assertEquals("uk", state(vm).selectedLanguage)
        assertTrue(fixture.savedLanguages().isEmpty())
    }

    @Test fun selectingALanguageDoesNotSendAPutUntilContinueIsPressed() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        awaitOptions(vm)
        vm.selectPreparationLanguage("uk")
        assertEquals("uk", state(vm).selectedLanguage)
        assertTrue(fixture.savedLanguages().isEmpty())
        vm.continueWithPreparationLanguage()
        vm.stateFlow.first { it is AuthViewState.Authorized }
        assertEquals(listOf("uk"), fixture.savedLanguages())
        val request = fixture.requests.last()
        assertEquals("PUT", request.method)
        assertNull(request.url.query)
        assertEquals("uk", fixture.language)
    }

    @Test fun unavailableLanguagesCannotBeSelectedOrSent() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        awaitOptions(vm)
        vm.selectPreparationLanguage("ru")
        assertEquals("en", state(vm).selectedLanguage)
        assertFalse(state(vm).languages.any { it.code == "ru" })
        vm.continueWithPreparationLanguage()
        vm.stateFlow.first { it is AuthViewState.Authorized }
        assertEquals(listOf("en"), fixture.savedLanguages())
    }

    @Test fun emptyAvailableListHasNoSelectionAndCannotSubmitAFallbackLanguage() = runTest(dispatcher) {
        val fixture = Fixture().apply {
            languagesBody = """{"items":[{"code":"en","name":"English","nativeName":"English","contentAvailable":false}]}"""
        }
        val vm = fixture.viewModel()
        awaitOptions(vm)
        assertTrue(state(vm).languages.isEmpty())
        assertNull(state(vm).selectedLanguage)
        vm.selectPreparationLanguage("en")
        vm.continueWithPreparationLanguage()
        assertTrue(fixture.savedLanguages().isEmpty())
        vm.backToSpecialization()
        assertTrue(vm.stateFlow.value is AuthViewState.SpecializationSelection)
    }

    @Test fun listLoadingErrorCanBeRetriedWithoutSendingALanguage() = runTest(dispatcher) {
        val fixture = Fixture().apply { languagesStatus = 500 }
        val vm = fixture.viewModel()
        awaitOptions(vm)
        assertEquals("Request failed", state(vm).languagesError)
        vm.continueWithPreparationLanguage()
        assertTrue(fixture.savedLanguages().isEmpty())
        fixture.languagesStatus = 200
        vm.loadPreparationLanguages()
        awaitOptions(vm)
        assertNull(state(vm).languagesError)
        assertEquals("en", state(vm).selectedLanguage)
        assertEquals(2, fixture.paths().count { it == "/preparation-languages" })
    }

    @Test fun failedLanguageSaveKeepsTheSelectionAndCanBeRetried() = runTest(dispatcher) {
        val fixture = Fixture().apply { languageSaveStatus = 500 }
        val vm = fixture.viewModel()
        awaitOptions(vm)
        vm.selectPreparationLanguage("uk")
        vm.continueWithPreparationLanguage()
        vm.stateFlow.first { (it as? AuthViewState.PreparationLanguageSelection)?.error != null }
        assertFalse(state(vm).isSaving)
        assertEquals("uk", state(vm).selectedLanguage)
        assertEquals("Request failed", state(vm).error)
        fixture.languageSaveStatus = 200
        vm.continueWithPreparationLanguage()
        vm.stateFlow.first { it is AuthViewState.Authorized }
        assertEquals(listOf("uk", "uk"), fixture.savedLanguages())
        assertEquals(1, fixture.paths().count { it == "/preparation-languages" })
    }

    @Test fun saveLocksSelectionBackNavigationAndDuplicateSubmissions() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            beforeLanguageSave = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        }
        val vm = fixture.viewModel()
        awaitOptions(vm)
        vm.continueWithPreparationLanguage()
        try {
            started.await()
            assertTrue(state(vm).isSaving)
            vm.selectPreparationLanguage("uk")
            vm.backToSpecialization()
            vm.continueWithPreparationLanguage()
            vm.loadPreparationLanguages()
            assertTrue(state(vm).isSaving)
            assertEquals("en", state(vm).selectedLanguage)
            assertEquals(1, fixture.paths().count { it == "/me/preparation-language" })
        } finally { release.countDown() }
        vm.stateFlow.first { it is AuthViewState.Authorized }
    }

    @Test fun backDuringLanguageLoadingCancelsTheRequestAndKeepsSpecializationVisible() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            beforeLanguages = {
                started.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
                finished.complete(Unit)
            }
        }
        val vm = fixture.viewModel()
        try {
            started.await()
            assertTrue(state(vm).isLoadingLanguages)
            vm.backToSpecialization()
            assertTrue(vm.stateFlow.value is AuthViewState.SpecializationSelection)
        } finally { release.countDown() }
        finished.await()
        runCurrent()
        assertTrue(vm.stateFlow.value is AuthViewState.SpecializationSelection)
    }

    @Test fun missingLanguageAfterLoginCanLoadOptionsWithoutAutomaticallySaving() = runBlocking {
        val fixture = Fixture().apply { tokens.clear() }
        val result = LoginUseCase(fixture.repository, fixture.resend, fixture.resolve)(
            com.oniolla.interviewer.data.payload.LoginPayload("developer@example.com", "password123"))
        assertTrue(result.toUi() is AuthViewState.PreparationLanguageSelection)
        val options = GetPreparationLanguagesUseCase(fixture.repository)(Unit) as ContentResult.Success
        assertEquals(listOf("uk", "en"), options.content.map { it.code })
        assertEquals(listOf("/auth/login", "/me", "/preparation-languages"), fixture.paths())
        assertTrue(fixture.savedLanguages().isEmpty())
    }

    @Test fun confirmedEmailOpensLanguageSelectionAndLoadsItsOptionsInTheViewModel() = runTest(dispatcher) {
        val fixture = Fixture().apply { emailVerified = false }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it is AuthViewState.Unauthorized.EmailConfirmation }
        vm.updateEmailCode("123456")
        vm.passwordText.value = "password123"
        vm.confirmEmail()
        awaitOptions(vm)
        assertEquals(Specialization.ANDROID, state(vm).specialization)
        assertEquals(listOf("/auth/refresh", "/me", "/auth/email/resend-code",
            "/auth/email/verify", "/me", "/preparation-languages"), fixture.paths())
        assertTrue(fixture.savedLanguages().isEmpty())
    }

    @Test fun expiredSessionWhileLoadingOptionsReturnsToLogin() = runTest(dispatcher) {
        val fixture = Fixture().apply { languagesStatus = 401 }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it is AuthViewState.Unauthorized.Login }
        assertNull(fixture.tokens.refreshToken)
        assertNull(fixture.tokens.accessToken)
        assertTrue(fixture.savedLanguages().isEmpty())
    }

    @Test fun languageStateCarriesSpecializationThroughTheUiConversion() {
        val viewState = AuthState.PreparationLanguageSelectionRequired(Specialization.QA, "de").toUi()
            as AuthViewState.PreparationLanguageSelection
        assertEquals(Specialization.QA, viewState.specialization)
        assertEquals("de", viewState.selectedLanguage)
        assertTrue(viewState.isLoadingLanguages)
    }
}
