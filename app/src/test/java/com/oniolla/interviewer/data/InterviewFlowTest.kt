package com.oniolla.interviewer.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.home.*
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class InterviewFlowTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    private class Tokens : SessionTokenStorage {
        override var accessToken: String? = "access"
        override var refreshToken: String? = "refresh"
        override var sessionVersion = 1L
    }

    private inner class Fixture {
        val gson = Gson()
        val tokens = Tokens()
        val requests = CopyOnWriteArrayList<Request>()
        var active = false
        var paginate = false
        var repeatedCursor = false
        var answerError: String? = null
        var answerStatus = 403
        var loseAnswer = false
        var loseCreate = false
        var failFinish = false
        var expire = false
        var rejectedRefresh = false
        var evaluations = 0
        var creations = 0
        var model = InterviewResponse("interview", "ANDROID", "MIDDLE", "IN_PROGRESS",
            questions = listOf(InterviewQuestion(1, 4, "First"), InterviewQuestion(2, 5, "Second")))
        val repository: InterviewRepository
        val useCase: InterviewUseCase
        val question = QuestionResponse(4, "First", "ANDROID", QuestionTopic(7, "flow", "Flow"),
            "MEDIUM", "JUNIOR", "SENIOR", language = "ru", requestedLanguage = "uk")

        fun history(status: String = "IN_PROGRESS") = LastInterview("interview", model.grade, status, null, null, null, null)
        fun evaluation(id: Long) = QuestionEvaluationResponse(id = "evaluation-$id", questionId = id,
            context = "INTERVIEW", interviewId = "interview", score = 82, overallScore = 100,
            userAnswer = "Answer", overallFeedback = "Feedback",
            referenceAnswer = "Reference", referenceAnswerAvailable = true)
        init {
            val origin = "https://example.test/"
            val api = Retrofit.Builder().baseUrl(origin).client(OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, origin))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val path = request.url.encodedPath
                    var code = 200
                    val payload = request.body?.let { gson.fromJson(Buffer().also(it::writeTo).readUtf8(), JsonObject::class.java) }
                    var body: Any = when {
                        path == "/auth/refresh" -> if (rejectedRefresh) {
                            code = 401
                            mapOf("error" to mapOf("code" to "UNAUTHORIZED"))
                        } else mapOf("accessToken" to "renewed", "refreshToken" to "renewed-refresh", "expiresIn" to 900)
                        expire && path == "/interviews/interview/answers" && request.header("Authorization") == "Bearer access" -> {
                            code = 401
                            mapOf("error" to mapOf("code" to "UNAUTHORIZED"))
                        }
                        path == "/interviews/history" -> {
                            if (paginate && request.url.queryParameter("cursor") == null)
                                InterviewHistoryResponse(listOf(history("FINISHED")), "next")
                            else if (repeatedCursor) InterviewHistoryResponse(listOf(history("FINISHED")), "next")
                            else InterviewHistoryResponse(if (active) listOf(history()) else emptyList())
                        }
                        path == "/interviews" && request.method == "POST" -> {
                            creations++
                            active = true
                            model = model.copy(grade = payload!!["grade"].asString)
                            if (loseCreate) { code = 503; mapOf("error" to mapOf("code" to "TEMPORARY")) } else model
                        }
                        path == "/interviews/interview" || path == "/interviews/interview/result" -> model
                        path == "/interviews/interview/current-question" -> {
                            val next = model.questions.firstOrNull { it.evaluation == null }
                            if (next == null) CurrentInterviewQuestion()
                            else CurrentInterviewQuestion(next.position, question.copy(id = next.questionId, question = next.question!!))
                        }
                        path == "/interviews/interview/answers" -> {
                            if (answerError != null) {
                                code = answerStatus
                                mapOf("error" to mapOf("code" to answerError, "message" to "Answer failed", "subscriptionRequired" to true))
                            } else {
                                evaluations++
                                val id = payload!!["questionId"].asLong
                                val result = evaluation(id)
                                model = model.copy(questions = model.questions.map {
                                    if (it.questionId == id) it.copy(answer = payload["answer"].asString, score = 82.0, evaluation = result)
                                    else it
                                })
                                if (loseAnswer) { code = 503; mapOf("error" to mapOf("code" to "TEMPORARY")) } else result
                            }
                        }
                        path == "/interviews/interview/finish" -> {
                            if (failFinish) { code = 503; mapOf("error" to mapOf("code" to "TEMPORARY")) }
                            else if (!model.inProgress) { code = 409; mapOf("error" to mapOf("code" to "INTERVIEW_ALREADY_FINISHED")) }
                            else {
                                active = false
                                model = model.copy(status = "FINISHED", overallScore = 41.0)
                                model
                            }
                        }
                        path == "/me/usage" -> mapOf("plan" to "FREE", "answerReveals" to mapOf("remaining" to 8),
                            "aiEvaluations" to mapOf("remaining" to 12 - evaluations))
                        path == "/stats" -> StatsResponse(summary = StatsSummary(dueCount = 3),
                            weakTopics = listOf(WeakTopic(7, "Flow", mastery = 42.0)))
                        else -> error("Unexpected endpoint $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                        .body(gson.toJson(body).toResponseBody("application/json".toMediaType())).build()
                }.build()).addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            val coordinator = SessionRefreshCoordinator(tokens, api)
            val home = HomeRepository(api, MemoryTopicCache(), coordinator,
                usageRepository = SessionUsageRepository(api, coordinator, tokens))
            repository = InterviewRepository(api, coordinator)
            useCase = InterviewUseCase(repository, home)
        }

        fun vm(handle: SavedStateHandle = SavedStateHandle()) = InterviewViewModel(useCase, handle).also {
            stores += ViewModelStore().apply { put("interview", it) }
        }
        fun calls(path: String) = requests.count { it.url.encodedPath == path }
        suspend fun ready(vm: InterviewViewModel) {
            vm.state.first { it.history is ContentViewState.Ready || it.history is ContentViewState.Failed }
        }
        suspend fun start(vm: InterviewViewModel) {
            ready(vm)
            vm.open()
            if (vm.state.value.page == InterviewPage.SETUP) vm.create()
            vm.state.first { it.page == InterviewPage.ANSWERING }
            vm.setMode(AnswerMode.TEXT)
            vm.updateAnswer(" Answer ")
        }
        fun payload(path: String): JsonObject = gson.fromJson(
            Buffer().also { requests.single { it.url.encodedPath == path }.body!!.writeTo(it) }.readUtf8(), JsonObject::class.java)
    }

    @Test fun startupHistoryFindsActiveInterviewOnLaterPageAndResumeDoesNotCreate() = runTest(dispatcher) {
        val f = Fixture().apply { active = true; paginate = true }
        val vm = f.vm()
        f.ready(vm)
        assertEquals("interview", vm.state.value.activeId)
        assertEquals(2, f.calls("/interviews/history"))
        vm.open()
        val state = vm.state.first { it.page == InterviewPage.ANSWERING }
        assertEquals(1, state.position)
        assertEquals("ru", state.practice.question!!.language)
        assertEquals(1, f.calls("/interviews/interview"))
        assertEquals(1, f.calls("/interviews/interview/current-question"))
        assertEquals(0, f.creations)
        vm.close()
        runCurrent()
        assertEquals(2, f.calls("/interviews/history"))
    }

    @Test fun repeatedHistoryCursorFailsInsteadOfLoopingOrStartingAnotherInterview() = runTest(dispatcher) {
        val f = Fixture().apply { repeatedCursor = true; paginate = true }
        val vm = f.vm()
        f.ready(vm)
        assertTrue(vm.state.value.history is ContentViewState.Failed)
        assertEquals(2, f.calls("/interviews/history"))
        assertEquals(0, f.creations)
    }

    @Test fun createLocksUiAndPostsSelectedGradeOnce() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.ready(vm)
        vm.open()
        vm.selectGrade(PracticeGrade.SENIOR)
        assertEquals(0, f.creations)
        vm.create()
        assertTrue(vm.state.value.actionLoading)
        vm.selectGrade(PracticeGrade.JUNIOR)
        vm.create()
        vm.close()
        assertEquals(PracticeGrade.SENIOR, vm.state.value.practice.grade)
        vm.state.first { it.page == InterviewPage.ANSWERING }
        assertEquals(1, f.creations)
        assertEquals("SENIOR", f.payload("/interviews")["grade"].asString)
        assertEquals("Bearer access", f.requests.first { it.url.encodedPath == "/interviews" }.header("Authorization"))
    }

    @Test fun answerUsesInterviewEndpointAndNextQuestionComesFromServer() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.start(vm)
        vm.evaluate()
        vm.evaluate()
        vm.close()
        assertEquals(InterviewPage.EVALUATING, vm.state.value.page)
        val review = vm.state.first { it.page == InterviewPage.REVIEW }
        assertEquals(82, review.practice.result!!.displayedScore)
        assertFalse(review.isLast)
        assertEquals(1, f.evaluations)
        val body = f.payload("/interviews/interview/answers")
        assertEquals(4L, body["questionId"].asLong)
        assertEquals("Answer", body["answer"].asString)
        assertFalse(body.has("grade"))
        assertEquals(0, f.calls("/questions/4/evaluate"))
        assertEquals(0, f.calls("/questions/4/answer"))
        vm.next()
        val second = vm.state.first { it.page == InterviewPage.ANSWERING }
        assertEquals(2, second.position)
        assertEquals(5L, second.practice.question!!.id)
        assertTrue(second.practice.answer.isEmpty())
    }

    @Test fun lastAnswerRequiresExplicitFinishAndSummaryUsesServerScoreAndRecommendations() = runTest(dispatcher) {
        val f = Fixture().apply { model = model.copy(questions = model.questions.take(1)) }
        val vm = f.vm()
        f.start(vm)
        vm.evaluate()
        assertTrue(vm.state.first { it.page == InterviewPage.REVIEW }.isLast)
        assertEquals(0, f.calls("/interviews/interview/finish"))
        vm.next()
        assertTrue(vm.state.value.actionLoading)
        vm.next()
        val summary = vm.state.first { it.page == InterviewPage.SUMMARY }
        assertEquals(41.0, summary.interview!!.overallScore!!, 0.0)
        val stats = vm.state.first { it.summary is ContentViewState.Ready }.summary as ContentViewState.Ready
        assertEquals(3, stats.content.summary.dueCount)
        assertEquals(42.0, stats.content.weakTopics.single().mastery, 0.0)
        assertEquals(1, f.calls("/interviews/interview/finish"))
        assertNull(f.requests.single { it.url.encodedPath == "/interviews/interview/finish" }.body?.let {
            Buffer().also(it::writeTo).readUtf8().takeIf { value -> value.isNotBlank() }
        })
        vm.close()
        assertTrue(vm.takeContentRefresh())
        assertFalse(vm.takeContentRefresh())
        assertEquals(InterviewPage.CLOSED, vm.state.value.page)
    }

    @Test fun exhaustedQuotaKeepsDraftAndCanFinishWithUnansweredQuestions() = runTest(dispatcher) {
        val f = Fixture().apply { answerError = "AI_EVALUATION_LIMIT_EXCEEDED"; answerStatus = 401 }
        val vm = f.vm()
        f.start(vm)
        vm.evaluate()
        val limit = vm.state.first { it.page == InterviewPage.LIMIT }
        assertEquals(" Answer ", limit.practice.answer)
        assertFalse(limit.sessionExpired)
        assertEquals(0, f.calls("/auth/refresh"))
        vm.finish()
        vm.state.first { it.page == InterviewPage.SUMMARY }
        assertEquals(0, f.evaluations)
    }

    @Test fun failedFinishKeepsReviewAndAllowsRetry() = runTest(dispatcher) {
        val f = Fixture().apply { failFinish = true; model = model.copy(questions = model.questions.take(1)) }
        val vm = f.vm()
        f.start(vm)
        vm.evaluate()
        vm.state.first { it.page == InterviewPage.REVIEW }
        vm.next()
        vm.state.first { it.error != null && !it.actionLoading }
        assertEquals(InterviewPage.REVIEW, vm.state.value.page)
        assertNotNull(vm.state.value.practice.result)
        f.failFinish = false
        vm.next()
        vm.state.first { it.page == InterviewPage.SUMMARY }
    }

    @Test fun lostCreationResponseRecoversExistingInterviewInsteadOfCreatingAnother() = runTest(dispatcher) {
        val f = Fixture().apply { loseCreate = true }
        val vm = f.vm()
        f.ready(vm)
        vm.open()
        vm.create()
        vm.state.first { it.error != null }
        assertEquals(InterviewPage.SETUP, vm.state.value.page)
        vm.create()
        vm.state.first { it.page == InterviewPage.ANSWERING }
        assertEquals(1, f.creations)
    }

    @Test fun lostEvaluationResponseRecoversReviewWithoutChargingAgain() = runTest(dispatcher) {
        val f = Fixture().apply { loseAnswer = true }
        val vm = f.vm()
        f.start(vm)
        vm.evaluate()
        vm.state.first { it.page == InterviewPage.ANSWERING && it.practice.answerError != null }
        vm.evaluate()
        vm.state.first { it.page == InterviewPage.REVIEW }
        assertEquals(1, f.evaluations)
        assertEquals(1, f.calls("/interviews/interview/answers"))
    }

    @Test fun restoredDraftIsKeptOnlyForSameCurrentQuestionAndNeverAutoSubmitted() = runTest(dispatcher) {
        val f = Fixture().apply { active = true }
        val vm = f.vm(SavedStateHandle(mapOf("interviewId" to "interview",
            "interviewQuestionId" to 4L, "interviewAnswer" to "Saved draft")))
        val ready = vm.state.first { it.page == InterviewPage.ANSWERING }
        assertEquals("Saved draft", ready.practice.answer)
        assertEquals(0, f.evaluations)
    }

    @Test fun completedQuestionDraftIsNotCopiedToTheNextQuestionAfterProcessDeath() = runTest(dispatcher) {
        val f = Fixture().apply {
            active = true
            model = model.copy(questions = model.questions.map {
                if (it.position == 1) it.copy(evaluation = evaluation(4)) else it
            })
        }
        val vm = f.vm(SavedStateHandle(mapOf("interviewId" to "interview",
            "interviewQuestionId" to 4L, "interviewAnswer" to "Old draft")))
        val ready = vm.state.first { it.page == InterviewPage.ANSWERING }
        assertEquals(5L, ready.practice.question!!.id)
        assertTrue(ready.practice.answer.isEmpty())
        assertEquals(0, f.evaluations)
    }

    @Test fun noCurrentQuestionOffersFinishInsteadOfInventingQuestion() = runTest(dispatcher) {
        val f = Fixture().apply {
            active = true
            model = model.copy(questions = emptyList())
        }
        val vm = f.vm()
        f.ready(vm)
        vm.open()
        vm.state.first { it.page == InterviewPage.READY_TO_FINISH }
        assertNull(vm.state.value.practice.question)
        assertEquals(0, f.calls("/interviews/interview/finish"))
    }

    @Test fun rejectedAccessRefreshesThenAnswersWithoutShowingError() = runTest(dispatcher) {
        val f = Fixture().apply { expire = true }
        val vm = f.vm()
        f.start(vm)
        vm.evaluate()
        vm.state.first { it.page == InterviewPage.REVIEW }
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(1, f.evaluations)
        assertFalse(vm.state.value.sessionExpired)
        assertNull(vm.state.value.error)
    }

    @Test fun rejectedRefreshExpiresSessionInsteadOfRepeatingEvaluation() = runTest(dispatcher) {
        val f = Fixture().apply { expire = true; rejectedRefresh = true }
        val vm = f.vm()
        f.start(vm)
        vm.evaluate()
        vm.state.first { it.sessionExpired }
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(0, f.evaluations)
    }
}
