package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.UiEvent
import com.oniolla.interviewer.ui.home.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesFlowTest {
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
        var firstStatus = 200
        var nextStatus = 200
        var mutationStatus = 204
        var offline = false
        var firstBody = page(listOf(1, 2), "opaque+/= cursor")
        var nextBody = page(listOf(2, 3), null)
        var responder: ((Request) -> String)? = null
        var beforeMutation: (() -> Unit)? = null
        val repository: HomeRepository
        val auth: AuthRepository
        val favorites: GetFavoriteQuestionsUseCase

        init {
            val origin = "https://api.example.test/"
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, origin))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests.add(request)
                    val path = request.url.encodedPath
                    val list = path in listOf("/questions/saved", "/reviews/due", "/questions")
                    val mutation = request.method in listOf("PUT", "DELETE")
                    if (list && offline) throw IOException("Offline")
                    val status = when {
                        mutation -> mutationStatus
                        list && request.url.queryParameter("cursor") != null -> nextStatus
                        list -> firstStatus
                        else -> 200
                    }
                    val body = if (status >= 400) {
                        """{"error":{"code":"FAILED","message":"Server message"}}"""
                    } else when {
                        mutation -> { beforeMutation?.invoke(); "" }
                        list -> responder?.invoke(request) ?: if (request.url.queryParameter("cursor") == null)
                            firstBody else nextBody
                        path == "/stats" -> """{"timeZone":"UTC","serverTime":"2026-10-14T12:00:00.000Z","weekStart":"2026-10-12","weekEnd":"2026-10-18","summary":{"answered":0,"streakDays":0,"dueCount":0,"averageStudySeconds":null},"readiness":{"score":0,"gradePerformance":[]},"studyProgress":[],"topics":[],"weakTopics":[],"weakKeyPoints":[]}"""
                        path == "/topics" -> "[]"
                        path == "/dashboard" -> """{"readiness":{"score":0,"answered":0,"practicedTopics":0,
                            "totalTopics":0,"gradePerformance":[]},"todayReview":[],"weakTopics":[],"dueCount":12}"""
                        path == "/me" -> """{"id":"user","email":"developer@example.com","displayName":"Dmitry",
                            "emailVerified":true,"specialization":"ANDROID","createdAt":"","updatedAt":"",
                            "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"en"}"""
                        else -> error("Unexpected path $path")
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
            favorites = GetFavoriteQuestionsUseCase(repository)
        }

        fun listRequests() = requests.filter {
            it.url.encodedPath in listOf("/questions/saved", "/reviews/due", "/questions")
        }

        companion object {
            fun page(ids: List<Int>, cursor: String?, saved: Boolean = true): String {
                val items = ids.joinToString(",") { id ->
                    """{"id":$id,"question":"Question $id","specialization":"ANDROID",
                    "topic":{"id":7,"slug":"flow","name":"Flow","visual":{"iconUrl":"/flow.svg",
                    "iconVersion":2,"darkColor":"#123456","lightColor":"#ABCDEF"}},
                    "difficulty":"MEDIUM","minGrade":"JUNIOR","maxGrade":"SENIOR","tags":["flow"],
                    "saved":$saved,"progress":{"questionId":$id,"attempts":2,"lastScore":100,"masteryScore":100,
                    "mastered":true},"updatedAt":"2026-10-13","language":"en","requestedLanguage":"en"}"""
                }
                val cursorJson = cursor?.let { "\"$it\"" } ?: "null"
                return """{"items":[$items],"nextCursor":$cursorJson,"topics":[],
                    "access":{"topicId":7,"totalCount":3,"visibleCount":3,"lockedCount":0,"limitedByPlan":false}}"""
            }
        }
    }

    private suspend fun viewModel(fixture: Fixture, handle: SavedStateHandle = SavedStateHandle(
        mapOf("homeTab" to "FAVORITES")
    )): HomeViewModel {
        val vm = HomeViewModel(
            GetDashboardUseCase(fixture.repository, fixture.auth),
            GetTopicsUseCase(fixture.repository), GetQuestionsUseCase(fixture.repository),
            handle, TopicCatalogUseCase(fixture.repository), SetQuestionSavedUseCase(fixture.repository),
            fixture.favorites, GetStatsUseCase(fixture.repository)
        )
        stores += ViewModelStore().apply { put("home", vm) }
        vm.stateFlow.first { it.dashboard is ContentViewState.Ready && it.topicCatalog is ContentViewState.Ready }
        return vm
    }

    private suspend fun ready(vm: HomeViewModel) {
        vm.stateFlow.first { it.favorites is ContentViewState.Ready }
    }

    private fun content(vm: HomeViewModel) =
        (vm.stateFlow.value.favorites as ContentViewState.Ready<QuestionsContent>).content

    @Test fun savedAndDueSendOnlyLimitAndOpaqueCursorAndDecodeTheSharedResponse() = runBlocking {
        val fixture = Fixture()
        listOf(FavoritesFilter.SAVED, FavoritesFilter.DUE).forEach { filter ->
            val response = (fixture.favorites(FavoriteQuestionsRequest(filter, "opaque+/= cursor")) as ContentResult.Success).content
            val request = fixture.listRequests().last()
            assertEquals(if (filter == FavoritesFilter.SAVED) "/questions/saved" else "/reviews/due", request.url.encodedPath)
            assertEquals("GET", request.method)
            assertNull(request.body)
            assertEquals("Bearer access", request.header("Authorization"))
            assertEquals(setOf("limit", "cursor"), request.url.queryParameterNames)
            assertEquals("20", request.url.queryParameter("limit"))
            assertEquals("opaque+/= cursor", request.url.queryParameter("cursor"))
            val question = response.items.first()
            assertEquals("/flow.svg", question.topic.visual!!.iconUrl)
            assertEquals("#123456", question.topic.visual!!.darkColor)
            assertEquals("#ABCDEF", question.topic.visual!!.lightColor)
            assertTrue(question.saved)
            assertTrue(question.progress!!.mastered)
            assertEquals(3, response.access!!.visibleCount)
        }
    }

    @Test fun masteredUsesQuestionsWithMasteredTrueAndNoLibraryFilters() = runBlocking {
        val fixture = Fixture()
        fixture.favorites(FavoriteQuestionsRequest(FavoritesFilter.MASTERED))
        val request = fixture.listRequests().single()
        assertEquals("/questions", request.url.encodedPath)
        assertEquals(setOf("limit", "mastered"), request.url.queryParameterNames)
        assertEquals("true", request.url.queryParameter("mastered"))
        fixture.repository.getQuestions(null, null, null, mastered = false)
        assertEquals("false", fixture.listRequests().last().url.queryParameter("mastered"))
    }

    @Test fun favoritesLoadLazilyAndKeepThePageAcrossUnchangedTabVisits() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture, SavedStateHandle())
        assertTrue(fixture.listRequests().isEmpty())
        vm.selectTab(HomeTab.FAVORITES)
        ready(vm)
        assertEquals(FavoritesFilter.SAVED, vm.stateFlow.value.favoritesFilter)
        vm.selectTab(HomeTab.HOME)
        vm.selectTab(HomeTab.FAVORITES)
        assertEquals(1, fixture.listRequests().size)
    }

    @Test fun singleChoiceFiltersChangeEndpointsResetCursorAndPersistSelection() = runTest(dispatcher) {
        val fixture = Fixture()
        val handle = SavedStateHandle(mapOf("homeTab" to "FAVORITES"))
        val vm = viewModel(fixture, handle)
        ready(vm)
        val initialRevision = content(vm).resultRevision
        vm.loadMoreFavorites()
        vm.stateFlow.first { (it.favorites as? ContentViewState.Ready)?.content?.items?.size == 3 }
        vm.selectFavoritesFilter(FavoritesFilter.DUE)
        assertSame(ContentViewState.Loading, vm.stateFlow.value.favorites)
        ready(vm)
        assertTrue(content(vm).resultRevision > initialRevision)
        assertEquals("/reviews/due", fixture.listRequests().last().url.encodedPath)
        assertNull(fixture.listRequests().last().url.queryParameter("cursor"))
        vm.selectFavoritesFilter(FavoritesFilter.MASTERED)
        ready(vm)
        assertEquals("MASTERED", handle.get<String>("favoritesFilter"))
        assertEquals("true", fixture.listRequests().last().url.queryParameter("mastered"))
        val count = fixture.listRequests().size
        vm.selectFavoritesFilter(FavoritesFilter.MASTERED)
        assertEquals(count, fixture.listRequests().size)
    }

    @Test fun restoredMasteredFilterIsUsedWithoutFetchingSavedFirst() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture, SavedStateHandle(mapOf(
            "homeTab" to "FAVORITES", "favoritesFilter" to "MASTERED"
        )))
        ready(vm)
        assertEquals(FavoritesFilter.MASTERED, vm.stateFlow.value.favoritesFilter)
        assertEquals("true", fixture.listRequests().single().url.queryParameter("mastered"))
    }

    @Test fun paginationDeduplicatesIdsPreservesScrollAndStopsAtTheEnd() = runTest(dispatcher) {
        val fixture = Fixture()
        val vm = viewModel(fixture)
        ready(vm)
        val revision = content(vm).resultRevision
        repeat(4) { vm.loadMoreFavorites() }
        vm.stateFlow.first { (it.favorites as? ContentViewState.Ready)?.content?.items?.size == 3 }
        assertEquals(listOf(1L, 2L, 3L), content(vm).items.map { it.id })
        assertEquals(revision, content(vm).resultRevision)
        assertNull(content(vm).nextCursor)
        assertEquals("opaque+/= cursor", fixture.listRequests().last().url.queryParameter("cursor"))
        vm.loadMoreFavorites()
        assertEquals(2, fixture.listRequests().size)
    }

    @Test fun failedAppendKeepsThePageAndRetriesTheSameCursor() = runTest(dispatcher) {
        val fixture = Fixture().apply { nextStatus = 500 }
        val vm = viewModel(fixture)
        ready(vm)
        vm.loadMoreFavorites()
        vm.stateFlow.first { (it.favorites as? ContentViewState.Ready)?.content?.appendError != null }
        assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })
        vm.loadMoreFavorites()
        assertEquals(2, fixture.listRequests().size)
        fixture.nextStatus = 200
        vm.retryFavoritePage()
        vm.stateFlow.first { (it.favorites as? ContentViewState.Ready)?.content?.items?.size == 3 }
        assertNull(content(vm).appendError)
        assertEquals(listOf("opaque+/= cursor", "opaque+/= cursor"),
            fixture.listRequests().drop(1).map { it.url.queryParameter("cursor") })
    }

    @Test fun aRepeatedCursorCannotCreateAnEndlessPagingLoop() = runTest(dispatcher) {
        val fixture = Fixture().apply { nextBody = Fixture.page(listOf(3), "opaque+/= cursor") }
        val vm = viewModel(fixture)
        ready(vm)
        vm.loadMoreFavorites()
        vm.stateFlow.first { (it.favorites as? ContentViewState.Ready)?.content?.items?.size == 3 }
        assertNull(content(vm).nextCursor)
        vm.loadMoreFavorites()
        assertEquals(2, fixture.listRequests().size)
    }

    @Test fun emptySavedDueAndMasteredRemainReadyForTheirEmptyMessages() = runTest(dispatcher) {
        val fixture = Fixture().apply { firstBody = Fixture.page(emptyList(), null) }
        val vm = viewModel(fixture)
        ready(vm)
        assertTrue(content(vm).items.isEmpty())
        listOf(FavoritesFilter.DUE, FavoritesFilter.MASTERED).forEach {
            vm.selectFavoritesFilter(it)
            ready(vm)
            assertTrue(content(vm).items.isEmpty())
            assertNull(content(vm).nextCursor)
        }
    }

    @Test fun initialErrorCanRetryAndExpiredAppendNavigatesToAuth() = runTest(dispatcher) {
        val fixture = Fixture().apply { firstStatus = 500 }
        val vm = viewModel(fixture)
        vm.stateFlow.first { it.favorites is ContentViewState.Failed }
        fixture.firstStatus = 200
        vm.loadFavorites()
        ready(vm)
        fixture.nextStatus = 401
        vm.loadMoreFavorites()
        vm.stateFlow.first { it.sessionExpired }
        assertSame(ContentViewState.NotLoaded, vm.stateFlow.value.favorites)
    }

    @Test fun aLateDueResponseCannotReplaceMasteredResults() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply {
            responder = { request ->
                if (request.url.encodedPath == "/reviews/due") {
                    started.complete(Unit)
                    check(release.await(5, TimeUnit.SECONDS))
                    finished.complete(Unit)
                    Fixture.page(listOf(99), null)
                } else Fixture.page(listOf(1), null)
            }
        }
        val vm = viewModel(fixture)
        ready(vm)
        try {
            vm.selectFavoritesFilter(FavoritesFilter.DUE)
            started.await()
            vm.selectFavoritesFilter(FavoritesFilter.MASTERED)
            ready(vm)
        } finally { release.countDown() }
        finished.await()
        runCurrent()
        assertEquals(FavoritesFilter.MASTERED, vm.stateFlow.value.favoritesFilter)
        assertEquals(listOf(1L), content(vm).items.map { it.id })
    }

    @Test fun unsavingUpdatesBothTabsImmediatelyAndRemovesSavedRowOnlyAfterSuccess() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val fixture = Fixture().apply { beforeMutation = {
            started.complete(Unit)
            check(release.await(5, TimeUnit.SECONDS))
        } }
        val vm = viewModel(fixture)
        ready(vm)
        vm.selectTab(HomeTab.QUESTIONS)
        vm.stateFlow.first { it.questions is ContentViewState.Ready }
        vm.selectTab(HomeTab.FAVORITES)
        try {
            vm.toggleQuestionSaved(1)
            assertFalse(content(vm).items.first().saved)
            assertFalse((vm.stateFlow.value.questions as ContentViewState.Ready).content.items.first().saved)
            advanceTimeBy(1000)
            started.await()
            assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })
        } finally { release.countDown() }
        vm.stateFlow.first { (it.favorites as? ContentViewState.Ready)?.content?.items?.size == 1 }
        assertEquals(2L, content(vm).items.single().id)
        assertEquals("DELETE", fixture.requests.last { it.method == "DELETE" }.method)
    }

    @Test fun failedUnsaveKeepsTheRowWithAnEmptyStarAndAnErrorToast() = runTest(dispatcher) {
        val fixture = Fixture().apply { mutationStatus = 500 }
        val vm = viewModel(fixture)
        ready(vm)
        val event = async { vm.uiEventsFlow.first() }
        runCurrent()
        vm.toggleQuestionSaved(1)
        advanceTimeBy(1000)
        assertEquals(UiEvent.Toast("Server message"), event.await())
        assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })
        assertFalse(content(vm).items.first().saved)
    }

    @Test fun savingInLibraryRefreshesSavedAndKeepsOptimisticChangeAgainstAStaleResponse() = runTest(dispatcher) {
        val fixture = Fixture().apply { firstBody = Fixture.page(listOf(1, 2), null, saved = false) }
        val vm = viewModel(fixture)
        ready(vm)
        vm.selectTab(HomeTab.QUESTIONS)
        vm.stateFlow.first { it.questions is ContentViewState.Ready }
        vm.toggleQuestionSaved(1)
        vm.selectTab(HomeTab.FAVORITES)
        ready(vm)
        assertTrue(content(vm).items.first().saved)
        assertEquals(2, fixture.requests.count { it.url.encodedPath == "/questions/saved" })
    }

    @Test fun dueAndMasteredRowsStayVisibleAfterSuccessfulUnsave() = runTest(dispatcher) {
        listOf(FavoritesFilter.DUE, FavoritesFilter.MASTERED).forEach { filter ->
            val firstStarted = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            var mutations = 0
            val fixture = Fixture().apply {
                beforeMutation = {
                    if (++mutations == 1) firstStarted.complete(Unit)
                    else secondStarted.complete(Unit)
                }
            }
            val vm = viewModel(fixture, SavedStateHandle(mapOf(
                "homeTab" to "FAVORITES", "favoritesFilter" to filter.name
            )))
            ready(vm)
            vm.toggleQuestionSaved(1)
            advanceTimeBy(1000)
            firstStarted.await()
            assertFalse(content(vm).items.first().saved)
            vm.toggleQuestionSaved(1)
            advanceTimeBy(1000)
            // The per-question lock allows PUT only after DELETE has been processed.
            secondStarted.await()
            assertEquals(listOf(1L, 2L), content(vm).items.map { it.id })
            assertTrue(content(vm).items.first().saved)
        }
    }
}
