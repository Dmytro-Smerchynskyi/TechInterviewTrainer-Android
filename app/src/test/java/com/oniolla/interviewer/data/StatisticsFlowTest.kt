package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.oniolla.interviewer.data.model.Readiness
import com.oniolla.interviewer.data.model.StudyProgressDay
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.home.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
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
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private lateinit var previousZone: TimeZone

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Prague"))
    }
    @After fun tearDown() {
        stores.forEach { it.clear() }
        TimeZone.setDefault(previousZone)
        Dispatchers.resetMain()
    }

    private class Tokens : SessionTokenStorage {
        override var sessionVersion = 0L
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
    }

    private class Fixture {
        val tokens = Tokens()
        val requests = CopyOnWriteArrayList<Request>()
        var status = 200
        var offline = false
        var expireAccess = false
        var refreshStatus = 200
        var nullableComparison = false
        var beforeStats: ((Request) -> Unit)? = null
        val repository: HomeRepository
        val auth: AuthRepository
        val stats: GetStatsUseCase

        init {
            val origin = "https://api.example.test/"
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, origin))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests.add(request)
                    val path = request.url.encodedPath
                    val code = when {
                        path == "/stats" && expireAccess && request.header("Authorization") == "Bearer access" -> 401
                        path == "/stats" -> status
                        path == "/auth/refresh" -> refreshStatus
                        else -> 200
                    }
                    if (path == "/stats") {
                        beforeStats?.invoke(request)
                        if (offline) throw IOException("Offline")
                    }
                    val body = if (code >= 400) {
                        val errorCode = if (code == 401) "UNAUTHORIZED" else "FAILED"
                        """{"error":{"code":"$errorCode","message":"Stats failed"}}"""
                    } else when (path) {
                        "/stats" -> body(request.url.queryParameter("weekStart") ?: "2026-10-12",
                            request.url.queryParameter("timeZone") ?: "UTC", nullableComparison)
                        "/dashboard" -> """{"readiness":{"score":0,"answered":0,"practicedTopics":0,
                            "totalTopics":0,"gradePerformance":[]},"todayReview":[],"weakTopics":[],"dueCount":0}"""
                        "/topics" -> "[]"
                        "/questions" -> """{"items":[],"nextCursor":null,"topics":[]}"""
                        "/me" -> """{"id":"user","email":"developer@example.com","displayName":"Dmitry",
                            "emailVerified":true,"specialization":"ANDROID","createdAt":"","updatedAt":"",
                            "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"en"}"""
                        "/auth/refresh" -> """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
                        else -> error("Unexpected path $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(code).message("Test response")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            val api = Retrofit.Builder().baseUrl(origin).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            val coordinator = SessionRefreshCoordinator(tokens, api)
            repository = HomeRepository(api, MemoryTopicCache(), coordinator)
            auth = AuthRepository(tokens, api, coordinator)
            stats = GetStatsUseCase(repository)
        }

        fun statsRequests() = requests.filter { it.url.encodedPath == "/stats" }

        companion object {
            fun body(week: String, zone: String, nullableComparison: Boolean): String {
                val comparison = if (nullableComparison) "null" else "12"
                val previous = if (nullableComparison) "null" else "66"
                return """{
                    "topics":[{"topicId":4,"name":"Localized topic","answered":42,"averageScore":80,"mastery":72,
                        "visual":{"iconUrl":"/topic.svg","iconVersion":3,"darkColor":"#7A129E","lightColor":"#52D3F3"}}],
                    "weakTopics":[{"topicId":4,"name":"Localized topic","mastery":72}],
                    "weakKeyPoints":[{"description":"Cancellation","topicId":4,"averageCoverage":45,"attempts":3}],
                    "readiness":{"score":78,"answered":124,"practicedTopics":6,"totalTopics":8,
                        "gradePerformance":[{"grade":"JUNIOR","answered":5,"averageScore":80}],
                        "previousWeekScore":$previous,"changeSinceLastWeekPoints":$comparison,
                        "comparedAt":"2026-10-13T20:07:05.333Z"},
                    "serverTime":"2026-10-13T20:07:05.333Z","timeZone":"$zone",
                    "weekStart":"$week","weekEnd":"${StatsDates.shiftDays(week, 6)}",
                    "summary":{"answered":124,"streakDays":7,"dueCount":18,"averageStudySeconds":null},
                    "studyProgress":[{"date":"$week","answered":6},
                        {"date":"${StatsDates.shiftDays(week, 1)}","answered":9}]
                }"""
            }
        }
    }

    private suspend fun viewModel(fixture: Fixture, handle: SavedStateHandle = SavedStateHandle(
        mapOf("homeTab" to "STATS")
    )): HomeViewModel {
        val vm = HomeViewModel(
            GetDashboardUseCase(fixture.repository, fixture.auth), GetTopicsUseCase(fixture.repository),
            GetQuestionsUseCase(fixture.repository), handle, TopicCatalogUseCase(fixture.repository),
            SetQuestionSavedUseCase(fixture.repository), GetFavoriteQuestionsUseCase(fixture.repository), fixture.stats
        )
        stores += ViewModelStore().apply { put("home", vm) }
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.topicCatalog is ContentViewState.Ready }
        return vm
    }

    private suspend fun ready(vm: HomeViewModel) {
        vm.stateFlow.first { (it.statistics as? ContentViewState.Ready)?.content?.let {
            content -> !content.isChartLoading && content.chartError == null
        } == true }
    }

    private fun content(vm: HomeViewModel) =
        (vm.stateFlow.value.statistics as ContentViewState.Ready<StatisticsContent>).content

    @Test fun statsUseGetWithoutPayloadAndEncodeOnlyTheSuppliedParameters() = runBlocking {
        val fixture = Fixture()
        fixture.stats(StatsRequest())
        val default = fixture.statsRequests().single()
        assertEquals("GET", default.method)
        assertNull(default.body)
        assertNull(default.url.query)
        fixture.stats(StatsRequest("Europe/Prague", "2026-10-05"))
        val request = fixture.statsRequests().last()
        assertEquals(setOf("timeZone", "weekStart"), request.url.queryParameterNames)
        assertEquals("Europe/Prague", request.url.queryParameter("timeZone"))
        assertEquals("2026-10-05", request.url.queryParameter("weekStart"))
        assertEquals("Bearer access", request.header("Authorization"))
    }

    @Test fun summaryReadinessTopicsAndNullableStudyTimeDecodeFromStats() = runBlocking {
        val fixture = Fixture()
        val response = (fixture.stats(StatsRequest()) as ContentResult.Success).content
        assertEquals(124, response.summary.answered)
        assertEquals(7, response.summary.streakDays)
        assertEquals(18, response.summary.dueCount)
        assertNull(response.summary.averageStudySeconds)
        assertEquals(78.0, response.readiness.score, 0.0)
        assertEquals(66.0, response.readiness.previousWeekScore!!, 0.0)
        assertEquals(12.0, readinessComparison(response.readiness)!!, 0.0)
        assertEquals(80.0, response.readiness.gradePerformance.single().averageScore, 0.0)
        val topic = response.topics.single()
        assertEquals(4L, topic.topicId)
        assertEquals("Localized topic", topic.name)
        assertEquals(72.0, topic.mastery, 0.0)
        assertEquals("#7A129E", topic.visual!!.darkColor)
        assertEquals("#52D3F3", topic.visual!!.lightColor)
        assertEquals("Cancellation", response.weakKeyPoints.single().description)
    }

    @Test fun nullComparisonStaysHiddenWhileZeroAndNegativeChangesAreValid() = runBlocking {
        val fixture = Fixture().apply { nullableComparison = true }
        val readiness = (fixture.stats(StatsRequest()) as ContentResult.Success).content.readiness
        assertNull(readiness.previousWeekScore)
        assertNull(readiness.changeSinceLastWeekPoints)
        assertNull(readinessComparison(readiness))
        assertNull(readinessComparison(Readiness(changeSinceLastWeekPoints = 12.0)))
        assertEquals(0.0, readinessComparison(Readiness(previousWeekScore = 50.0, changeSinceLastWeekPoints = 0.0))!!, 0.0)
        assertEquals(-5.5, readinessComparison(Readiness(previousWeekScore = 50.0, changeSinceLastWeekPoints = -5.5))!!, 0.0)
    }

    @Test fun currentWeekUsesTheRequestedTimeZoneAtTheSundayMondayBoundary() {
        val now = StatsDates.serverTimeMillis("2026-10-11T22:30:00.000Z")!!
        assertEquals("2026-10-05", StatsDates.currentWeek("UTC", now))
        assertEquals("2026-10-12", StatsDates.currentWeek("Europe/Prague", now))
        assertEquals("Europe/Prague", StatsDates.deviceTimeZone())
    }

    @Test fun shiftingWeeksHandlesDstAndYearBoundariesAndRejectsInvalidDates() {
        assertEquals("2026-10-26", StatsDates.shiftDays("2026-10-19", 7))
        assertEquals("2026-01-05", StatsDates.shiftDays("2025-12-29", 7))
        assertNull(StatsDates.validMonday("2026-10-13"))
        assertNull(StatsDates.validMonday("2026-02-30"))
        assertNull(StatsDates.validMonday("2026-2-2"))
        assertEquals("2026-10-12", StatsDates.validMonday("2026-10-12"))
        val afterDst = StatsDates.serverTimeMillis("2026-10-25T23:30:00.000Z")!!
        assertEquals("2026-10-26", StatsDates.currentWeek("Europe/Prague", afterDst))
    }

    @Test fun activityMapsToSevenCalendarDaysAndFillsMissingDaysWithZero() {
        val days = StatsDates.weekDays("2026-10-12", listOf(
            StudyProgressDay("2026-10-12", 2), StudyProgressDay("2026-10-12", 3),
            StudyProgressDay("2026-10-13", -1), StudyProgressDay("2026-10-19", 50)
        ))
        assertEquals((12..18).map { "2026-10-$it" }, days.map { it.date })
        assertEquals(listOf(5, 0, 0, 0, 0, 0, 0), days.map { it.answered })
    }

    @Test fun statsLoadAtHomeStartupAndTabVisitsReuseTheLoadedStats() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture, SavedStateHandle())
        ready(vm)
        assertEquals(HomeTab.HOME, vm.stateFlow.value.tab)
        assertEquals(1, fixture.statsRequests().size)
        vm.selectTab(HomeTab.STATS)
        val request = fixture.statsRequests().single()
        assertEquals("Europe/Prague", request.url.queryParameter("timeZone"))
        assertNull(request.url.queryParameter("weekStart"))
        assertEquals("2026-10-12", content(vm).weekStart)
        assertEquals("2026-10-12", content(vm).currentWeekStart)
        vm.selectTab(HomeTab.HOME)
        vm.selectTab(HomeTab.STATS)
        assertEquals(1, fixture.statsRequests().size)
    }

    @Test fun chartWeekChangesKeepCurrentSummaryAndReadinessAndCannotGoIntoTheFuture() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture)
        ready(vm)
        vm.changeStatsWeek(1)
        assertEquals(1, fixture.statsRequests().size)
        val original = content(vm).response
        vm.changeStatsWeek(-1)
        assertTrue(content(vm).isChartLoading)
        assertEquals(original.summary, content(vm).response.summary)
        assertEquals(original.readiness, content(vm).response.readiness)
        ready(vm)
        assertEquals("2026-10-05", fixture.statsRequests().last().url.queryParameter("weekStart"))
        assertEquals(original.summary, content(vm).response.summary)
        assertEquals(original.readiness, content(vm).response.readiness)
        assertTrue(content(vm).canGoToNextWeek)
        vm.changeStatsWeek(1)
        ready(vm)
        assertFalse(content(vm).canGoToNextWeek)
        assertEquals("2026-10-12", content(vm).weekStart)
    }

    @Test fun initialErrorsRetryAndWeekErrorsOnlyReplaceTheChart() = runTest(dispatcher) {
        val fixture = Fixture().apply { status = 500 }
        val vm = viewModel(fixture)
        vm.stateFlow.first { it.statistics is ContentViewState.Failed }
        fixture.status = 200
        vm.loadStats()
        ready(vm)
        val summary = content(vm).response.summary
        fixture.status = 500
        vm.changeStatsWeek(-1)
        vm.stateFlow.first { (it.statistics as? ContentViewState.Ready)?.content?.chartError != null }
        assertEquals(summary, content(vm).response.summary)
        assertFalse(content(vm).isChartLoading)
        assertEquals("Stats failed", content(vm).chartError)
        fixture.status = 200
        vm.loadStats()
        ready(vm)
        assertEquals("2026-10-05", content(vm).weekStart)
    }

    @Test fun lateOldWeekResponsesCannotOverwriteTheNewlySelectedWeek() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            beforeStats = { request ->
                if (request.url.queryParameter("weekStart") == "2026-10-05") {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                    finished.complete(Unit)
                }
            }
        }
        val vm = viewModel(fixture)
        ready(vm)
        try {
            vm.changeStatsWeek(-1)
            started.await()
            vm.changeStatsWeek(-1)
            ready(vm)
            assertEquals("2026-09-28", content(vm).weekStart)
        } finally { release.countDown() }
        finished.await()
        runCurrent()
        assertEquals("2026-09-28", content(vm).weekStart)
        assertEquals("2026-09-28", content(vm).response.studyProgress.first().date)
    }

    @Test fun aValidSelectedWeekIsRestoredAfterRecreation() = runTest(dispatcher) {
        val fixture = Fixture()
        val handle = SavedStateHandle(mapOf("homeTab" to "STATS", "statsWeekStart" to "2000-01-03"))
        val vm = viewModel(fixture, handle)
        ready(vm)
        assertEquals("2000-01-03", fixture.statsRequests().single().url.queryParameter("weekStart"))
        vm.changeStatsWeek(1)
        ready(vm)
        assertEquals("2000-01-10", handle.get<String>("statsWeekStart"))
    }

    @Test fun anInvalidRestoredWeekFallsBackToTheServerDefaultWeek() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture, SavedStateHandle(mapOf("homeTab" to "STATS", "statsWeekStart" to "bad-date")))
        ready(vm)
        assertNull(fixture.statsRequests().single().url.queryParameter("weekStart"))
        assertEquals("2026-10-12", content(vm).weekStart)
    }

    @Test fun statsHttpAndNetworkErrorsUseTheExistingErrorMapping() = runBlocking {
        assertEquals(ContentResult.Error("Stats failed"),
            Fixture().apply { status = 500 }.stats(StatsRequest()))
        assertEquals(ContentResult.Error("Something unexpected happened"),
            Fixture().apply { offline = true }.stats(StatsRequest()))
    }

    @Test fun unauthorizedStatsAutomaticallyRefreshAndRetryWithTheSameWeekAndZone() = runBlocking {
        val fixture = Fixture().apply { expireAccess = true }
        assertTrue(fixture.stats(StatsRequest("Europe/Prague", "2026-10-05")) is ContentResult.Success)
        assertEquals(listOf("Bearer access", "Bearer new-access"),
            fixture.statsRequests().map { it.header("Authorization") })
        fixture.statsRequests().forEach {
            assertEquals("Europe/Prague", it.url.queryParameter("timeZone"))
            assertEquals("2026-10-05", it.url.queryParameter("weekStart"))
        }
        assertEquals(1, fixture.requests.count { it.url.encodedPath == "/auth/refresh" })
    }

    @Test fun aRejectedRefreshNavigatesToAuthWithoutRetryingIndefinitely() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture)
        ready(vm)
        fixture.expireAccess = true
        fixture.refreshStatus = 401
        vm.changeStatsWeek(-1)
        vm.stateFlow.first { it.sessionExpired }
        assertSame(ContentViewState.NotLoaded, vm.stateFlow.value.statistics)
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
        assertEquals(1, fixture.requests.count { it.url.encodedPath == "/auth/refresh" })
    }
}
