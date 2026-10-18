package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.gson.Gson
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.data.model.QuestionProgress
import com.oniolla.interviewer.data.model.QuestionTopic
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.UiEvent
import com.oniolla.interviewer.ui.home.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class QuestionDetailFlowTest {
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
        val mutations = Channel<Request>(Channel.UNLIMITED)
        var reveals = 8
        var aiReviews = 12
        var answerStatus = 200
        var questionStatus = 200
        var evaluationStatus = 200
        var lastScore: Double? = null
        var beforeQuestion: (() -> Unit)? = null
        var questionResponseId: Long? = null
        var usageStatus = 200
        var savedStatus = 204
        var refreshStatus = 200
        var expireAccess = false
        var answerQuestionId: Long? = null
        var serverSaved = false
        var serverAnswerRevealed = false
        var beforeAnswer: (() -> Unit)? = null
        val repository: HomeRepository
        val auth: AuthRepository

        init {
            val origin = "https://example.test/"
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, origin))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val path = request.url.encodedPath
                    val answer = path.matches(Regex("/questions/\\d+/answer"))
                    val saved = path.matches(Regex("/questions/\\d+/saved"))
                    val singleQuestion = path.matches(Regex("/questions/\\d+"))
                    val status = when {
                        (answer || singleQuestion) && expireAccess && request.header("Authorization") == "Bearer access" -> 401
                        singleQuestion -> questionStatus
                        path == "/questions/4/evaluate" -> evaluationStatus
                        answer -> answerStatus
                        saved -> savedStatus
                        path == "/me/subscription" -> usageStatus
                        path == "/auth/refresh" -> refreshStatus
                        else -> 200
                    }
                    if (answer) beforeAnswer?.invoke()
                    if (singleQuestion) beforeQuestion?.invoke()
                    val body = if (status >= 400) {
                        val code = if (status == 401) "UNAUTHORIZED"
                            else if (path.endsWith("/evaluate") && status == 400) "AI_EVALUATION_LIMIT_EXCEEDED" else "FAILED"
                        """{"error":{"code":"$code","message":"Request failed"}}"""
                    } else when {
                        answer -> {
                            if (!serverAnswerRevealed) reveals = (reveals - 1).coerceAtLeast(0)
                            serverAnswerRevealed = true
                            val id = answerQuestionId ?: request.url.pathSegments[1].toLong()
                            """{"questionId":$id,"referenceAnswer":"Reference answer $id","language":"en","requestedLanguage":"en"}"""
                        }
                        singleQuestion -> Gson().toJson(question(questionResponseId ?: request.url.pathSegments[1].toLong()))
                        path == "/questions/4/evaluate" -> {
                            lastScore = 93.0
                            """{"id":"attempt-1","questionId":4,"score":82,"overallScore":82,"context":"PRACTICE",
                                "userAnswer":"My answer","overallFeedback":"Feedback","grade":"MIDDLE"}"""
                        }
                        saved -> { serverSaved = request.method == "PUT"; "" }
                        path == "/me/subscription" -> usage()
                        path == "/questions" || path == "/questions/saved" ->
                            """{"items":[${Gson().toJson(question())}],"nextCursor":null,"topics":[]}"""
                        path == "/dashboard" -> """{"readiness":{"score":0,"gradePerformance":[]},
                            "todayReview":[${Gson().toJson(question())}],"weakTopics":[],"dueCount":0,"usage":${usage()}}"""
                        path == "/topics" -> "[]"
                        path == "/stats" -> """{"timeZone":"UTC","weekStart":"2026-10-12","weekEnd":"2026-10-18",
                            "serverTime":"2026-10-14T12:00:00Z","readiness":{"score":0,"gradePerformance":[]},
                            "summary":{"answered":0,"streakDays":0,"dueCount":0,"averageStudySeconds":null},
                            "topics":[],"weakTopics":[],"weakKeyPoints":[],"studyProgress":[]}"""
                        path == "/me" -> """{"id":"user","email":"developer@example.com","displayName":"Developer",
                            "emailVerified":true,"specialization":"ANDROID","createdAt":"","updatedAt":"",
                            "onboardingCompleted":true,"linkedProviders":["PASSWORD"],"preparationLanguage":"en"}"""
                        path == "/auth/refresh" -> """{"accessToken":"renewed","refreshToken":"renewed-refresh","expiresIn":900}"""
                        else -> error("Unexpected path $path")
                    }
                    if (saved) mutations.trySend(request)
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(status).message("Test").body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            val api = Retrofit.Builder().baseUrl(origin).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            val coordinator = SessionRefreshCoordinator(tokens, api)
            val usageRepository = SessionUsageRepository(api, coordinator, tokens)
            repository = HomeRepository(api, MemoryTopicCache(), coordinator, usageRepository = usageRepository)
            auth = AuthRepository(tokens, api, coordinator, usageRepository = usageRepository)
        }
        fun question(id: Long = 4) = QuestionResponse(id, "Question $id", "ANDROID",
            QuestionTopic(7, "flow", "Flow"), "MEDIUM", "JUNIOR", "SENIOR", listOf("Flow"), serverSaved,
            answerRevealed = serverAnswerRevealed,
            progress = lastScore?.let { QuestionProgress(id, 2, it, 70.0, null, null, false, null) })

        private fun usage() = """{"plan":"FREE","answerReveals":{"used":${20 - reveals},"limit":20,"remaining":$reveals},
            "aiEvaluations":{"used":${20 - aiReviews},"reserved":0,"limit":20,"remaining":$aiReviews,
                "periodEndsAt":null,"resetAt":null},"questions":{"unlimited":true,"limitPerTopic":null}}"""
        fun practiceVm() = PracticeViewModel(EvaluateQuestionUseCase(repository), SavedStateHandle())
            .also { stores += ViewModelStore().apply { put("practice", it) } }
        fun calls(path: String) = requests.count { it.url.encodedPath == path }
        fun vm() = HomeViewModel(GetDashboardUseCase(repository, auth), GetTopicsUseCase(repository),
            GetQuestionsUseCase(repository), SavedStateHandle(mapOf("homeTab" to "QUESTIONS")),
            TopicCatalogUseCase(repository), SetQuestionSavedUseCase(repository),
            GetFavoriteQuestionsUseCase(repository), GetStatsUseCase(repository), GetQuestionAnswerUseCase(repository))
            .also { stores += ViewModelStore().apply { put("home", it) } }
    }

    private suspend fun ready(vm: HomeViewModel) {
        vm.stateFlow.first { it.questions is ContentViewState.Ready && it.dashboard is ContentViewState.Ready &&
            it.statistics is ContentViewState.Ready && it.topicCatalog is ContentViewState.Ready }
    }
    private suspend fun open(vm: HomeViewModel, question: QuestionResponse) {
        vm.openQuestion(question)
        vm.stateFlow.first { it.questionDetail?.let { d -> !d.isUsageLoading } == true }
    }
    private suspend fun revealed(vm: HomeViewModel) {
        vm.stateFlow.first { it.questionDetail?.let { d ->
            d.answer is ContentViewState.Ready && !d.isUsageLoading
        } == true }
    }
    private fun detail(vm: HomeViewModel) = requireNotNull(vm.stateFlow.value.questionDetail)
    private fun list(vm: HomeViewModel) = (vm.stateFlow.value.questions as ContentViewState.Ready).content

    @Test fun successfulPracticeFetchesServerLastScoreAndPreservesCachedAnswerAndSavedState() = runTest(dispatcher) {
        val f = Fixture().apply { lastScore = 35.0 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        vm.revealQuestionAnswer()
        revealed(vm)
        val answer = detail(vm).answer
        vm.toggleQuestionSaved(4)
        val practice = f.practiceVm()
        practice.open(detail(vm).question)
        practice.continueToAnswer()
        practice.setMode(AnswerMode.TEXT)
        practice.updateAnswer("My answer")
        practice.evaluateAnswer()
        practice.state.first { it.page == PracticePage.REVIEW }
        assertEquals(82, practice.state.value.result!!.displayedScore)
        // Evaluation does not itself unlock a reveal; retain the local reveal/cache state.
        f.serverAnswerRevealed = false
        assertTrue(practice.takeContentRefresh())
        vm.refreshAfterPractice(practice.state.value.question!!.id)
        vm.stateFlow.first { it.questionDetail?.question?.progress?.lastScore == 93.0 }
        assertFalse(practice.takeContentRefresh())
        assertEquals(2, detail(vm).question.progress!!.attempts)
        assertEquals(70.0, detail(vm).question.progress!!.masteryScore, 0.0)
        assertTrue(detail(vm).question.saved)
        assertTrue(detail(vm).question.answerRevealed)
        assertSame(answer, detail(vm).answer)
        assertTrue(detail(vm).isAnswerVisible)
        val request = f.requests.single { it.url.encodedPath == "/questions/4" }
        assertEquals("GET", request.method)
        assertEquals("Bearer access", request.header("Authorization"))
        assertNull(request.body)
        assertNull(request.url.query)
        assertEquals(1, f.calls("/questions/4/evaluate"))
    }

    @Test fun failedQuestionRefreshKeepsSuccessfulEvaluationAndPreviousDetail() = runTest(dispatcher) {
        val f = Fixture().apply { lastScore = 35.0; questionStatus = 500 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        val practice = f.practiceVm()
        practice.open(detail(vm).question)
        practice.continueToAnswer()
        practice.setMode(AnswerMode.TEXT)
        practice.updateAnswer("My answer")
        practice.evaluateAnswer()
        practice.state.first { it.page == PracticePage.REVIEW }
        val toast = async(start = CoroutineStart.UNDISPATCHED) { vm.uiEventsFlow.first() }
        if (practice.takeContentRefresh()) vm.refreshAfterPractice(4)
        assertEquals(UiEvent.Toast("Request failed"), toast.await())
        assertEquals(35.0, detail(vm).question.progress!!.lastScore, 0.0)
        assertEquals(PracticePage.REVIEW, practice.state.value.page)
        assertFalse(vm.stateFlow.value.sessionExpired)
        assertEquals(1, f.calls("/questions/4/evaluate"))
    }

    @Test fun unsuccessfulPracticeAndInterviewRefreshDoNotFetchIndividualQuestion() = runTest(dispatcher) {
        for (status in listOf(400, 500)) {
            val f = Fixture().apply { evaluationStatus = status }
            val vm = f.vm()
            ready(vm)
            open(vm, f.question())
            val practice = f.practiceVm()
            practice.open(detail(vm).question)
            practice.continueToAnswer()
            practice.setMode(AnswerMode.TEXT)
            practice.updateAnswer("My answer")
            practice.evaluateAnswer()
            practice.state.first { it.page == PracticePage.LIMIT ||
                (it.page == PracticePage.ANSWERING && it.answerError != null) }
            assertFalse(practice.takeContentRefresh())
            vm.refreshAfterPractice() // Interview calls the shared refresh without a practice question ID.
            runCurrent()
            assertEquals(0, f.calls("/questions/4"))
        }
    }

    @Test fun practicedQuestionRefreshRenewsExpiredAccessAndIgnoresMismatchedResponseId() = runTest(dispatcher) {
        val f = Fixture().apply { lastScore = 35.0 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        f.lastScore = 75.0
        f.expireAccess = true
        vm.refreshAfterPractice(4)
        vm.stateFlow.first { it.questionDetail?.question?.progress?.lastScore == 75.0 }
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(listOf("Bearer access", "Bearer renewed"),
            f.requests.filter { it.url.encodedPath == "/questions/4" }.map { it.header("Authorization") })
        f.questionResponseId = 5
        f.lastScore = 99.0
        val toast = async(start = CoroutineStart.UNDISPATCHED) { vm.uiEventsFlow.first() }
        vm.refreshAfterPractice(4)
        assertEquals(UiEvent.Toast("Response belongs to another question"), toast.await())
        assertEquals(4L, detail(vm).question.id)
        assertEquals(75.0, detail(vm).question.progress!!.lastScore, 0.0)
    }

    @Test fun latePracticedQuestionResponseDoesNotReplaceAnotherOpenDetail() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply { lastScore = 35.0 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        f.beforeQuestion = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        try {
            vm.refreshAfterPractice(4)
            started.await()
            open(vm, f.question(5))
        } finally { release.countDown() }
        runCurrent()
        assertEquals(5L, detail(vm).question.id)
        assertEquals(35.0, detail(vm).question.progress!!.lastScore, 0.0)
    }

    @Test fun openingUsesTheLoadedQuestionAndDoesNotRequestTheAnswer() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        ready(vm)
        val question = list(vm).items.single()
        open(vm, question)
        assertEquals(question, detail(vm).question)
        assertSame(ContentViewState.NotLoaded, detail(vm).answer)
        assertEquals(8, detail(vm).revealsRemaining)
        assertEquals(12, detail(vm).aiReviewsRemaining)
        assertTrue(detail(vm).canReveal)
        assertTrue(detail(vm).canPractice)
        assertEquals(0, f.calls("/questions/4/answer"))
        assertEquals(1, f.calls("/questions"))
        assertEquals(1, f.calls("/me/subscription"))
    }

    @Test fun revealUsesAuthorizedGetShowsLoadingAndRefreshesQuotasAfterSuccess() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        vm.revealQuestionAnswer()
        assertSame(ContentViewState.Loading, detail(vm).answer)
        vm.revealQuestionAnswer()
        revealed(vm)
        val answer = (detail(vm).answer as ContentViewState.Ready).content
        assertEquals("Reference answer 4", answer.referenceAnswer)
        assertEquals("en", answer.language)
        assertEquals("en", answer.requestedLanguage)
        assertEquals(7, detail(vm).revealsRemaining)
        assertEquals(7, (vm.stateFlow.value.dashboard as ContentViewState.Ready)
            .content.dashboard.usage!!.answerReveals!!.remaining)
        val request = f.requests.single { it.url.encodedPath == "/questions/4/answer" }
        assertEquals("GET", request.method)
        assertNull(request.body)
        assertNull(request.url.query)
        assertEquals("Bearer access", request.header("Authorization"))
        vm.revealQuestionAnswer()
        runCurrent()
        assertEquals(1, f.calls("/questions/4/answer"))
        assertEquals(2, f.calls("/me/subscription"))
    }

    @Test fun aPreviouslyRevealedAnswerCanBeLoadedWithNoCreditsAndShownFromCache() = runTest(dispatcher) {
        val f = Fixture().apply { serverAnswerRevealed = true; reveals = 0 }
        val vm = f.vm()
        ready(vm)
        open(vm, list(vm).items.single())
        assertTrue(detail(vm).question.answerRevealed)
        assertTrue(detail(vm).canReveal)
        assertFalse(detail(vm).isAnswerVisible)
        vm.revealQuestionAnswer()
        assertTrue(detail(vm).isAnswerVisible)
        revealed(vm)
        val cached = detail(vm).answer
        assertEquals(0, detail(vm).revealsRemaining)
        vm.revealQuestionAnswer()
        assertFalse(detail(vm).isAnswerVisible)
        assertSame(cached, detail(vm).answer)
        vm.revealQuestionAnswer()
        assertTrue(detail(vm).isAnswerVisible)
        assertSame(cached, detail(vm).answer)
        runCurrent()
        assertEquals(1, f.calls("/questions/4/answer"))
        assertEquals(1, f.calls("/me/subscription"))
    }

    @Test fun successfulFirstRevealUpdatesAllListsAndCanBeHiddenAfterTheLastCredit() = runTest(dispatcher) {
        val f = Fixture().apply { reveals = 1 }
        val vm = f.vm()
        ready(vm)
        vm.selectTab(HomeTab.FAVORITES)
        vm.stateFlow.first { it.favorites is ContentViewState.Ready }
        open(vm, list(vm).items.single())
        vm.revealQuestionAnswer()
        revealed(vm)
        assertEquals(0, detail(vm).revealsRemaining)
        assertTrue(detail(vm).question.answerRevealed)
        assertTrue(list(vm).items.single().answerRevealed)
        assertTrue((vm.stateFlow.value.favorites as ContentViewState.Ready).content.items.single().answerRevealed)
        assertTrue((vm.stateFlow.value.dashboard as ContentViewState.Ready).content.dashboard.todayReview.single().answerRevealed)
        assertTrue(detail(vm).canReveal)
        vm.revealQuestionAnswer()
        assertFalse(detail(vm).isAnswerVisible)
        vm.revealQuestionAnswer()
        assertTrue(detail(vm).isAnswerVisible)
        runCurrent()
        assertEquals(1, f.calls("/questions/4/answer"))
        vm.closeQuestion()
        open(vm, f.question().copy(answerRevealed = false))
        assertTrue(detail(vm).question.answerRevealed)
        assertTrue(detail(vm).canReveal)
        val usageCalls = f.calls("/me/subscription")
        vm.revealQuestionAnswer()
        revealed(vm)
        assertEquals(2, f.calls("/questions/4/answer"))
        assertEquals(usageCalls, f.calls("/me/subscription"))
    }

    @Test fun alreadyRevealedAnswerIsAvailableEvenWhenUsageFails() = runTest(dispatcher) {
        val f = Fixture().apply { serverAnswerRevealed = true; usageStatus = 503 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        assertNotNull(detail(vm).usageError)
        assertTrue(detail(vm).canReveal)
        vm.revealQuestionAnswer()
        revealed(vm)
        assertTrue(detail(vm).isAnswerVisible)
        runCurrent()
        assertEquals(1, f.calls("/me/subscription"))
        f.usageStatus = 200
        vm.revealQuestionAnswer()
        vm.revealQuestionAnswer()
        runCurrent()
        assertEquals(1, f.calls("/questions/4/answer"))
    }

    @Test fun failedPreviouslyRevealedAnswerAndRetryDoNotRefreshUsage() = runTest(dispatcher) {
        val f = Fixture().apply { serverAnswerRevealed = true; answerStatus = 503 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        val usage = detail(vm).usage
        vm.revealQuestionAnswer()
        vm.stateFlow.first { it.questionDetail?.answer is ContentViewState.Failed }
        runCurrent()
        assertEquals(1, f.calls("/me/subscription"))
        assertEquals(usage, detail(vm).usage)
        assertTrue(detail(vm).question.answerRevealed)
        f.answerStatus = 200
        vm.revealQuestionAnswer()
        revealed(vm)
        runCurrent()
        assertEquals(1, f.calls("/me/subscription"))
        assertEquals(2, f.calls("/questions/4/answer"))
    }

    @Test fun aFailedFirstRevealDoesNotMarkTheQuestionAsAlreadyRevealed() = runTest(dispatcher) {
        val f = Fixture().apply { answerStatus = 503 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        vm.revealQuestionAnswer()
        vm.stateFlow.first { it.questionDetail?.let { d ->
            d.answer is ContentViewState.Failed && !d.isUsageLoading
        } == true }
        assertFalse(detail(vm).question.answerRevealed)
        assertFalse(list(vm).items.single().answerRevealed)
        assertTrue(detail(vm).isAnswerVisible)
    }

    @Test fun answerRevealedIsParsedFromTheQuestionResponse() {
        val gson = Gson()
        assertTrue(gson.fromJson("""{"id":4,"answerRevealed":true}""", QuestionResponse::class.java).answerRevealed)
        assertFalse(gson.fromJson("""{"id":4,"answerRevealed":false}""", QuestionResponse::class.java).answerRevealed)
        assertFalse(gson.fromJson("""{"id":4}""", QuestionResponse::class.java).answerRevealed)
    }

    @Test fun paywallIsOfferedOnlyForKnownExhaustedCreditsWhileIdle() = runTest(dispatcher) {
        val f = Fixture().apply { reveals = 0; aiReviews = 0 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        val detail = detail(vm)
        assertTrue(detail.revealLimitReached)
        assertTrue(detail.practiceLimitReached)
        for (unavailable in listOf(
            detail.copy(isUsageLoading = true),
            detail.copy(usageError = "Request failed"),
            detail.copy(usage = null),
            detail.copy(answer = ContentViewState.Loading)
        )) {
            assertFalse(unavailable.revealLimitReached)
            assertFalse(unavailable.practiceLimitReached)
        }
        val alreadyRevealed = detail.copy(question = detail.question.copy(answerRevealed = true))
        assertFalse(alreadyRevealed.revealLimitReached)
        assertTrue(alreadyRevealed.canReveal)
        assertTrue(alreadyRevealed.practiceLimitReached)
        assertEquals(0, f.calls("/questions/4/answer"))
    }

    @Test fun zeroRevealsDisableRevealButDoNotDisableAvailableAiReviews() = runTest(dispatcher) {
        val f = Fixture().apply { reveals = 0 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        assertFalse(detail(vm).canReveal)
        assertTrue(detail(vm).canPractice)
        vm.revealQuestionAnswer()
        runCurrent()
        assertEquals(0, f.calls("/questions/4/answer"))
        assertSame(ContentViewState.NotLoaded, detail(vm).answer)
    }

    @Test fun zeroAiReviewsDoNotDisableReveal() = runTest(dispatcher) {
        val f = Fixture().apply { aiReviews = 0 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        assertFalse(detail(vm).canPractice)
        assertTrue(detail(vm).canReveal)
    }

    @Test fun failedAnswerKeepsQuestionAndCanBeRetried() = runTest(dispatcher) {
        val f = Fixture().apply { answerStatus = 503 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        vm.revealQuestionAnswer()
        vm.stateFlow.first { it.questionDetail?.let { d ->
            d.answer is ContentViewState.Failed && !d.isUsageLoading
        } == true }
        assertEquals("Request failed", (detail(vm).answer as ContentViewState.Failed).message)
        assertEquals(4L, detail(vm).question.id)
        assertEquals(8, detail(vm).revealsRemaining)
        assertTrue(detail(vm).canReveal)
        f.answerStatus = 200
        vm.revealQuestionAnswer()
        revealed(vm)
        assertEquals(2, f.calls("/questions/4/answer"))
    }

    @Test fun unauthorizedAnswerRefreshesAndRetriesBeforeShowingAnswer() = runTest(dispatcher) {
        val f = Fixture().apply { expireAccess = true }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        vm.revealQuestionAnswer()
        revealed(vm)
        assertFalse(vm.stateFlow.value.sessionExpired)
        assertEquals(2, f.calls("/questions/4/answer"))
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals("renewed", f.tokens.accessToken)
        assertEquals("Bearer renewed", f.requests.last { it.url.encodedPath == "/questions/4/answer" }
            .header("Authorization"))
        assertEquals(7, detail(vm).revealsRemaining)
    }

    @Test fun rejectedRefreshExpiresSessionWithoutLooping() = runTest(dispatcher) {
        val f = Fixture().apply { expireAccess = true; refreshStatus = 401 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        vm.revealQuestionAnswer()
        vm.stateFlow.first { it.sessionExpired }
        assertNull(f.tokens.accessToken)
        assertNull(f.tokens.refreshToken)
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(1, f.calls("/questions/4/answer"))
    }

    @Test fun switchingQuestionsCancelsTheOldAnswerAndDoesNotDisplayItInTheNewQuestion() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture()
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        f.beforeAnswer = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        try {
            vm.revealQuestionAnswer()
            started.await()
            vm.closeQuestion()
            assertNull(vm.stateFlow.value.questionDetail)
            open(vm, f.question(5))
            assertEquals(5L, detail(vm).question.id)
            assertSame(ContentViewState.NotLoaded, detail(vm).answer)
        } finally { release.countDown() }
        runCurrent()
        assertEquals(5L, detail(vm).question.id)
        assertSame(ContentViewState.NotLoaded, detail(vm).answer)
    }

    @Test fun detailSaveIsImmediateDebouncedAndSynchronizedWithListsAndDashboard() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        ready(vm)
        open(vm, list(vm).items.single())
        vm.toggleQuestionSaved(4)
        assertTrue(detail(vm).question.saved)
        assertTrue(list(vm).items.single().saved)
        assertTrue((vm.stateFlow.value.dashboard as ContentViewState.Ready).content.dashboard.todayReview.single().saved)
        advanceTimeBy(999)
        runCurrent()
        assertEquals(0, f.calls("/questions/4/saved"))
        advanceTimeBy(1)
        runCurrent()
        assertEquals("PUT", f.mutations.receive().method)
        vm.closeQuestion()
        vm.selectTab(HomeTab.FAVORITES)
        vm.stateFlow.first { it.favorites is ContentViewState.Ready }
        assertTrue((vm.stateFlow.value.favorites as ContentViewState.Ready).content.items.single().saved)
    }

    @Test fun failedSavingAndUnSavingBothLeaveTheDetailStarEmpty() = runTest(dispatcher) {
        for (initialSaved in listOf(false, true)) {
            val f = Fixture().apply { serverSaved = initialSaved; savedStatus = 500 }
            val vm = f.vm()
            ready(vm)
            open(vm, f.question())
            val toast = async(start = CoroutineStart.UNDISPATCHED) { vm.uiEventsFlow.first { it is UiEvent.Toast } }
            vm.toggleQuestionSaved(4)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(if (initialSaved) "DELETE" else "PUT", f.mutations.receive().method)
            toast.await()
            assertFalse(detail(vm).question.saved)
            assertFalse(list(vm).items.single().saved)
        }
    }

    @Test fun aQuestionOpenedFromTodaysReviewCanBeSavedWithoutBeingInTheQuestionList() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        ready(vm)
        val review = (vm.stateFlow.value.dashboard as ContentViewState.Ready).content.dashboard.todayReview.single()
        open(vm, review.copy(id = 99))
        vm.toggleQuestionSaved(99)
        assertTrue(detail(vm).question.saved)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("/questions/99/saved", f.mutations.receive().url.encodedPath)
    }

    @Test fun failedUsageDisablesActionsUntilUsageRetrySucceeds() = runTest(dispatcher) {
        val f = Fixture().apply { usageStatus = 503 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        assertEquals("Request failed", detail(vm).usageError)
        assertFalse(detail(vm).canReveal)
        assertFalse(detail(vm).canPractice)
        vm.revealQuestionAnswer()
        assertEquals(0, f.calls("/questions/4/answer"))
        f.usageStatus = 200
        vm.refreshQuestionUsage()
        vm.stateFlow.first { it.questionDetail?.let { d -> !d.isUsageLoading && d.usageError == null } == true }
        assertTrue(detail(vm).canReveal)
    }

    @Test fun mismatchedAnswerIdIsRejectedAndNeverDisplayed() = runTest(dispatcher) {
        val f = Fixture().apply { answerQuestionId = 5 }
        val vm = f.vm()
        ready(vm)
        open(vm, f.question())
        vm.revealQuestionAnswer()
        vm.stateFlow.first { it.questionDetail?.let { detail ->
            detail.answer is ContentViewState.Failed && !detail.isUsageLoading
        } == true }
        assertEquals(4L, detail(vm).question.id)
        assertEquals("Answer belongs to another question", (detail(vm).answer as ContentViewState.Failed).message)
    }

    @Test fun selectingATabClosesDetailWithoutReloadingExistingQuestions() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        ready(vm)
        val content = list(vm)
        open(vm, content.items.single())
        vm.selectTab(HomeTab.QUESTIONS)
        assertNull(vm.stateFlow.value.questionDetail)
        assertEquals(content, list(vm))
        assertEquals(HomeTab.QUESTIONS, vm.stateFlow.value.tab)
        assertEquals(1, f.calls("/questions"))
    }
}
