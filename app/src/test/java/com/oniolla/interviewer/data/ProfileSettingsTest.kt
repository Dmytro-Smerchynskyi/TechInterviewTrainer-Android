package com.oniolla.interviewer.data

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.oniolla.interviewer.data.local.*
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.data.payload.SignupPayload
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.home.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileSettingsTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val databases = mutableListOf<TopicDatabase>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        stores.forEach { it.clear() }
        databases.forEach { it.close() }
        Dispatchers.resetMain()
    }

    private class Tokens : SessionTokenStorage {
        override var sessionVersion = 0L
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
    }

    private inner class Fixture {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("profile-test-${System.nanoTime()}", Context.MODE_PRIVATE)
        val preferences = AppPreferences(prefs)
        val tokens = Tokens()
        val cache = SessionStatsCache()
        val database = Room.inMemoryDatabaseBuilder(context, TopicDatabase::class.java).allowMainThreadQueries().build()
            .also { databases += it }
        val requests = CopyOnWriteArrayList<Request>()
        var specialization = "ANDROID"
        var language = "en"
        var streak = 7
        var updateStatus = 200
        var usageStatus = 200
        var deleteStatus = 204
        var expireDeleteAccess = false
        var beforeDelete: (() -> Unit)? = null
        var logoutStatus = 204
        var logoutOffline = false
        var beforeLogout: (() -> Unit)? = null
        var reveals = 8
        var beforeUsage: (() -> Unit)? = null
        var beforeDashboard: (() -> Unit)? = null
        var beforeUpdate: (() -> Unit)? = null
        var beforeStats: (() -> Unit)? = null
        val auth: AuthRepository
        val home: HomeRepository
        val manage: ManageProfileUseCase

        init {
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, "https://api.example.test/"))
                .addInterceptor { chain ->
                val request = chain.request()
                requests.add(request)
                val path = request.url.encodedPath
                if (path == "/auth/logout") {
                    beforeLogout?.invoke()
                    if (logoutOffline) throw java.io.IOException("Offline")
                }
                val deleting = path == "/me" && request.method == "DELETE"
                if (deleting) beforeDelete?.invoke()
                val code = when {
                    deleting && expireDeleteAccess && request.header("Authorization") == "Bearer access" -> 401
                    deleting -> deleteStatus
                    path == "/auth/logout" -> logoutStatus
                    path == "/me/subscription" -> usageStatus
                    request.method == "PUT" -> updateStatus
                    else -> 200
                }
                if (request.method == "PUT") beforeUpdate?.invoke()
                val body = if (code >= 400) """{"error":{"code":"${if (code == 401) "UNAUTHORIZED" else "FAILED"}","message":"Unable to update profile"}}"""
                    else if (deleting) "" else when (path) {
                        "/me/specialization" -> {
                            specialization = payload(request).get("specialization").asString
                            profileBody()
                        }
                        "/me/preparation-language" -> {
                            language = payload(request).get("language").asString
                            profileBody()
                        }
                        "/me" -> profileBody()
                        "/me/subscription" -> usageBody().also { beforeUsage?.invoke() }
                        "/questions/4/answer" -> {
                            reveals--
                            """{"questionId":4,"referenceAnswer":"Reference answer","language":"en","requestedLanguage":"en"}"""
                        }
                        "/preparation-languages" -> """{"items":[
                            {"code":"en","name":"English","nativeName":"English","contentAvailable":true},
                            {"code":"uk","name":"Ukrainian","nativeName":"Українська","contentAvailable":true},
                            {"code":"ja","name":"Japanese","nativeName":"日本語","contentAvailable":false}]}"""
                        "/topics" -> "[]"
                        "/questions", "/questions/saved" -> """{"items":[],"nextCursor":null,"topics":[],"access":null}"""
                        "/dashboard" -> """{"readiness":{"score":0,"answered":0,"practicedTopics":0,"totalTopics":0,
                            "gradePerformance":[]},"todayReview":[],"weakTopics":[],"dueCount":0,"usage":${usageBody()}}"""
                            .also { beforeDashboard?.invoke() }
                        "/stats" -> {
                            val body = statsBody()
                            beforeStats?.invoke()
                            body
                        }
                        "/auth/logout" -> ""
                        "/auth/refresh" -> """{"accessToken":"renewed","refreshToken":"renewed-refresh","expiresIn":900}"""
                        "/auth/register" -> """{"message":"Account created"}"""
                        else -> error("Unexpected path $path")
                    }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test response")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
            val api = Retrofit.Builder().baseUrl("https://api.example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build().create(Api::class.java)
            val coordinator = SessionRefreshCoordinator(tokens, api)
            val usageRepository = SessionUsageRepository(api, coordinator, tokens)
            auth = AuthRepository(tokens, api, coordinator, LocalUserData(database, cache, preferences, context), usageRepository)
            home = HomeRepository(api, RoomTopicCache(database.topicDao()), coordinator, cache, usageRepository)
            manage = ManageProfileUseCase(auth, cache, tokens, preferences)
        }

        private fun payload(request: Request): JsonObject =
            Gson().fromJson(Buffer().also { request.body!!.writeTo(it) }.readUtf8(), JsonObject::class.java)

        private fun profileBody() = """{"id":"user","email":"developer@example.com","displayName":"Dmitry S.",
            "emailVerified":true,"specialization":"$specialization","preparationLanguage":"$language",
            "createdAt":"","updatedAt":"","onboardingCompleted":true,"linkedProviders":["PASSWORD"]}"""

        private fun usageBody() = """{"plan":"FREE","answerReveals":{"used":${20 - reveals},"limit":20,"remaining":$reveals},
            "aiEvaluations":{"used":8,"reserved":0,"limit":20,"remaining":12,"periodEndsAt":null,"resetAt":null},
            "questions":{"unlimited":true,"limitPerTopic":null}}"""

        private fun statsBody() = """{"timeZone":"UTC","weekStart":"2026-10-12","weekEnd":"2026-10-18",
            "serverTime":"2026-10-14T12:00:00.000Z","readiness":{"score":0,"gradePerformance":[]},
            "summary":{"answered":12,"streakDays":$streak,"dueCount":0,"averageStudySeconds":null},
            "topics":[],"weakTopics":[],"weakKeyPoints":[],"studyProgress":[]}"""

        fun profileVm(): ProfileViewModel = ProfileViewModel(manage, GetPreparationLanguagesUseCase(auth), SavedStateHandle())
            .also { vm -> stores += ViewModelStore().apply { put("profile", vm) } }

        fun homeVm(): HomeViewModel = HomeViewModel(GetDashboardUseCase(home, auth),
            GetTopicsUseCase(home), GetQuestionsUseCase(home), SavedStateHandle(),
            TopicCatalogUseCase(home), SetQuestionSavedUseCase(home),
            GetFavoriteQuestionsUseCase(home), GetStatsUseCase(home), GetQuestionAnswerUseCase(home))
            .also { vm -> stores += ViewModelStore().apply { put("home", vm) } }

        fun calls(path: String) = requests.count { it.url.encodedPath == path }

        suspend fun seedData() {
            tokens.saveSession("access", "refresh")
            preferences.setTheme(ThemeMode.DARK)
            prefs.edit().putString("refresh_token", "old-user-secret").apply()
            database.topicDao().replaceIfChanged("seed", listOf(CachedTopic.fromResponse("seed",
                TopicResponse(1, "ANDROID", "kotlin", "Kotlin", "Description"))))
            home.getStats(null, null)
        }
    }

    private suspend fun readyHome(vm: HomeViewModel) {
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.statistics is ContentViewState.Ready &&
            it.topicCatalog is ContentViewState.Ready }
    }

    private suspend fun readyProfile(vm: ProfileViewModel) {
        vm.state.first { it.profile is ContentViewState.Ready }
    }

    @Test fun planUsageLoadsOnProfileAndRemainsCachedAcrossAccountAndReopening() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.profileVm()
        runCurrent()
        assertEquals(0, f.calls("/me/subscription"))
        vm.open()
        readyProfile(vm)
        val usage = (vm.state.first { it.usage is ContentViewState.Ready }.usage as ContentViewState.Ready).content
        assertEquals(12, usage.aiEvaluations!!.remaining)
        assertEquals(8, usage.answerReveals!!.remaining)
        assertTrue(usage.questions!!.unlimited)
        assertEquals(1, f.calls("/me/subscription"))
        val profile = (vm.state.value.profile as ContentViewState.Ready).content
        vm.openAccount()
        vm.finishAccount(profile, false)
        vm.back()
        vm.open()
        runCurrent()
        assertEquals(1, f.calls("/me/subscription"))
    }

    @Test fun profileUsesTheUsageAlreadyLoadedByDashboardWithoutAnExtraRequest() = runTest(dispatcher) {
        val f = Fixture()
        val home = f.homeVm()
        readyHome(home)
        val profile = f.profileVm()
        profile.open()
        readyProfile(profile)
        profile.state.first { it.usage is ContentViewState.Ready }
        assertEquals(8, (profile.state.value.usage as ContentViewState.Ready).content.answerReveals!!.remaining)
        assertEquals(0, f.calls("/me/subscription"))
    }

    @Test fun revealingAnAnswerUpdatesTheClosedProfileAndReopeningDoesNotRefetchUsage() = runTest(dispatcher) {
        val f = Fixture()
        val home = f.homeVm()
        readyHome(home)
        val profile = f.profileVm()
        profile.open()
        readyProfile(profile)
        profile.back()
        val question = QuestionResponse(4, "Question", "ANDROID", QuestionTopic(1, "kotlin", "Kotlin"),
            "MEDIUM", "JUNIOR", "SENIOR")
        home.openQuestion(question)
        home.stateFlow.first { it.questionDetail?.isUsageLoading == false }
        home.revealQuestionAnswer()
        home.stateFlow.first { it.questionDetail?.let { detail ->
            detail.answer is ContentViewState.Ready && !detail.isUsageLoading
        } == true }
        profile.state.first { (it.usage as? ContentViewState.Ready)?.content?.answerReveals?.remaining == 7 }
        assertEquals(7, (home.stateFlow.value.dashboard as ContentViewState.Ready).content.dashboard.usage!!.answerReveals!!.remaining)
        assertEquals(2, f.calls("/me/subscription"))
        home.closeQuestion()
        profile.open()
        runCurrent()
        assertEquals(7, (profile.state.value.usage as ContentViewState.Ready).content.answerReveals!!.remaining)
        assertEquals(2, f.calls("/me/subscription"))
    }

    @Test fun aProfileUsageRefreshAlsoUpdatesTheOpenQuestionAndDashboard() = runTest(dispatcher) {
        val f = Fixture()
        val home = f.homeVm()
        readyHome(home)
        val profile = f.profileVm()
        profile.open()
        readyProfile(profile)
        home.openQuestion(QuestionResponse(4, "Question", "ANDROID", QuestionTopic(1, "kotlin", "Kotlin"),
            "MEDIUM", "JUNIOR", "SENIOR"))
        home.stateFlow.first { it.questionDetail?.isUsageLoading == false }
        f.reveals = 0
        profile.loadUsage()
        profile.state.first { (it.usage as? ContentViewState.Ready)?.content?.answerReveals?.remaining == 0 }
        home.stateFlow.first { it.questionDetail?.revealsRemaining == 0 }
        assertFalse(home.stateFlow.value.questionDetail!!.canReveal)
        assertEquals(0, (home.stateFlow.value.dashboard as ContentViewState.Ready).content.dashboard.usage!!.answerReveals!!.remaining)
    }

    @Test fun anOldDashboardResponseCannotOverwriteNewUsageFromTheServer() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture()
        val profile = f.profileVm()
        profile.open()
        readyProfile(profile)
        profile.state.first { it.usage is ContentViewState.Ready }
        f.beforeDashboard = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        val home = f.homeVm()
        try {
            started.await()
            f.reveals = 7
            f.auth.accountUsage(forceRefresh = true)
        } finally { release.countDown() }
        readyHome(home)
        assertEquals(7, f.manage.currentUsage()!!.answerReveals!!.remaining)
        assertEquals(7, (home.stateFlow.value.dashboard as ContentViewState.Ready).content.dashboard.usage!!.answerReveals!!.remaining)
        profile.state.first { (it.usage as? ContentViewState.Ready)?.content?.answerReveals?.remaining == 7 }
    }

    @Test fun concurrentFirstUsageRequestsShareTheSameNetworkResult() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture()
        f.beforeUsage = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        val first = async { f.auth.accountUsage() }
        try {
            started.await()
            val second = async { f.auth.accountUsage() }
            runCurrent()
            assertEquals(1, f.calls("/me/subscription"))
            release.countDown()
            assertEquals(first.await(), second.await())
        } finally { release.countDown() }
        assertEquals(1, f.calls("/me/subscription"))
    }

    @Test fun usageCacheIsClearedAndNeverReusedByAnotherUser() = runTest(dispatcher) {
        val f = Fixture()
        assertEquals(8, f.auth.accountUsage().answerReveals!!.remaining)
        f.auth.clearSession()
        assertNull(f.manage.currentUsage())
        assertNull(f.manage.cachedUsage().first())
        f.tokens.saveSession("other-access", "other-refresh")
        assertNull(f.manage.currentUsage())
        f.reveals = 2
        assertEquals(2, f.auth.accountUsage().answerReveals!!.remaining)
        assertEquals(2, f.calls("/me/subscription"))
    }

    @Test fun aFailedUsageRefreshKeepsTheLastSuccessfulSharedValue() = runTest(dispatcher) {
        val f = Fixture()
        val cached = f.auth.accountUsage()
        f.usageStatus = 500
        assertTrue(f.manage.usage(forceRefresh = true) is ContentResult.Error)
        assertEquals(cached, f.manage.currentUsage())
        assertEquals(cached, f.home.currentUsage())
        assertEquals(2, f.calls("/me/subscription"))
    }

    @Test fun failedPlanUsageCanBeRetriedFromProfile() = runTest(dispatcher) {
        val f = Fixture().apply { usageStatus = 500 }
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.state.first { it.usage is ContentViewState.Failed }
        assertFalse(vm.state.value.sessionExpired)
        assertEquals(ProfilePage.PROFILE, vm.state.value.page)
        f.usageStatus = 200
        vm.loadUsage()
        vm.state.first { it.usage is ContentViewState.Ready }
        assertEquals(2, f.calls("/me/subscription"))
    }

    @Test fun accountChangesUpdateProfileAndRequestHomeReloadOnReturn() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        val profile = (vm.state.value.profile as ContentViewState.Ready).content
        vm.openAccount()
        assertEquals(ProfilePage.ACCOUNT, vm.state.value.page)
        vm.finishAccount(profile.copy(displayName = "New Developer"), true)
        assertEquals(ProfilePage.PROFILE, vm.state.value.page)
        assertEquals("New Developer", (vm.state.value.profile as ContentViewState.Ready).content.displayName)
        assertTrue(vm.back())
        vm.open()
        readyProfile(vm)
        assertFalse(vm.back())
    }

    @Test fun startupCachesStatsAndOpeningProfileReusesMeAndStats() = runTest(dispatcher) {
        val f = Fixture()
        val home = f.homeVm()
        readyHome(home)
        val profile = f.profileVm()
        profile.open()
        readyProfile(profile)
        profile.state.first { it.streakDays == 7 }
        assertEquals(1, f.calls("/stats"))
        assertEquals(1, f.calls("/me"))
        profile.back()
        profile.open()
        runCurrent()
        assertEquals(1, f.calls("/stats"))
        assertEquals(1, f.calls("/me"))
        assertEquals("Dmitry S.", (profile.state.value.profile as ContentViewState.Ready).content.displayName)
    }

    @Test fun everySuccessfulStatsRequestUpdatesTheProfileStreakCache() = runTest(dispatcher) {
        val f = Fixture()
        f.auth.getMe()
        f.home.getStats(null, null)
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.state.first { it.streakDays == 7 }
        f.streak = 9
        f.home.getStats("Europe/Prague", "2026-10-05")
        vm.state.first { it.streakDays == 9 }
        assertEquals(2, f.calls("/stats"))
    }

    @Test fun specializationsSaveAndReturnToProfileWhileBackDoesNotSave() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.editSpecialization()
        assertEquals(ProfilePage.SPECIALIZATION, vm.state.value.page)
        vm.selectSpecialization(Specialization.IOS)
        assertFalse(vm.back())
        assertEquals(0, f.calls("/me/specialization"))
        vm.editSpecialization()
        vm.selectSpecialization(Specialization.DOTNET)
        vm.saveSpecialization()
        assertTrue(vm.state.value.specialization.isLoading)
        vm.state.first { it.page == ProfilePage.PROFILE && !it.isSaving }
        assertEquals("DOTNET", (vm.state.value.profile as ContentViewState.Ready).content.specialization)
        assertTrue(vm.back())
        assertEquals(ProfilePage.CLOSED, vm.state.value.page)
        assertEquals(1, f.calls("/me/specialization"))
    }

    @Test fun languageEditorUsesAvailableLanguagesAndReturnsToProfileAfterSaving() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.editLanguage()
        vm.state.first { !it.language.isLoadingLanguages }
        assertEquals(listOf("en", "uk"), vm.state.value.language.languages.map { it.code })
        vm.selectLanguage("ja")
        assertEquals("en", vm.state.value.language.selectedLanguage)
        vm.selectLanguage("uk")
        vm.saveLanguage()
        vm.state.first { it.page == ProfilePage.PROFILE && !it.isSaving }
        assertEquals("uk", (vm.state.value.profile as ContentViewState.Ready).content.preparationLanguage)
        assertTrue(vm.back())
        assertEquals(1, f.calls("/me/preparation-language"))
    }

    @Test fun failedSaveKeepsTheEditorAndShowsAnError() = runTest(dispatcher) {
        val f = Fixture().apply { updateStatus = 500 }
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.editSpecialization()
        vm.selectSpecialization(Specialization.BACKEND)
        vm.saveSpecialization()
        vm.state.first { it.specialization.error != null }
        assertEquals(ProfilePage.SPECIALIZATION, vm.state.value.page)
        assertFalse(vm.state.value.isSaving)
        assertEquals("ANDROID", (vm.state.value.profile as ContentViewState.Ready).content.specialization)
        vm.back()
        assertFalse(vm.back())
    }

    @Test fun backAndRepeatedSubmitAreBlockedWhileSaving() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply { beforeUpdate = {
            started.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
        } }
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.editSpecialization()
        try {
            vm.saveSpecialization()
            started.await()
            vm.saveSpecialization()
            assertFalse(vm.back())
            assertEquals(ProfilePage.SPECIALIZATION, vm.state.value.page)
            assertEquals(1, f.calls("/me/specialization"))
        } finally { release.countDown() }
        vm.state.first { it.page == ProfilePage.PROFILE }
    }

    @Test fun themeUpdatesImmediatelyPersistsAndResetsToSystemWhenCleared() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.profileVm()
        vm.showThemeDialog()
        vm.selectTheme(ThemeMode.LIGHT)
        runCurrent()
        assertEquals(ThemeMode.LIGHT, vm.state.value.theme)
        assertFalse(vm.state.value.themeDialog)
        assertEquals(ThemeMode.LIGHT, AppPreferences(f.prefs).theme.value)
        vm.selectTheme(ThemeMode.DARK)
        runCurrent()
        assertEquals(ThemeMode.DARK, vm.state.value.theme)
        f.preferences.clear()
        runCurrent()
        assertEquals(ThemeMode.SYSTEM, vm.state.value.theme)
        assertEquals(ThemeMode.SYSTEM, AppPreferences(f.prefs).theme.value)
    }

    @Test fun returningHomeReloadsMeAndAllTabDataAndReplacesTheStatisticsCache() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.homeVm()
        readyHome(vm)
        f.auth.selectSpecialization(Specialization.IOS)
        f.auth.selectPreparationLanguage("uk")
        f.streak = 8
        vm.reloadAfterProfileChange()
        vm.stateFlow.first {
            it.dashboard is ContentViewState.Ready && it.statistics is ContentViewState.Ready &&
                it.topics is ContentViewState.Ready && it.questions is ContentViewState.Ready &&
                it.favorites is ContentViewState.Ready && it.topicCatalog is ContentViewState.Ready
        }
        val profile = (vm.stateFlow.value.dashboard as ContentViewState.Ready).content.profile
        assertEquals("IOS", profile.specialization)
        assertEquals("uk", profile.preparationLanguage)
        assertEquals(2, f.calls("/me"))
        assertEquals(2, f.calls("/dashboard"))
        assertEquals(2, f.calls("/stats"))
        assertEquals(1, f.calls("/questions"))
        assertEquals(1, f.calls("/questions/saved"))
        assertEquals(8, f.cache.observe(f.tokens.sessionVersion).first()!!.summary.streakDays)
    }

    @Test fun deleteRequiresConfirmationAndCancelDoesNotSendARequest() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.deleteAccount()
        vm.showDeleteAccountDialog()
        assertFalse(vm.state.value.deleteAccountDialog)
        vm.openAccount()
        vm.showDeleteAccountDialog()
        assertTrue(vm.state.value.deleteAccountDialog)
        vm.dismissDeleteAccountDialog()
        assertFalse(vm.state.value.deleteAccountDialog)
        vm.showDeleteAccountDialog()
        assertFalse(vm.back())
        assertFalse(vm.state.value.deleteAccountDialog)
        assertEquals(ProfilePage.ACCOUNT, vm.state.value.page)
        assertTrue(f.requests.none { it.method == "DELETE" })
        assertNotNull(f.tokens.refreshToken)
    }

    @Test fun deletingAccountClearsTheSameLocalDataAsLogoutBeforeNavigating() = runTest(dispatcher) {
        val f = Fixture()
        f.seedData()
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.openAccount()
        vm.showDeleteAccountDialog()
        vm.deleteAccount()
        vm.state.first { it.signedOut }
        val request = f.requests.single { it.method == "DELETE" }
        assertEquals("/me", request.url.encodedPath)
        assertEquals("Bearer access", request.header("Authorization"))
        assertNull(request.body)
        assertNull(request.url.query)
        assertEquals(0, f.calls("/auth/logout"))
        assertNull(f.tokens.accessToken)
        assertNull(f.tokens.refreshToken)
        assertFalse(f.database.topicDao().hasCatalog("seed"))
        assertTrue(f.prefs.all.isEmpty())
        assertEquals(ThemeMode.SYSTEM, f.preferences.theme.value)
        assertNull(f.cache.observe(f.tokens.sessionVersion).first())
        assertNull(f.manage.currentUsage())
        assertSame(ContentViewState.NotLoaded, vm.state.value.profile)
        assertEquals(ProfilePage.CLOSED, vm.state.value.page)
        assertFalse(vm.state.value.deleteAccountDialog)
    }

    @Test fun failedDeleteKeepsAccountAndDataAndAllowsRetry() = runTest(dispatcher) {
        val f = Fixture().apply { deleteStatus = 500 }
        f.seedData()
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.openAccount()
        vm.showDeleteAccountDialog()
        vm.deleteAccount()
        vm.state.first { it.deleteAccountError }
        assertFalse(vm.state.value.signedOut)
        assertFalse(vm.state.value.isSaving)
        assertTrue(vm.state.value.deleteAccountDialog)
        assertEquals("refresh", f.tokens.refreshToken)
        assertTrue(f.database.topicDao().hasCatalog("seed"))
        assertEquals(ThemeMode.DARK, f.preferences.theme.value)
        assertNotNull(f.cache.observe(f.tokens.sessionVersion).first())
        f.deleteStatus = 204
        vm.deleteAccount()
        vm.state.first { it.signedOut }
        assertEquals(2, f.requests.count { it.method == "DELETE" })
    }

    @Test fun deleteLocksUiAndPreventsDismissalLogoutAndDuplicateRequests() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture()
        f.beforeDelete = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.openAccount()
        try {
            vm.showDeleteAccountDialog()
            vm.deleteAccount()
            started.await()
            assertTrue(vm.state.value.isDeletingAccount)
            assertTrue(vm.state.value.isSaving)
            vm.deleteAccount()
            vm.dismissDeleteAccountDialog()
            vm.signOut()
            assertFalse(vm.back())
            assertTrue(vm.state.value.deleteAccountDialog)
            assertEquals(1, f.requests.count { it.method == "DELETE" })
            assertEquals(0, f.calls("/auth/logout"))
            assertNotNull(f.tokens.refreshToken)
        } finally { release.countDown() }
        vm.state.first { it.signedOut }
    }

    @Test fun deleteRefreshesAnExpiredAccessTokenThenRetriesTheAuthorizedRequest() = runTest(dispatcher) {
        val f = Fixture().apply { expireDeleteAccess = true }
        f.seedData()
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.openAccount()
        vm.showDeleteAccountDialog()
        vm.deleteAccount()
        vm.state.first { it.signedOut }
        val requests = f.requests.filter { it.method == "DELETE" }
        assertEquals(2, requests.size)
        assertEquals("Bearer renewed", requests.last().header("Authorization"))
        assertEquals(1, f.calls("/auth/refresh"))
        assertTrue(requests.all { it.body == null })
        assertNull(f.tokens.refreshToken)
    }

    @Test fun lateDeletionResponseCannotEraseAnotherUserSession() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture()
        f.seedData()
        f.beforeDelete = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        val deletion = async { runCatching { f.auth.deleteAccount() } }
        try {
            started.await()
            f.tokens.saveSession("other-access", "other-refresh")
        } finally { release.countDown() }
        assertTrue(deletion.await().exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals("other-access", f.tokens.accessToken)
        assertEquals("other-refresh", f.tokens.refreshToken)
        assertTrue(f.database.topicDao().hasCatalog("seed"))
    }

    @Test fun signOutClearsRoomTokensPreferencesProfileAndStatsBeforeNavigating() = runTest(dispatcher) {
        val f = Fixture()
        f.seedData()
        val vm = f.profileVm()
        vm.open()
        readyProfile(vm)
        vm.signOut()
        vm.state.first { it.signedOut }
        val request = f.requests.single { it.url.encodedPath == "/auth/logout" }
        assertEquals("POST", request.method)
        assertNull(request.url.query)
        assertNull(request.header("Authorization"))
        val body = Gson().fromJson(Buffer().also { request.body!!.writeTo(it) }.readUtf8(), JsonObject::class.java)
        assertEquals(setOf("refreshToken"), body.keySet())
        assertEquals("refresh", body["refreshToken"].asString)
        assertEquals(0, f.calls("/auth/refresh"))
        assertNull(f.tokens.accessToken)
        assertNull(f.tokens.refreshToken)
        assertFalse(f.database.topicDao().hasCatalog("seed"))
        assertTrue(f.database.topicDao().topics("seed").isEmpty())
        assertTrue(f.prefs.all.isEmpty())
        assertEquals(ThemeMode.SYSTEM, f.preferences.theme.value)
        assertNull(f.cache.observe(f.tokens.sessionVersion).first())
        assertSame(ContentViewState.NotLoaded, vm.state.value.profile)
        assertNull(f.manage.currentUsage())
        assertNull(f.home.currentUsage())
    }

    @Test fun logoutStillClearsLocalDataWhenOfflineOrRejectedAndNeverRefreshes() = runTest(dispatcher) {
        for (failure in listOf("offline", "unauthorized", "server")) {
            val f = Fixture()
            f.seedData()
            f.logoutOffline = failure == "offline"
            f.logoutStatus = if (failure == "unauthorized") 401 else 500
            val vm = f.profileVm()
            vm.signOut()
            vm.state.first { it.signedOut }
            assertEquals(1, f.calls("/auth/logout"))
            assertEquals(0, f.calls("/auth/refresh"))
            assertNull(f.tokens.accessToken)
            assertNull(f.tokens.refreshToken)
            assertFalse(f.database.topicDao().hasCatalog("seed"))
            assertTrue(f.prefs.all.isEmpty())
            assertNull(f.cache.observe(f.tokens.sessionVersion).first())
        }
    }

    @Test fun logoutWithoutRefreshTokenSkipsRequestButClearsLocalData() = runTest(dispatcher) {
        val f = Fixture()
        f.seedData()
        f.tokens.refreshToken = null
        val vm = f.profileVm()
        vm.signOut()
        vm.state.first { it.signedOut }
        assertEquals(0, f.calls("/auth/logout"))
        assertEquals(0, f.calls("/auth/refresh"))
        assertNull(f.tokens.accessToken)
        assertFalse(f.database.topicDao().hasCatalog("seed"))
        assertTrue(f.prefs.all.isEmpty())
    }

    @Test fun logoutLocksUiAndIgnoresDuplicateClicksUntilRevocationCompletes() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture()
        f.seedData()
        f.beforeLogout = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        val vm = f.profileVm()
        try {
            vm.signOut()
            started.await()
            assertTrue(vm.state.value.isSigningOut)
            assertFalse(vm.state.value.signedOut)
            assertEquals("refresh", f.tokens.refreshToken)
            vm.signOut()
            assertEquals(1, f.calls("/auth/logout"))
        } finally { release.countDown() }
        vm.state.first { it.signedOut }
        assertNull(f.tokens.refreshToken)
    }

    @Test fun lateLogoutCannotClearANewerSession() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture()
        f.seedData()
        f.beforeLogout = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        val logout = async { f.auth.logout() }
        try {
            started.await()
            f.tokens.saveSession("other-access", "other-refresh")
        } finally { release.countDown() }
        logout.await()
        assertEquals("other-access", f.tokens.accessToken)
        assertEquals("other-refresh", f.tokens.refreshToken)
        assertTrue(f.database.topicDao().hasCatalog("seed"))
        assertEquals(1, f.calls("/auth/logout"))
    }

    @Test fun signupUsesTheSameDataCleanupBeforeRegisteringAnotherAccount() = runTest(dispatcher) {
        val f = Fixture()
        f.seedData()
        val registration = f.auth.signup(SignupPayload("new@example.com", "password123", "New user"))
        assertNull(f.tokens.refreshToken)
        assertFalse(f.database.topicDao().hasCatalog("seed"))
        assertTrue(f.prefs.all.isEmpty())
        assertNull(f.cache.observe(f.tokens.sessionVersion).first())
        assertEquals("Account created", registration.blockingGet().message)
    }

    @Test fun cacheIsScopedToTheSessionAndLateCallsCannotRestoreClearedData() = runTest(dispatcher) {
        val cache = SessionStatsCache()
        val generation = cache.generation()
        cache.update(generation, 1, StatsResponse(summary = StatsSummary(streakDays = 7)))
        assertNull(cache.observe(2).first())
        cache.clear()
        cache.update(generation, 1, StatsResponse(summary = StatsSummary(streakDays = 9)))
        assertNull(cache.observe(1).first())
        cache.update(cache.generation(), 2, StatsResponse(summary = StatsSummary(streakDays = 3)))
        assertEquals(3, cache.observe(2).first()!!.summary.streakDays)
    }

    @Test fun clearingStatsWhileARequestIsRunningPreventsLateCacheWrites() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply { beforeStats = {
            started.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
        } }
        val home = f.homeVm()
        try {
            started.await()
            f.cache.clear()
        } finally { release.countDown() }
        readyHome(home)
        assertNull(f.cache.observe(f.tokens.sessionVersion).first())
    }
}
