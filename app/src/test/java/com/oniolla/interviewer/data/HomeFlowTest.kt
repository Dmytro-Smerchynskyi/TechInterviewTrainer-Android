package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.graphics.Color
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.GetTopicsUseCase
import com.oniolla.interviewer.domain.TopicCatalogUseCase
import com.oniolla.interviewer.domain.TopicsFilter
import com.oniolla.interviewer.domain.GetStatsUseCase
import com.oniolla.interviewer.domain.GetFavoriteQuestionsUseCase
import com.oniolla.interviewer.domain.GetQuestionsUseCase
import com.oniolla.interviewer.domain.SetQuestionSavedUseCase
import com.oniolla.interviewer.domain.loadContent
import com.oniolla.interviewer.ui.home.ContentViewState
import com.oniolla.interviewer.ui.home.HomeTab
import com.oniolla.interviewer.ui.home.HomeViewModel
import com.oniolla.interviewer.ui.home.displayNameInitials
import com.oniolla.interviewer.ui.home.parseTopicColor
import com.oniolla.interviewer.ui.home.percent
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runCurrent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFlowTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class MemoryTokens : SessionTokenStorage {
        override var sessionVersion: Long = 0
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
    }

    private class Fixture {
        val requests = CopyOnWriteArrayList<Request>()
        var dashboardStatus = 200
        var topicsStatus = 200
        var meStatus = 200
        var offlinePath: String? = null
        var dashboardBody = """{
          "readiness":{"score":67.5,"answered":124,"practicedTopics":8,"totalTopics":10,
            "gradePerformance":[{"grade":"JUNIOR","answered":10,"averageScore":72.5}],
            "label":"BUILDING_FOUNDATIONS"},
          "dueCount":18,
          "todayReview":[{"id":4,"question":"Cold vs Hot Flow","specialization":"ANDROID",
            "topic":{"id":7,"slug":"flow","name":"Flow","visual":{"iconUrl":"https://example.test/flow.svg",
              "iconVersion":2,"darkColor":"#8EF313","lightColor":"#F7DD88"}},
            "difficulty":"MEDIUM","minGrade":"JUNIOR","maxGrade":"SENIOR","tags":["stream"],"saved":true,
            "progress":{"questionId":4,"attempts":1,"lastScore":70.5,"masteryScore":60,
              "lastReviewedAt":"2026-10-12T15:37:43Z","nextReviewAt":"2026-10-13T15:37:43Z",
              "mastered":false,"updatedAt":"2026-10-12T15:37:43Z"},
            "updatedAt":"2026-10-12T15:37:43Z","sortOrder":0,"language":"en","requestedLanguage":"en"}],
          "weakTopics":[{"topicId":7,"name":"Flow","answered":12,"averageScore":60,"mastery":42.5,
            "lastActivityAt":null,"visual":{"iconUrl":"https://example.test/flow.svg","iconVersion":2,
              "darkColor":"#D086A5","lightColor":"#EE7E14"}}],
          "lastInterview":{"id":"interview","grade":"JUNIOR","status":"IN_PROGRESS","overallScore":null,
            "startedAt":"2026-10-12T15:37:43Z","finishedAt":null,"createdAt":"2026-10-12T15:37:43Z"},
          "usage":{"plan":"FREE","answerReveals":{"used":2,"limit":20,"remaining":18},
            "aiEvaluations":{"used":8,"reserved":1,"limit":20,"remaining":11,
              "periodEndsAt":"2026-11-12T15:37:43Z","resetAt":"2026-11-12T15:37:43Z"},
            "questions":{"unlimited":true,"limitPerTopic":null}}
        }"""
        var topicsBody = """[
          {"id":9,"specialization":"ANDROID","slug":"hidden","name":"Hidden","description":null,
            "sortOrder":0,"active":false,"visual":null},
          {"id":7,"specialization":"ANDROID","slug":"flow","name":"Flow","description":"Streams",
            "sortOrder":2,"active":true,"visual":{"iconUrl":"https://example.test/flow.svg",
              "iconVersion":3,"darkColor":"#0235F0","lightColor":"#A48D35"},
            "answered":12,"averageScore":60.5,"mastery":42.5},
          {"id":1,"specialization":"ANDROID","slug":"kotlin","name":"Kotlin","description":"Essentials",
            "sortOrder":1,"active":true,"visual":null}
        ]"""
        var weakTopicsBody: String? = null
        var beforeTopics: ((Request) -> Unit)? = null
        val tokens = MemoryTokens()
        val cache = MemoryTopicCache()
        val home: HomeRepository
        val dashboard: GetDashboardUseCase
        val topics: GetTopicsUseCase

        init {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests.add(request)
                val path = request.url.encodedPath
                if (offlinePath == path) throw IOException("Offline")
                val status = when (path) {
                    "/dashboard" -> dashboardStatus
                    "/topics" -> topicsStatus
                    "/questions" -> 200
                    "/stats" -> 200
                    "/me" -> meStatus
                    else -> error("Unexpected endpoint $path")
                }
                val body = if (status >= 400) """{"error":{"code":"REQUEST_FAILED","message":"Server message"}}"""
                    else when (path) {
                        "/stats" -> """{"timeZone":"UTC","serverTime":"2026-10-14T12:00:00.000Z","weekStart":"2026-10-12","weekEnd":"2026-10-18","summary":{"answered":0,"streakDays":0,"dueCount":0,"averageStudySeconds":null},"readiness":{"score":0,"gradePerformance":[]},"studyProgress":[],"topics":[],"weakTopics":[],"weakKeyPoints":[]}"""
                        "/dashboard" -> dashboardBody
                        "/topics" -> if (request.url.queryParameter("type") == "weak") weakTopicsBody ?: topicsBody else topicsBody
                        "/questions" -> """{"items":[],"nextCursor":null,"topics":[],"access":null}"""
                        else -> """{"id":"user","email":"developer@example.com","displayName":"Dmitry S",
                          "emailVerified":true,"specialization":"ANDROID","createdAt":"","updatedAt":"",
                          "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"en"}"""
                    }
                if (path == "/topics") beforeTopics?.invoke(request)
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(status).message("Test response")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
            val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build().create(Api::class.java)
            val sessionRefresh = SessionRefreshCoordinator(tokens, api)
            home = HomeRepository(api, cache, sessionRefresh)
            dashboard = GetDashboardUseCase(home, AuthRepository(tokens, api, sessionRefresh))
            topics = GetTopicsUseCase(home)
        }

        fun paths() = requests.map { it.url.encodedPath }
        fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
            HomeViewModel(dashboard, topics, GetQuestionsUseCase(home), handle, TopicCatalogUseCase(home), SetQuestionSavedUseCase(home), GetFavoriteQuestionsUseCase(home), GetStatsUseCase(home), com.oniolla.interviewer.domain.GetQuestionAnswerUseCase(home))
    }

    @Test fun dashboardAndProfileUseGetWithoutPayloadOrQuery() = runBlocking {
        val fixture = Fixture()
        val content = (fixture.dashboard(Unit) as ContentResult.Success).content
        assertEquals(setOf("/dashboard", "/me"), fixture.paths().toSet())
        fixture.requests.forEach {
            assertEquals("GET", it.method)
            assertNull(it.body)
            assertNull(it.url.query)
        }
        assertEquals("Dmitry S", content.profile.displayName)
        assertEquals(67.5, content.dashboard.readiness.score, 0.0)
        assertEquals("BUILDING_FOUNDATIONS", content.dashboard.readiness.label)
        assertEquals(72.5, content.dashboard.readiness.gradePerformance.single().averageScore, 0.0)
        assertEquals(18, content.dashboard.dueCount)
        assertEquals(124, content.dashboard.readiness.answered)
    }

    @Test fun dashboardNestedQuestionsVisualsProgressAndQuotasAreDecoded() = runBlocking {
        val data = Fixture().home.getDashboard()
        val question = data.todayReview.single()
        assertEquals("Cold vs Hot Flow", question.question)
        assertEquals(2, question.topic.visual!!.iconVersion)
        assertEquals("#8EF313", question.topic.visual!!.darkColor)
        assertEquals("#F7DD88", question.topic.visual!!.lightColor)
        assertTrue(question.saved)
        assertEquals(70.5, question.progress!!.lastScore, 0.0)
        assertFalse(question.progress!!.mastered)
        assertEquals(listOf("stream"), question.tags)
        assertEquals("en", question.requestedLanguage)
        assertEquals(42.5, data.weakTopics.single().mastery, 0.0)
        assertNull(data.lastInterview!!.overallScore)
        assertNull(data.lastInterview!!.finishedAt)
        assertEquals(11, data.usage!!.aiEvaluations!!.remaining)
        assertEquals(1, data.usage!!.aiEvaluations!!.reserved)
        assertTrue(data.usage!!.questions!!.unlimited)
    }

    @Test fun emptyDashboardAndNullableSectionsAreValid() = runBlocking {
        val fixture = Fixture().apply {
            dashboardBody = """{"readiness":{"score":0,"answered":0,"practicedTopics":0,"totalTopics":0,
              "gradePerformance":[],"label":"BUILDING_FOUNDATIONS"},"dueCount":0,"todayReview":[],
              "weakTopics":[],"lastInterview":null,"usage":null}"""
        }
        val content = (fixture.dashboard(Unit) as ContentResult.Success).content.dashboard
        assertTrue(content.todayReview.isEmpty())
        assertTrue(content.weakTopics.isEmpty())
        assertNull(content.lastInterview)
        assertNull(content.usage)
    }

    @Test fun topicsUsesParameterlessGetAndSortsActiveTopics() = runBlocking {
        val fixture = Fixture()
        val topics = (fixture.topics(TopicsFilter.ALL) as ContentResult.Success).content
        assertEquals(listOf("/topics"), fixture.paths())
        assertEquals("GET", fixture.requests.single().method)
        assertNull(fixture.requests.single().url.query)
        assertNull(fixture.requests.single().body)
        assertEquals(listOf("Kotlin", "Flow"), topics.map { it.name })
        assertEquals("Streams", topics.last().description)
        assertEquals("#0235F0", topics.last().visual!!.darkColor)
        assertEquals("#A48D35", topics.last().visual!!.lightColor)
        assertNull(topics.first().visual)
        assertEquals(12, topics.last().answered)
        assertEquals(60.5, topics.last().averageScore!!, 0.0)
        assertEquals(42.5, topics.last().mastery!!, 0.0)
        assertNull(topics.first().mastery)
    }

    @Test fun weakTopicsRequestUsesOnlyTheWeakTypeQuery() = runBlocking {
        val fixture = Fixture().apply { weakTopicsBody = "[]" }
        val result = fixture.topics(TopicsFilter.WEAK) as ContentResult.Success
        assertTrue(result.content.isEmpty())
        val request = fixture.requests.single()
        assertEquals("/topics", request.url.encodedPath)
        assertEquals("GET", request.method)
        assertEquals(setOf("type"), request.url.queryParameterNames)
        assertEquals("weak", request.url.queryParameter("type"))
        assertNull(request.body)
    }

    @Test fun zeroMasteryIsPreservedAsAValueInsteadOfMissingProgress() = runBlocking {
        val fixture = Fixture().apply {
            weakTopicsBody = """[{"id":7,"specialization":"ANDROID","slug":"flow","name":"Flow",
                "sortOrder":1,"active":true,"visual":null,"answered":0,"averageScore":0,"mastery":0}]"""
        }
        val topic = (fixture.topics(TopicsFilter.WEAK) as ContentResult.Success).content.single()
        assertEquals(0.0, topic.mastery!!, 0.0)
        assertEquals(0, topic.answered)
        assertEquals(0.0, topic.averageScore!!, 0.0)
    }

    @Test fun topicFilterChangesReloadAllAndWeakWithTheirCorrectQuery() = runTest(dispatcher) {
        val fixture = Fixture().apply { weakTopicsBody = "[]" }
        val handle = SavedStateHandle()
        val vm = fixture.viewModel(handle)
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        vm.selectTab(HomeTab.TOPICS)
        vm.stateFlow.first { it.topics is ContentViewState.Ready }
        assertSame(TopicsFilter.ALL, vm.stateFlow.value.topicsFilter)
        vm.selectTopicsFilter(TopicsFilter.WEAK)
        assertSame(ContentViewState.Loading, vm.stateFlow.value.topics)
        vm.stateFlow.first { it.topics is ContentViewState.Ready }
        assertSame(TopicsFilter.WEAK, vm.stateFlow.value.topicsFilter)
        assertEquals("WEAK", handle.get<String>("topicsFilter"))
        assertTrue((vm.stateFlow.value.topics as ContentViewState.Ready).content.isEmpty())
        vm.selectTopicsFilter(TopicsFilter.ALL)
        vm.stateFlow.first { it.topics is ContentViewState.Ready }
        assertEquals("ALL", handle.get<String>("topicsFilter"))
        assertEquals(listOf(null, null, "weak", null),
            fixture.requests.filter { it.url.encodedPath == "/topics" }.map { it.url.queryParameter("type") })
    }

    @Test fun homeSeeAllOpensWeakTopicsWithoutRefetchingTheWeakList() = runTest(dispatcher) {
        val fixture = Fixture()
        val handle = SavedStateHandle()
        val vm = fixture.viewModel(handle)
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        vm.openWeakTopics()
        assertSame(HomeTab.TOPICS, vm.stateFlow.value.tab)
        assertSame(TopicsFilter.WEAK, vm.stateFlow.value.topicsFilter)
        vm.stateFlow.first { it.topics is ContentViewState.Ready }
        val requests = fixture.requests.filter { it.url.queryParameter("type") == "weak" }
        assertEquals(1, requests.size)
        assertEquals("weak", requests.single().url.queryParameter("type"))
        assertEquals("TOPICS", handle.get<String>("homeTab"))
        assertEquals("WEAK", handle.get<String>("topicsFilter"))
        vm.selectTab(HomeTab.HOME)
        vm.openWeakTopics()
        assertEquals(1, fixture.requests.count { it.url.queryParameter("type") == "weak" })
    }

    @Test fun weakFilterIsRestoredWithTheTopicsTab() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "TOPICS", "topicsFilter" to "WEAK")))
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.topics is ContentViewState.Ready }
        assertSame(TopicsFilter.WEAK, vm.stateFlow.value.topicsFilter)
        assertEquals("weak", fixture.requests.single { it.url.queryParameter("type") == "weak" }.url.queryParameter("type"))
    }

    @Test fun weakRetryKeepsItsFilterAndRepeatedSelectionDoesNotDuplicateTheRequest() = runTest(dispatcher) {
        val fixture = Fixture().apply { topicsStatus = 500 }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        vm.openWeakTopics()
        vm.stateFlow.first { it.topics is ContentViewState.Failed }
        assertSame(TopicsFilter.WEAK, vm.stateFlow.value.topicsFilter)
        fixture.topicsStatus = 200
        vm.loadTopics()
        vm.selectTopicsFilter(TopicsFilter.WEAK)
        vm.loadTopics()
        vm.stateFlow.first { it.topics is ContentViewState.Ready }
        assertEquals(listOf("weak", "weak"),
            fixture.requests.filter { it.url.queryParameter("type") == "weak" }.map { it.url.queryParameter("type") })
        vm.selectTab(HomeTab.HOME)
        vm.selectTab(HomeTab.TOPICS)
        assertSame(TopicsFilter.WEAK, vm.stateFlow.value.topicsFilter)
        assertEquals(2, fixture.requests.count { it.url.queryParameter("type") == "weak" })
    }

    @Test fun anOldAllResponseCannotReplaceTheNewWeakResponse() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            weakTopicsBody = "[]"
            beforeTopics = { request ->
                if (request.url.queryParameter("type") == null) {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                    finished.complete(Unit)
                }
            }
        }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        vm.selectTab(HomeTab.TOPICS)
        try {
            started.await()
            vm.selectTopicsFilter(TopicsFilter.WEAK)
            vm.stateFlow.first { it.topics is ContentViewState.Ready }
            assertTrue((vm.stateFlow.value.topics as ContentViewState.Ready).content.isEmpty())
        } finally { release.countDown() }
        finished.await()
        runCurrent()
        assertSame(TopicsFilter.WEAK, vm.stateFlow.value.topicsFilter)
        assertTrue((vm.stateFlow.value.topics as ContentViewState.Ready).content.isEmpty())
    }

    @Test fun emptyTopicsResponseIsSuccessful() = runBlocking {
        val fixture = Fixture().apply { topicsBody = "[]" }
        assertTrue((fixture.topics(TopicsFilter.ALL) as ContentResult.Success).content.isEmpty())
    }

    @Test fun serverErrorPreservesHttpMessage() = runBlocking {
        val fixture = Fixture().apply { dashboardStatus = 500 }
        assertEquals(ContentResult.Error("Server message"), fixture.dashboard(Unit))
    }

    @Test fun topicsErrorIsMappedWithoutCallingDashboard() = runBlocking {
        val fixture = Fixture().apply { topicsStatus = 400 }
        assertEquals(ContentResult.Error("Server message"), fixture.topics(TopicsFilter.ALL))
        assertEquals(listOf("/topics"), fixture.paths())
    }

    @Test fun offlineErrorHasTheExistingNetworkErrorMessage() = runBlocking {
        val fixture = Fixture().apply { offlinePath = "/topics" }
        assertEquals(ContentResult.Error("Something unexpected happened"), fixture.topics(TopicsFilter.ALL))
    }

    @Test fun unauthorizedDashboardAndTopicsSignalSessionExpiry() = runBlocking {
        assertSame(ContentResult.SessionExpired, Fixture().apply { dashboardStatus = 401 }.dashboard(Unit))
        assertSame(ContentResult.SessionExpired, Fixture().apply { topicsStatus = 401 }.topics(TopicsFilter.ALL))
        assertSame(ContentResult.SessionExpired, Fixture().apply { meStatus = 401 }.dashboard(Unit))
    }

    @Test fun cancellationIsNotConvertedIntoContentError() = runBlocking {
        val cancellation = CancellationException("Cancelled")
        try {
            loadContent<Unit> { throw cancellation }
            fail("Expected cancellation")
        } catch (error: CancellationException) { assertSame(cancellation, error) }
    }

    @Test fun homeInitializesCatalogOnceAndReturningHomeDoesNotReloadIt() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        assertSame(ContentViewState.Loading, vm.stateFlow.value.dashboard)
        assertSame(ContentViewState.NotLoaded, vm.stateFlow.value.topics)
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.topicCatalog is ContentViewState.Ready }
        runCurrent()
        assertEquals(1, fixture.paths().count { it == "/topics" })
        vm.selectTab(HomeTab.TOPICS)
        vm.stateFlow.first { it.topics is ContentViewState.Ready }
        assertEquals(2, fixture.paths().count { it == "/topics" })
        vm.selectTab(HomeTab.HOME)
        runCurrent()
        vm.selectTab(HomeTab.TOPICS)
        assertTrue(vm.stateFlow.value.topics is ContentViewState.Ready)
        assertEquals(2, fixture.paths().count { it == "/topics" })
        assertEquals(1, fixture.cache.refreshes.value)
    }

    @Test fun weakTopicOpensQuestionsAndPreservesTheSearchAcrossTabChanges() = runTest(dispatcher) {
        val fixture = Fixture()
        val handle = SavedStateHandle()
        val vm = fixture.viewModel(handle)
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        vm.openTopicQuestions("Coroutines")
        vm.stateFlow.first { it.questions is ContentViewState.Ready }
        assertSame(HomeTab.QUESTIONS, vm.stateFlow.value.tab)
        assertEquals("Coroutines", vm.stateFlow.value.questionSearch)
        assertEquals("QUESTIONS", handle.get<String>("homeTab"))
        assertEquals("Coroutines", handle.get<String>("questionSearch"))
        vm.selectTab(HomeTab.HOME)
        vm.selectTab(HomeTab.QUESTIONS)
        assertEquals("Coroutines", vm.stateFlow.value.questionSearch)
        vm.updateQuestionSearch("")
        vm.stateFlow.first { it.questions is ContentViewState.Ready }
        assertEquals("", handle.get<String>("questionSearch"))
        assertEquals(1, fixture.paths().count { it == "/topics" })
        assertEquals(1, fixture.cache.refreshes.value)
        assertSame(ContentViewState.NotLoaded, vm.stateFlow.value.topics)
    }

    @Test fun savedTabAndQuestionSearchAreRestored() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS", "questionSearch" to "Flow")))
        assertSame(HomeTab.QUESTIONS, vm.stateFlow.value.tab)
        assertEquals("Flow", vm.stateFlow.value.questionSearch)
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.questions is ContentViewState.Ready }
    }

    @Test fun topicsFailureKeepsDashboardAndRetryRecovers() = runTest(dispatcher) {
        val fixture = Fixture().apply { topicsStatus = 500 }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        vm.selectTab(HomeTab.TOPICS)
        vm.stateFlow.first { it.topics is ContentViewState.Failed }
        assertEquals(ContentViewState.Failed("Server message"), vm.stateFlow.value.topics)
        assertTrue(vm.stateFlow.value.dashboard is ContentViewState.Ready)
        fixture.topicsStatus = 200
        vm.loadTopics()
        assertSame(ContentViewState.Loading, vm.stateFlow.value.topics)
        vm.stateFlow.first { it.topics is ContentViewState.Ready }
        assertEquals(3, fixture.paths().count { it == "/topics" })
    }

    @Test fun repeatedRetryWhileLoadingDoesNotDuplicateDashboardCalls() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        repeat(5) { vm.loadDashboard() }
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        assertEquals(1, fixture.paths().count { it == "/dashboard" })
        assertEquals(1, fixture.paths().count { it == "/me" })
    }

    @Test fun unauthorizedContentSetsNavigationState() = runTest(dispatcher) {
        val fixture = Fixture().apply { dashboardStatus = 401 }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.sessionExpired }
        assertTrue(vm.stateFlow.value.sessionExpired)
        assertSame(ContentViewState.NotLoaded, vm.stateFlow.value.dashboard)
    }

    @Test fun initialsHandleSingleNamesMultipleWordsAndNonLatinNames() {
        assertEquals("DS", displayNameInitials("  Dmitry  Smerchynskyi "))
        assertEquals("DE", displayNameInitials("Developer"))
        assertEquals("АЛ", displayNameInitials("Алексей"))
        assertEquals("AB", displayNameInitials("Alice Maria Brown"))
        assertEquals("D", displayNameInitials("D"))
        assertEquals("", displayNameInitials("  "))
    }

    @Test fun topicColorsAreParsedWithoutCrashingForInvalidInput() {
        val fallback = Color.Blue
        assertEquals(Color(0xFF8EF313), parseTopicColor("#8EF313", fallback))
        assertEquals(Color(0x80F7DD88), parseTopicColor("#80F7DD88", fallback))
        listOf(null, "", "#", "#GGGGGG", "#FF", "not a color").forEach {
            assertEquals(fallback, parseTopicColor(it, fallback))
        }
    }

    @Test fun questionTabsAndFilterSelectionReuseTheCatalogWithoutNetworkReload() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.topicCatalog is ContentViewState.Ready }
        runCurrent()
        val catalog = vm.stateFlow.value.topicCatalog
        vm.selectTab(HomeTab.QUESTIONS)
        vm.stateFlow.first { it.questions is ContentViewState.Ready }
        vm.selectQuestionTopic(7, "Flow")
        vm.stateFlow.first { it.questions is ContentViewState.Ready }
        vm.clearQuestionTopic()
        vm.stateFlow.first { it.questions is ContentViewState.Ready }
        vm.selectTab(HomeTab.STATS)
        vm.selectTab(HomeTab.QUESTIONS)
        assertEquals(catalog, vm.stateFlow.value.topicCatalog)
        assertEquals(1, fixture.paths().count { it == "/topics" })
        assertEquals(1, fixture.cache.writes)
    }

    @Test fun repeatedHomeEntriesAndDashboardReloadsDoNotFetchTheCatalogAgain() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.topicCatalog is ContentViewState.Ready && it.dashboard is ContentViewState.Ready }
        runCurrent()
        val catalog = vm.stateFlow.value.topicCatalog
        fixture.topicsBody = "[]"
        repeat(3) {
            vm.selectTab(HomeTab.STATS)
            vm.selectTab(HomeTab.HOME)
            runCurrent()
        }
        vm.loadDashboard()
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        runCurrent()
        assertEquals(catalog, vm.stateFlow.value.topicCatalog)
        assertEquals(1, fixture.cache.writes)
        assertEquals(1, fixture.cache.refreshes.value)
        assertEquals(1, fixture.paths().count { it == "/topics" })
        assertEquals(1, fixture.paths().count { it == "/me" })
        assertEquals(2, fixture.paths().count { it == "/dashboard" })
    }

    @Test fun launchingANewHomeViewModelRefreshesTheCatalogForThatLaunch() = runTest(dispatcher) {
        val fixture = Fixture()
        val first = fixture.viewModel()
        first.stateFlow.first { it.topicCatalog is ContentViewState.Ready }
        runCurrent()
        fixture.topicsBody = "[]"
        val second = fixture.viewModel()
        fixture.cache.refreshes.first { it == 2 }
        second.stateFlow.first {
            it.dashboard is ContentViewState.Ready &&
                (it.topicCatalog as? ContentViewState.Ready)?.content?.isEmpty() == true
        }
        assertEquals(2, fixture.paths().count { it == "/topics" })
    }

    @Test fun unauthorizedProfileDoesNotStartCatalogInitialization() = runTest(dispatcher) {
        val fixture = Fixture().apply { meStatus = 401 }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.sessionExpired }
        runCurrent()
        assertFalse(fixture.paths().contains("/topics"))
        assertEquals(0, fixture.cache.writes)
    }

    @Test fun failedCatalogCanRetryAndDuplicateRetriesDoNotSendExtraRequests() = runTest(dispatcher) {
        val fixture = Fixture().apply { topicsStatus = 500 }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.topicCatalog is ContentViewState.Failed }
        runCurrent()
        assertTrue(vm.stateFlow.value.dashboard is ContentViewState.Ready)
        assertEquals(0, fixture.cache.writes)
        fixture.topicsStatus = 200
        repeat(5) { vm.refreshTopicCatalog() }
        vm.stateFlow.first { it.topicCatalog is ContentViewState.Ready }
        assertEquals(2, fixture.paths().count { it == "/topics" })
        assertEquals(1, fixture.cache.writes)
    }

    @Test fun aFailedExplicitCatalogRetryKeepsCachedTopicsAvailable() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.topicCatalog is ContentViewState.Ready }
        runCurrent()
        val cached = vm.stateFlow.value.topicCatalog
        fixture.topicsStatus = 500
        vm.selectTab(HomeTab.QUESTIONS)
        val event = async { vm.uiEventsFlow.first() }
        runCurrent()
        vm.refreshTopicCatalog()
        assertEquals(com.oniolla.interviewer.ui.UiEvent.Toast("Server message"), event.await())
        assertEquals(cached, vm.stateFlow.value.topicCatalog)
        assertEquals(1, fixture.cache.writes)
    }

    @Test fun expiredSessionDuringCatalogRefreshTriggersAuthNavigation() = runTest(dispatcher) {
        val fixture = Fixture().apply { topicsStatus = 401 }
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.sessionExpired }
        assertEquals(0, fixture.cache.writes)
    }

    @Test fun percentageRenderingClampsUnexpectedScores() {
        assertEquals(0, percent(-5.0))
        assertEquals(100, percent(120.0))
        assertEquals(67, percent(67.5))
        assertEquals(0, percent(Double.NaN))
    }
}
