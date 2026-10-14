package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetDashboardUseCase
import com.oniolla.interviewer.domain.GetStatsUseCase
import com.oniolla.interviewer.domain.GetFavoriteQuestionsUseCase
import com.oniolla.interviewer.domain.GetQuestionsUseCase
import com.oniolla.interviewer.domain.GetTopicsUseCase
import com.oniolla.interviewer.domain.QuestionSavedRequest
import com.oniolla.interviewer.domain.SetQuestionSavedUseCase
import com.oniolla.interviewer.domain.TopicCatalogUseCase
import com.oniolla.interviewer.ui.UiEvent
import com.oniolla.interviewer.ui.home.ContentViewState
import com.oniolla.interviewer.ui.home.HomeTab
import com.oniolla.interviewer.ui.home.HomeViewModel
import com.oniolla.interviewer.ui.home.QuestionsContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class SavedQuestionsFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        stores.forEach { it.clear() }
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
        val completedMutations = Channel<Request>(Channel.UNLIMITED)
        val serverSaved = ConcurrentHashMap<Long, Boolean>()
        var mutationStatus = 204
        var offline = false
        var expireAccess = false
        var refreshStatus = 200
        var beforeMutation: ((Request) -> Unit)? = null
        val repository: HomeRepository
        val auth: AuthRepository
        val setSaved: SetQuestionSavedUseCase

        init {
            val origin = "https://api.example.test/"
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, origin))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests.add(request)
                    val path = request.url.encodedPath
                    val mutation = request.url.pathSegments.let {
                        it.size == 3 && it[0] == "questions" && it[2] == "saved"
                    }
                    val status = when {
                        mutation && expireAccess && request.header("Authorization") == "Bearer access" -> 401
                        mutation -> mutationStatus
                        path == "/auth/refresh" -> refreshStatus
                        else -> 200
                    }
                    if (mutation) {
                        beforeMutation?.invoke(request)
                        if (offline) throw IOException("Offline")
                        if (status == 204) {
                            serverSaved[request.url.pathSegments[1].toLong()] = request.method == "PUT"
                        }
                        completedMutations.trySend(request)
                    }
                    val body = if (status >= 400) {
                        val code = if (status == 401) "UNAUTHORIZED" else "FAILED"
                        """{"error":{"code":"$code","message":"Saved request failed"}}"""
                    } else when {
                        mutation -> ""
                        path == "/questions" -> {
                            val next = request.url.queryParameter("cursor") == null
                            val items = (if (next) listOf(1L, 2L) else listOf(2L, 3L)).joinToString(",") { id ->
                                """{"id":$id,"question":"Question $id","specialization":"ANDROID",
                                "topic":{"id":7,"slug":"flow","name":"Flow"},"difficulty":"EASY",
                                "minGrade":"JUNIOR","maxGrade":"MIDDLE","tags":[],"saved":${serverSaved[id] == true}}"""
                            }
                            val cursor = if (next) "\"next\"" else "null"
                            """{"items":[$items],"nextCursor":$cursor,"topics":[]}"""
                        }
                        path == "/dashboard" -> """{"readiness":{"score":0,"answered":0,"practicedTopics":0,
                            "totalTopics":0,"gradePerformance":[]},"todayReview":[],"weakTopics":[],"dueCount":0}"""
                        path == "/stats" -> """{"timeZone":"UTC","serverTime":"2026-10-14T12:00:00.000Z","weekStart":"2026-10-12","weekEnd":"2026-10-18","summary":{"answered":0,"streakDays":0,"dueCount":0,"averageStudySeconds":null},"readiness":{"score":0,"gradePerformance":[]},"studyProgress":[],"topics":[],"weakTopics":[],"weakKeyPoints":[]}"""
                        path == "/topics" -> "[]"
                        path == "/me" -> """{"id":"user","email":"developer@example.com","displayName":"Dmitry",
                            "emailVerified":true,"specialization":"ANDROID","createdAt":"","updatedAt":"",
                            "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"en"}"""
                        path == "/auth/refresh" -> """{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900}"""
                        else -> error("Unexpected endpoint $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(status).message("Test response")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            val api = Retrofit.Builder().baseUrl(origin).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            val coordinator = SessionRefreshCoordinator(tokens, api)
            repository = HomeRepository(api, MemoryTopicCache(), coordinator)
            auth = AuthRepository(tokens, api, coordinator)
            setSaved = SetQuestionSavedUseCase(repository)
        }

        fun mutations() = requests.filter { it.url.encodedPath.endsWith("/saved") }
    }

    private suspend fun viewModel(fixture: Fixture): HomeViewModel {
        val vm = HomeViewModel(
            GetDashboardUseCase(fixture.repository, fixture.auth),
            GetTopicsUseCase(fixture.repository),
            GetQuestionsUseCase(fixture.repository),
            SavedStateHandle(mapOf("homeTab" to "QUESTIONS")),
            TopicCatalogUseCase(fixture.repository),
            fixture.setSaved,
            GetFavoriteQuestionsUseCase(fixture.repository),
            GetStatsUseCase(fixture.repository)
        )
        stores += ViewModelStore().apply { put("home", vm) }
        vm.stateFlow.first {
            it.questions is ContentViewState.Ready && it.dashboard is ContentViewState.Ready &&
                it.topicCatalog is ContentViewState.Ready
        }
        return vm
    }

    private fun content(vm: HomeViewModel) =
        (vm.stateFlow.value.questions as ContentViewState.Ready<QuestionsContent>).content

    private fun saved(vm: HomeViewModel, id: Long = 1) = content(vm).items.first { it.id == id }.saved

    @Test fun putAndDeleteUseTheQuestionIdAndAcceptNoContentWithoutAPayload() = runBlocking {
        val fixture = Fixture()
        assertEquals(ContentResult.Success(Unit), fixture.setSaved(QuestionSavedRequest(42, true)))
        assertEquals(ContentResult.Success(Unit), fixture.setSaved(QuestionSavedRequest(42, false)))
        val requests = fixture.mutations()
        assertEquals(listOf("PUT", "DELETE"), requests.map { it.method })
        requests.forEach {
            assertEquals("/questions/42/saved", it.url.encodedPath)
            assertNull(it.url.query)
            assertEquals(0L, it.body?.contentLength() ?: 0L)
            assertEquals("Bearer access", it.header("Authorization"))
        }
        assertFalse(fixture.serverSaved.getValue(42))
    }

    @Test fun starChangesImmediatelyButTheRequestWaitsOneSecond() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture)
        val revision = content(vm).resultRevision
        vm.toggleQuestionSaved(1)
        assertTrue(saved(vm))
        assertEquals(revision, content(vm).resultRevision)
        runCurrent()
        advanceTimeBy(999)
        runCurrent()
        assertTrue(fixture.mutations().isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals("PUT", fixture.completedMutations.receive().method)
        assertTrue(saved(vm))
        assertTrue(fixture.serverSaved.getValue(1))
    }

    @Test fun rapidTapsSendOnlyTheLastChoiceAfterItsDebounce() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture)
        vm.toggleQuestionSaved(1)
        advanceTimeBy(400)
        vm.toggleQuestionSaved(1)
        assertFalse(saved(vm))
        advanceTimeBy(400)
        vm.toggleQuestionSaved(1)
        assertTrue(saved(vm))
        advanceTimeBy(999)
        runCurrent()
        assertTrue(fixture.mutations().isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals("PUT", fixture.completedMutations.receive().method)
        assertEquals(1, fixture.mutations().size)
    }

    @Test fun twoTapsSendDeleteAndKeepTheStarEmpty() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture)
        vm.toggleQuestionSaved(1)
        advanceTimeBy(300)
        vm.toggleQuestionSaved(1)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals("DELETE", fixture.completedMutations.receive().method)
        assertFalse(saved(vm))
        assertEquals(1, fixture.mutations().size)
    }

    @Test fun differentQuestionsHaveIndependentDebounceTimers() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture)
        vm.toggleQuestionSaved(1)
        vm.toggleQuestionSaved(2)
        advanceTimeBy(500)
        vm.toggleQuestionSaved(1)
        advanceTimeBy(500)
        runCurrent()
        assertEquals("/questions/2/saved", fixture.completedMutations.receive().url.encodedPath)
        assertEquals(1, fixture.mutations().size)
        advanceTimeBy(500)
        runCurrent()
        assertEquals("/questions/1/saved", fixture.completedMutations.receive().url.encodedPath)
        assertEquals(listOf("PUT", "DELETE"), fixture.mutations().map { it.method })
        assertTrue(saved(vm, 2))
        assertFalse(saved(vm, 1))
    }

    @Test fun failedSaveClearsTheStarAndKeepsTheListWithAnErrorToast() = runTest(dispatcher) {
        val fixture = Fixture().apply { mutationStatus = 500 }
        val vm = viewModel(fixture)
        val event = async { vm.uiEventsFlow.first() }
        runCurrent()
        vm.toggleQuestionSaved(1)
        assertTrue(saved(vm))
        advanceTimeBy(1000)
        assertEquals(UiEvent.Toast("Saved request failed"), event.await())
        assertFalse(saved(vm))
        assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })
        assertFalse(vm.stateFlow.value.sessionExpired)
    }

    @Test fun failedDeleteAlsoLeavesTheStarEmptyInsteadOfRestoringSaved() = runTest(dispatcher) {
        val fixture = Fixture().apply { serverSaved[1] = true; mutationStatus = 500 }
        val vm = viewModel(fixture)
        assertTrue(saved(vm))
        val event = async { vm.uiEventsFlow.first() }
        runCurrent()
        vm.toggleQuestionSaved(1)
        assertFalse(saved(vm))
        advanceTimeBy(1000)
        assertEquals(UiEvent.Toast("Saved request failed"), event.await())
        assertEquals("DELETE", fixture.mutations().single().method)
        assertFalse(saved(vm))
        assertTrue(fixture.serverSaved.getValue(1))
    }

    @Test fun networkFailureClearsTheOptimisticStar() = runTest(dispatcher) {
        val fixture = Fixture().apply { offline = true }
        val vm = viewModel(fixture)
        val event = async { vm.uiEventsFlow.first() }
        runCurrent()
        vm.toggleQuestionSaved(1)
        advanceTimeBy(1000)
        assertEquals(UiEvent.Toast("Something unexpected happened"), event.await())
        assertFalse(saved(vm))
    }

    @Test fun reloadedPagesAndPaginationDoNotOverwritePendingStarChangesOrResetScroll() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture)
        vm.toggleQuestionSaved(2)
        val revision = content(vm).resultRevision
        vm.loadMoreQuestions()
        vm.stateFlow.first { (it.questions as? ContentViewState.Ready)?.content?.items?.size == 3 }
        assertTrue(saved(vm, 2))
        assertEquals(revision, content(vm).resultRevision)
        vm.loadQuestions()
        vm.stateFlow.first { it.questions is ContentViewState.Ready }
        assertTrue(saved(vm, 2))
        vm.selectTab(HomeTab.HOME)
        vm.selectTab(HomeTab.QUESTIONS)
        advanceTimeBy(1000)
        assertEquals("/questions/2/saved", fixture.completedMutations.receive().url.encodedPath)
        assertEquals(1, fixture.mutations().size)
    }

    @Test fun aSlowSaveFinishesBeforeANewerDeleteIsSent() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            beforeMutation = { request ->
                if (request.method == "PUT") {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                }
            }
        }
        val vm = viewModel(fixture)
        try {
            vm.toggleQuestionSaved(1)
            advanceTimeBy(1000)
            started.await()
            vm.toggleQuestionSaved(1)
            assertFalse(saved(vm))
            advanceTimeBy(1000)
            runCurrent()
            assertEquals(listOf("PUT"), fixture.mutations().map { it.method })
        } finally { release.countDown() }
        assertEquals("PUT", fixture.completedMutations.receive().method)
        assertEquals("DELETE", fixture.completedMutations.receive().method)
        assertFalse(fixture.serverSaved.getValue(1))
        assertFalse(saved(vm))
    }

    @Test fun anOlderFailedRequestCannotClearANewerSavedChoice() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            mutationStatus = 500
            beforeMutation = {
                if (mutations().size == 1) {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                }
            }
        }
        val vm = viewModel(fixture)
        try {
            vm.toggleQuestionSaved(1)
            advanceTimeBy(1000)
            started.await()
            vm.toggleQuestionSaved(1)
            vm.toggleQuestionSaved(1)
            assertTrue(saved(vm))
            advanceTimeBy(1000)
            runCurrent()
            fixture.mutationStatus = 204
        } finally { release.countDown() }
        fixture.completedMutations.receive()
        fixture.completedMutations.receive()
        assertEquals(listOf("PUT", "PUT"), fixture.mutations().map { it.method })
        assertTrue(saved(vm))
        assertTrue(fixture.serverSaved.getValue(1))
    }

    @Test fun unauthorizedSaveRefreshesAndRetriesAutomatically() = runBlocking {
        val fixture = Fixture().apply { expireAccess = true }
        assertEquals(ContentResult.Success(Unit), fixture.setSaved(QuestionSavedRequest(1, true)))
        assertEquals(listOf("Bearer access", "Bearer new-access"),
            fixture.mutations().map { it.header("Authorization") })
        assertEquals(1, fixture.requests.count { it.url.encodedPath == "/auth/refresh" })
        assertTrue(fixture.serverSaved.getValue(1))
    }

    @Test fun expiredRefreshClearsTheStarAndNavigatesToAuthWithoutLooping() = runTest(dispatcher) {
        val fixture = Fixture().apply { expireAccess = true; refreshStatus = 401 }
        val vm = viewModel(fixture)
        vm.toggleQuestionSaved(1)
        advanceTimeBy(1000)
        vm.stateFlow.first { it.sessionExpired }
        runCurrent()
        assertFalse(saved(vm))
        assertNull(fixture.tokens.accessToken)
        assertNull(fixture.tokens.refreshToken)
        assertEquals(1, fixture.mutations().size)
        assertEquals(1, fixture.requests.count { it.url.encodedPath == "/auth/refresh" })
        vm.toggleQuestionSaved(1)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(1, fixture.mutations().size)
    }
}
