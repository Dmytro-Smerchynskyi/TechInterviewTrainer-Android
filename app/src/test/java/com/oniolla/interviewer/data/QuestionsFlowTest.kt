package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.GetFavoriteQuestionsUseCase
import com.oniolla.interviewer.domain.GetQuestionsUseCase
import com.oniolla.interviewer.domain.SetQuestionSavedUseCase
import com.oniolla.interviewer.domain.GetTopicsUseCase
import com.oniolla.interviewer.domain.TopicCatalogUseCase
import com.oniolla.interviewer.domain.QuestionsRequest
import com.oniolla.interviewer.ui.home.ContentViewState
import com.oniolla.interviewer.ui.home.HomeTab
import com.oniolla.interviewer.ui.home.HomeViewModel
import com.oniolla.interviewer.ui.home.QuestionsContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionsFlowTest {
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
        var initialStatus = 200
        var nextStatus = 200
        var offline = false
        var initialBody = page(listOf(1, 2), "cursor+/= next")
        var nextBody = page(listOf(2, 3), null)
        var responder: ((Request) -> String)? = null
        val repository: HomeRepository
        val getQuestions: GetQuestionsUseCase
        private val auth: AuthRepository

        init {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                requests.add(request)
                val path = request.url.encodedPath
                val status = if (path == "/questions") {
                    if (offline) throw IOException("Offline")
                    if (request.url.queryParameter("cursor") == null) initialStatus else nextStatus
                } else 200
                val body = if (status >= 400) {
                    """{"error":{"code":"FAILED","message":"Server message"}}"""
                } else when (path) {
                    "/questions" -> responder?.invoke(request)
                        ?: if (request.url.queryParameter("cursor") == null) initialBody else nextBody
                    "/dashboard" -> """{"readiness":{"score":0,"answered":0,"practicedTopics":0,"totalTopics":0,
                        "gradePerformance":[]},"todayReview":[],"weakTopics":[],"dueCount":0}"""
                    "/topics" -> "[]"
                    "/me" -> """{"id":"user","email":"developer@example.com","displayName":"Dmitry",
                        "emailVerified":true,"specialization":"ANDROID","createdAt":"","updatedAt":"",
                        "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"en"}"""
                    else -> error("Unexpected endpoint $path")
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(status).message("Test response")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
            val api = Retrofit.Builder().baseUrl("https://api.example.test/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            val sessionRefresh = SessionRefreshCoordinator(tokens, api)
            repository = HomeRepository(api, MemoryTopicCache(), sessionRefresh)
            getQuestions = GetQuestionsUseCase(repository)
            auth = AuthRepository(tokens, api, sessionRefresh)
        }

        fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
            HomeViewModel(GetDashboardUseCase(repository, auth), GetTopicsUseCase(repository), getQuestions, handle, TopicCatalogUseCase(repository), SetQuestionSavedUseCase(repository), GetFavoriteQuestionsUseCase(repository))
        fun questionRequests() = requests.filter { it.url.encodedPath == "/questions" }

        companion object {
            fun page(ids: List<Int>, cursor: String?, locked: Int = 0): String {
                val items = ids.joinToString(",") { id -> """
                    {"id":$id,"question":"Question $id","specialization":"ANDROID",
                     "topic":{"id":7,"slug":"flow","name":"Flow","visual":{"iconUrl":"/assets/topics/flow.svg",
                        "iconVersion":2,"darkColor":"#0A3C23","lightColor":"#2E92D1"}},
                     "difficulty":"MEDIUM","minGrade":"JUNIOR","maxGrade":"SENIOR","tags":["flow"],"saved":true,
                     "progress":{"questionId":$id,"attempts":2,"lastScore":80,"masteryScore":75,
                        "lastReviewedAt":"2026-10-12T19:22:47Z","nextReviewAt":"2026-10-13T19:22:47Z",
                        "mastered":false,"updatedAt":"2026-10-12T19:22:47Z"},
                     "updatedAt":"2026-10-12T19:22:47Z","sortOrder":$id,"language":"en","requestedLanguage":"en"}
                """ }
                val access = """{"topicId":7,"totalCount":${ids.size + locked},"visibleCount":${ids.size},
                    "lockedCount":$locked,"limitedByPlan":${locked > 0}}"""
                val cursorJson = cursor?.let { "\"$it\"" } ?: "null"
                return """{"items":[$items],"nextCursor":$cursorJson,"topics":[$access],"access":$access}"""
            }
        }
    }

    private fun content(vm: HomeViewModel) =
        (vm.stateFlow.value.questions as ContentViewState.Ready<QuestionsContent>).content

    @Test fun questionsUseGetWithTwentyItemsAndLeaveOptionalFiltersOut() = runBlocking {
        val fixture = Fixture()
        fixture.getQuestions(QuestionsRequest())
        val request = fixture.questionRequests().single()
        assertEquals("GET", request.method)
        assertNull(request.body)
        assertEquals(setOf("limit"), request.url.queryParameterNames)
        assertEquals("20", request.url.queryParameter("limit"))
    }

    @Test fun topicSearchAndOpaqueCursorAreEncodedWithoutChangingTheirValues() = runBlocking {
        val fixture = Fixture()
        fixture.getQuestions(QuestionsRequest(7, "  flow & inline + ?  ", "cursor+/= next"))
        val url = fixture.questionRequests().single().url
        assertEquals(setOf("topicId", "search", "limit", "cursor"), url.queryParameterNames)
        assertEquals("7", url.queryParameter("topicId"))
        assertEquals("flow & inline + ?", url.queryParameter("search"))
        assertEquals("cursor+/= next", url.queryParameter("cursor"))
    }

    @Test fun nestedQuestionsAndAccessCountsAreDecoded() = runBlocking {
        val fixture = Fixture().apply { initialBody = Fixture.page(listOf(1), "opaque", locked = 40) }
        val response = (fixture.getQuestions(QuestionsRequest()) as ContentResult.Success).content
        assertEquals("opaque", response.nextCursor)
        val question = response.items.single()
        assertEquals(7L, question.topic.id)
        assertEquals("/assets/topics/flow.svg", question.topic.visual!!.iconUrl)
        assertEquals("#0A3C23", question.topic.visual!!.darkColor)
        assertEquals("#2E92D1", question.topic.visual!!.lightColor)
        assertTrue(question.saved)
        assertEquals(75.0, question.progress!!.masteryScore, 0.0)
        assertEquals(listOf("flow"), question.tags)
        assertEquals(40, response.access!!.lockedCount)
        assertTrue(response.access!!.limitedByPlan)
        assertEquals(response.access, response.topics.single())
    }

    @Test fun httpAndNetworkErrorsUseTheExistingContentErrorMapping() = runBlocking {
        assertEquals(ContentResult.Error("Server message"),
            Fixture().apply { initialStatus = 500 }.getQuestions(QuestionsRequest()))
        assertEquals(ContentResult.Error("Something unexpected happened"),
            Fixture().apply { offline = true }.getQuestions(QuestionsRequest()))
        assertSame(ContentResult.SessionExpired,
            Fixture().apply { initialStatus = 401 }.getQuestions(QuestionsRequest()))
    }

    @Test fun questionsLoadLazilyAndKeepTheirPageWhenSwitchingTabs() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        assertTrue(fixture.questionRequests().isEmpty())
        vm.selectTab(HomeTab.QUESTIONS)
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.selectTab(HomeTab.HOME)
        vm.selectTab(HomeTab.QUESTIONS)
        assertEquals(1, fixture.questionRequests().size)
        assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })
    }

    @Test fun topicAndSearchStayIndependentAndClearingTopicKeepsSearch() = runTest(dispatcher) {
        val fixture = Fixture()
        val handle = SavedStateHandle()
        val vm = fixture.viewModel(handle)
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        vm.selectQuestionTopic(7, "Coroutines")
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        assertEquals("", vm.stateFlow.value.questionSearch)
        assertEquals("7", fixture.questionRequests().last().url.queryParameter("topicId"))
        vm.updateQuestionSearch("flow")
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        assertEquals("Coroutines", vm.stateFlow.value.questionTopic!!.name)
        assertEquals("flow", fixture.questionRequests().last().url.queryParameter("search"))
        assertEquals("7", fixture.questionRequests().last().url.queryParameter("topicId"))
        vm.clearQuestionTopic()
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        assertNull(vm.stateFlow.value.questionTopic)
        assertNull(handle.get<Long>("questionTopicId"))
        assertEquals("flow", vm.stateFlow.value.questionSearch)
        val request = fixture.questionRequests().last()
        assertNull(request.url.queryParameter("topicId"))
        assertNull(request.url.queryParameter("cursor"))
        assertEquals("flow", request.url.queryParameter("search"))
    }

    @Test fun weakTopicSearchDoesNotChangeTheSelectedTopicCaption() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel()
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready }
        vm.selectQuestionTopic(7, "Coroutines")
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.openTopicQuestions("Flow")
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        assertEquals("Coroutines", vm.stateFlow.value.questionTopic!!.name)
        assertEquals(7L, vm.stateFlow.value.questionTopic!!.id)
        assertEquals("Flow", vm.stateFlow.value.questionSearch)
        assertEquals("7", fixture.questionRequests().last().url.queryParameter("topicId"))
        assertEquals("Flow", fixture.questionRequests().last().url.queryParameter("search"))
    }

    @Test fun freshResultsResetScrollRevisionWhilePaginationPreservesIt() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS")))
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        val firstRevision = content(vm).resultRevision
        vm.loadMoreQuestions()
        vm.stateFlow.first { (it.questions as? ContentViewState.Ready)?.content?.items?.size == 3 }
        assertEquals(firstRevision, content(vm).resultRevision)

        vm.updateQuestionSearch("flow")
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        val searchRevision = content(vm).resultRevision
        assertTrue(searchRevision > firstRevision)
        assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })

        vm.selectQuestionTopic(7, "Flow")
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        val topicRevision = content(vm).resultRevision
        assertTrue(topicRevision > searchRevision)

        vm.clearQuestionTopic()
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        val clearedTopicRevision = content(vm).resultRevision
        assertTrue(clearedTopicRevision > topicRevision)

        vm.updateQuestionSearch("")
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        val clearedSearchRevision = content(vm).resultRevision
        assertTrue(clearedSearchRevision > clearedTopicRevision)

        vm.loadQuestions()
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        assertTrue(content(vm).resultRevision > clearedSearchRevision)
    }

    @Test fun paginationKeepsFiltersDeduplicatesItemsAndStopsWithoutCursor() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel(SavedStateHandle(mapOf(
            "homeTab" to "QUESTIONS", "questionSearch" to "flow",
            "questionTopicId" to 7L, "questionTopicName" to "Flow")))
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        repeat(4) { vm.loadMoreQuestions() }
        vm.stateFlow.first { (it.questions as? ContentViewState.Ready)?.content?.items?.size == 3 }
        assertEquals(listOf(1L, 2L, 3L), content(vm).items.map { it.id })
        assertNull(content(vm).nextCursor)
        val requests = fixture.questionRequests()
        assertEquals(2, requests.size)
        assertNull(requests.first().url.queryParameter("cursor"))
        assertEquals("cursor+/= next", requests.last().url.queryParameter("cursor"))
        requests.forEach {
            assertEquals("7", it.url.queryParameter("topicId"))
            assertEquals("flow", it.url.queryParameter("search"))
            assertEquals("20", it.url.queryParameter("limit"))
            assertNull(it.url.queryParameter("difficulty"))
            assertNull(it.url.queryParameter("grade"))
            assertNull(it.url.queryParameter("saved"))
        }
        vm.loadMoreQuestions()
        assertEquals(2, fixture.questionRequests().size)
    }

    @Test fun nextPageErrorKeepsItemsAndRetriesTheSameCursor() = runTest(dispatcher) {
        val fixture = Fixture().apply { nextStatus = 500 }
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS")))
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.loadMoreQuestions()
        vm.stateFlow.first { (it.questions as? ContentViewState.Ready)?.content?.appendError != null }
        assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })
        assertEquals("Server message", content(vm).appendError)
        vm.loadMoreQuestions()
        assertEquals(2, fixture.questionRequests().size)
        fixture.nextStatus = 200
        vm.retryQuestionPage()
        vm.stateFlow.first { (it.questions as? ContentViewState.Ready)?.content?.items?.size == 3 }
        assertNull(content(vm).appendError)
        assertEquals(listOf("cursor+/= next", "cursor+/= next"),
            fixture.questionRequests().drop(1).map { it.url.queryParameter("cursor") })
    }

    @Test fun changingSearchResetsTheCursorAndDebouncesRapidTyping() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS")))
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.loadMoreQuestions()
        vm.stateFlow.first { (it.questions as? ContentViewState.Ready)?.content?.items?.size == 3 }
        val previousResults = vm.stateFlow.value.questions
        vm.updateQuestionSearch("f")
        assertTrue(vm.stateFlow.value.isQuestionSearchPending)
        assertEquals(previousResults, vm.stateFlow.value.questions)
        advanceTimeBy(100)
        vm.updateQuestionSearch("fl")
        advanceTimeBy(100)
        vm.updateQuestionSearch("flow")
        advanceTimeBy(499)
        assertTrue(vm.stateFlow.value.isQuestionSearchPending)
        assertEquals(previousResults, vm.stateFlow.value.questions)
        assertEquals(2, fixture.questionRequests().size)
        advanceTimeBy(1)
        runCurrent()
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        assertEquals(3, fixture.questionRequests().size)
        assertNull(fixture.questionRequests().last().url.queryParameter("cursor"))
        assertEquals("flow", fixture.questionRequests().last().url.queryParameter("search"))
        assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })
    }

    @Test fun clearingSearchCancelsThePendingDebounceAndReloadsImmediately() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS")))
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.updateQuestionSearch("flow")
        advanceTimeBy(100)
        vm.loadMoreQuestions()
        assertEquals(1, fixture.questionRequests().size)
        vm.updateQuestionSearch("")
        assertFalse(vm.stateFlow.value.isQuestionSearchPending)
        assertSame(ContentViewState.Loading, vm.stateFlow.value.questions)
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, fixture.questionRequests().size)
        assertNull(fixture.questionRequests().last().url.queryParameter("search"))
        assertNull(fixture.questionRequests().last().url.queryParameter("cursor"))
    }

    @Test fun selectingTopicDuringDebounceImmediatelyUsesTheLatestSearchOnce() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS")))
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.updateQuestionSearch("flow")
        advanceTimeBy(100)
        vm.selectQuestionTopic(7, "Flow")
        assertFalse(vm.stateFlow.value.isQuestionSearchPending)
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, fixture.questionRequests().size)
        assertEquals("flow", fixture.questionRequests().last().url.queryParameter("search"))
        assertEquals("7", fixture.questionRequests().last().url.queryParameter("topicId"))
    }

    @Test fun repeatedServerCursorCannotCauseAnEndlessPaginationLoop() = runTest(dispatcher) {
        val fixture = Fixture().apply { nextBody = Fixture.page(listOf(3), "cursor+/= next") }
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS")))
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.loadMoreQuestions()
        vm.stateFlow.first { (it.questions as? ContentViewState.Ready)?.content?.items?.size == 3 }
        assertNull(content(vm).nextCursor)
        vm.loadMoreQuestions()
        assertEquals(2, fixture.questionRequests().size)
    }

    @Test fun restoredTopicAndSearchAreSentTogetherOnFirstPage() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS",
            "questionTopicId" to 7L, "questionTopicName" to "Coroutines", "questionSearch" to "flow")))
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        assertEquals(7L, vm.stateFlow.value.questionTopic!!.id)
        assertEquals("Coroutines", vm.stateFlow.value.questionTopic!!.name)
        val request = fixture.questionRequests().single()
        assertEquals("7", request.url.queryParameter("topicId"))
        assertEquals("flow", request.url.queryParameter("search"))
        assertNull(request.url.queryParameter("cursor"))
    }

    @Test fun lateResponseFromAnOldSearchCannotReplaceNewResults() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            responder = { request ->
                if (request.url.queryParameter("search") == "old") {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                    finished.complete(Unit)
                    Fixture.page(listOf(99), null)
                } else Fixture.page(listOf(1), null)
            }
        }
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS", "questionSearch" to "old")))
        try {
            started.await()
            vm.updateQuestionSearch("new")
            vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending && it.dashboard is ContentViewState.Ready }
            assertEquals(listOf(1L), content(vm).items.map { it.id })
        } finally { release.countDown() }
        finished.await()
        runCurrent()
        assertEquals("new", vm.stateFlow.value.questionSearch)
        assertEquals(listOf(1L), content(vm).items.map { it.id })
    }

    @Test fun changingTopicWhileAnAppendIsRunningDiscardsTheOldPage() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            responder = { request ->
                when {
                    request.url.queryParameter("cursor") != null -> {
                        started.complete(Unit)
                        check(release.await(5, TimeUnit.SECONDS))
                        finished.complete(Unit)
                        Fixture.page(listOf(99), null)
                    }
                    request.url.queryParameter("topicId") == "8" -> Fixture.page(listOf(8), null)
                    else -> Fixture.page(listOf(1), "old_cursor")
                }
            }
        }
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS")))
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.loadMoreQuestions()
        try {
            started.await()
            vm.selectQuestionTopic(8, "Kotlin")
            vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
            assertEquals(listOf(8L), content(vm).items.map { it.id })
            assertNull(fixture.questionRequests().last().url.queryParameter("cursor"))
        } finally { release.countDown() }
        finished.await()
        runCurrent()
        assertEquals("Kotlin", vm.stateFlow.value.questionTopic!!.name)
        assertEquals(listOf(8L), content(vm).items.map { it.id })
    }

    @Test fun clearingSearchImmediatelyRemovesSearchAndCursorWhileKeepingTopic() = runTest(dispatcher) {
        val fixture = Fixture()
        val handle = SavedStateHandle(mapOf("homeTab" to "QUESTIONS", "questionSearch" to "flow",
            "questionTopicId" to 7L, "questionTopicName" to "Flow"))
        val vm = fixture.viewModel(handle)
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.loadMoreQuestions()
        vm.stateFlow.first { (it.questions as? ContentViewState.Ready)?.content?.items?.size == 3 }
        vm.updateQuestionSearch("")
        runCurrent()
        vm.stateFlow.first { it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        assertEquals("", vm.stateFlow.value.questionSearch)
        assertEquals("", handle.get<String>("questionSearch"))
        val url = fixture.questionRequests().last().url
        assertNull(url.queryParameter("search"))
        assertNull(url.queryParameter("cursor"))
        assertEquals("7", url.queryParameter("topicId"))
    }

    @Test fun anExpiredSessionDuringPaginationTriggersAuthNavigation() = runTest(dispatcher) {
        val fixture = Fixture().apply { nextStatus = 401 }
        val vm = fixture.viewModel(SavedStateHandle(mapOf("homeTab" to "QUESTIONS")))
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.questions is ContentViewState.Ready && !it.isQuestionSearchPending }
        vm.loadMoreQuestions()
        vm.stateFlow.first { it.sessionExpired }
        assertSame(ContentViewState.NotLoaded, vm.stateFlow.value.questions)
    }
}
