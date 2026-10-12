package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.graphics.Color
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.GetTopicsUseCase
import com.oniolla.interviewer.domain.GetQuestionsUseCase
import com.oniolla.interviewer.domain.loadContent
import com.oniolla.interviewer.ui.home.ContentViewState
import com.oniolla.interviewer.ui.home.HomeTab
import com.oniolla.interviewer.ui.home.HomeViewModel
import com.oniolla.interviewer.ui.home.displayNameInitials
import com.oniolla.interviewer.ui.home.parseTopicColor
import com.oniolla.interviewer.ui.home.percent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFlowTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class MemoryTokens : SessionTokenStorage {
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
        override fun clear() { accessToken = null; refreshToken = null }
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
              "iconVersion":3,"darkColor":"#0235F0","lightColor":"#A48D35"}},
          {"id":1,"specialization":"ANDROID","slug":"kotlin","name":"Kotlin","description":"Essentials",
            "sortOrder":1,"active":true,"visual":null}
        ]"""
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
                    "/me" -> meStatus
                    else -> error("Unexpected endpoint $path")
                }
                val body = if (status >= 400) """{"error":{"code":"REQUEST_FAILED","message":"Server message"}}"""
                    else when (path) {
                        "/dashboard" -> dashboardBody
                        "/topics" -> topicsBody
                        "/questions" -> """{"items":[],"nextCursor":null,"topics":[],"access":null}"""
                        else -> """{"id":"user","email":"developer@example.com","displayName":"Dmitry S",
                          "emailVerified":true,"specialization":"ANDROID","createdAt":"","updatedAt":"",
                          "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"en"}"""
                    }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(status).message("Test response")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
            val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build().create(Api::class.java)
            home = HomeRepository(api)
            dashboard = GetDashboardUseCase(home, AuthRepository(MemoryTokens(), api))
            topics = GetTopicsUseCase(home)
        }

        fun paths() = requests.map { it.url.encodedPath }
        fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
            HomeViewModel(dashboard, topics, GetQuestionsUseCase(home), handle)
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
        val topics = (fixture.topics(Unit) as ContentResult.Success).content
        assertEquals(listOf("/topics"), fixture.paths())
        assertEquals("GET", fixture.requests.single().method)
        assertNull(fixture.requests.single().url.query)
        assertNull(fixture.requests.single().body)
        assertEquals(listOf("Kotlin", "Flow"), topics.map { it.name })
        assertEquals("Streams", topics.last().description)
        assertEquals("#0235F0", topics.last().visual!!.darkColor)
        assertEquals("#A48D35", topics.last().visual!!.lightColor)
        assertNull(topics.first().visual)
    }

    @Test fun emptyTopicsResponseIsSuccessful() = runBlocking {
        val fixture = Fixture().apply { topicsBody = "[]" }
        assertTrue((fixture.topics(Unit) as ContentResult.Success).content.isEmpty())
    }

    @Test fun serverErrorPreservesHttpMessage() = runBlocking {
        val fixture = Fixture().apply { dashboardStatus = 500 }
        assertEquals(ContentResult.Error("Server message"), fixture.dashboard(Unit))
    }

    @Test fun topicsErrorIsMappedWithoutCallingDashboard() = runBlocking {
        val fixture = Fixture().apply { topicsStatus = 400 }
        assertEquals(ContentResult.Error("Server message"), fixture.topics(Unit))
        assertEquals(listOf("/topics"), fixture.paths())
    }

    @Test fun offlineErrorHasTheExistingNetworkErrorMessage() = runBlocking {
        val fixture = Fixture().apply { offlinePath = "/topics" }
        assertEquals(ContentResult.Error("Something unexpected happened"), fixture.topics(Unit))
    }

    @Test fun unauthorizedDashboardAndTopicsSignalSessionExpiry() = runBlocking {
        assertSame(ContentResult.SessionExpired, Fixture().apply { dashboardStatus = 401 }.dashboard(Unit))
        assertSame(ContentResult.SessionExpired, Fixture().apply { topicsStatus = 401 }.topics(Unit))
        assertSame(ContentResult.SessionExpired, Fixture().apply { meStatus = 401 }.dashboard(Unit))
    }

    @Test fun cancellationIsNotConvertedIntoContentError() = runBlocking {
        val cancellation = CancellationException("Cancelled")
        try {
            loadContent<Unit> { throw cancellation }
            fail("Expected cancellation")
        } catch (error: CancellationException) { assertSame(cancellation, error) }
    }

    @Test fun homeLoadsInitiallyAndTopicsLoadOnlyWhenOpened() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        assertSame(ContentViewState.Loading, vm.stateFlow.value.dashboard)
        assertSame(ContentViewState.NotLoaded, vm.stateFlow.value.topics)
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        assertFalse(fixture.paths().contains("/topics"))
        vm.selectTab(HomeTab.TOPICS)
        assertSame(ContentViewState.Loading, vm.stateFlow.value.topics)
        vm.stateFlow.first { it.topics is ContentViewState.Ready }
        vm.selectTab(HomeTab.HOME)
        vm.selectTab(HomeTab.TOPICS)
        assertEquals(1, fixture.paths().count { it == "/topics" })
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
        assertFalse(fixture.paths().contains("/topics"))
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
        assertEquals(2, fixture.paths().count { it == "/topics" })
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

    @Test fun percentageRenderingClampsUnexpectedScores() {
        assertEquals(0, percent(-5.0))
        assertEquals(100, percent(120.0))
        assertEquals(67, percent(67.5))
        assertEquals(0, percent(Double.NaN))
    }
}
