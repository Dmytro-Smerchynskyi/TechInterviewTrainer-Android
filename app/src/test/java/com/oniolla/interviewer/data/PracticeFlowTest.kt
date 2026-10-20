package com.oniolla.interviewer.data

import com.oniolla.interviewer.analytics.AnalyticsEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.domain.*
import com.oniolla.interviewer.ui.home.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class PracticeFlowTest {
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
        val analytics = RecordingAnalytics()
        val requests = CopyOnWriteArrayList<Request>()
        var status = 200
        var errorCode = "EVALUATION_FAILED"
        var aiRemaining = 12
        var usageStatus = 200
        var expireAccess = false
        var refreshStatus = 200
        var responseQuestionId = 4L
        var beforeEvaluate: (() -> Unit)? = null
        var attempts = 0
        val usage: SessionUsageRepository
        val home: HomeRepository
        val useCase: EvaluateQuestionUseCase

        init {
            val origin = "https://example.test/"
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionAuthorizationInterceptor(tokens, origin))
                .addInterceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val path = request.url.encodedPath
                    val expired = path == "/questions/4/evaluate" && expireAccess &&
                        request.header("Authorization") == "Bearer access"
                    val code = when {
                        expired -> 401
                        path == "/questions/4/evaluate" -> status
                        path == "/me/subscription" -> usageStatus
                        path == "/auth/refresh" -> refreshStatus
                        else -> error("Unexpected endpoint $path")
                    }
                    if (path == "/questions/4/evaluate") beforeEvaluate?.invoke()
                    val body = if (code >= 400) {
                        val custom = if (expired || path == "/auth/refresh") "UNAUTHORIZED" else errorCode
                        if (custom == "AI_EVALUATION_LIMIT_EXCEEDED") aiRemaining = 0
                        """{"error":{"code":"$custom","message":"Request failed","subscriptionRequired":true}}"""
                    } else when (path) {
                        "/questions/4/evaluate" -> {
                            attempts++
                            aiRemaining--
                            """{"id":"attempt-$attempts","questionId":$responseQuestionId,"context":"PRACTICE",
                                "interviewId":null,"interviewQuestionId":null,"grade":"SENIOR",
                                "userAnswer":"My answer","score":82,"overallScore":100,"overallFeedback":"Feedback",
                                "strengths":["Clear definitions"],"improvements":["Explain replay"],
                                "keyPoints":[{"id":"KP_CURRENT_VALUE","description":"Current state","coveragePercent":100,
                                    "comment":"Correct","status":"STRONG"}],
                                "referenceAnswerAvailable":true,"referenceAnswer":"Reference answer",
                                "referenceAnswerLanguage":"en","createdAt":"2026-10-15T12:00:00Z"}"""
                        }
                        "/me/subscription" -> """{"plan":"FREE","answerReveals":{"used":12,"limit":20,"remaining":8},
                            "aiEvaluations":{"used":${20 - aiRemaining},"reserved":0,"limit":20,"remaining":$aiRemaining,
                                "periodEndsAt":null,"resetAt":null},"questions":{"unlimited":true,"limitPerTopic":null}}"""
                        "/auth/refresh" -> """{"accessToken":"renewed","refreshToken":"renewed-refresh","expiresIn":900}"""
                        else -> error("Unexpected endpoint $path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                        .body(body.toResponseBody("application/json".toMediaType())).build()
                }.build()
            val api = Retrofit.Builder().baseUrl(origin).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(Api::class.java)
            val coordinator = SessionRefreshCoordinator(tokens, api)
            usage = SessionUsageRepository(api, coordinator, tokens)
            home = HomeRepository(api, MemoryTopicCache(), coordinator, usageRepository = usage)
            useCase = EvaluateQuestionUseCase(home)
        }

        fun question() = QuestionResponse(4, "Question", "ANDROID", QuestionTopic(7, "flow", "Flow"),
            "MEDIUM", "JUNIOR", "SENIOR", tags = listOf("Flow", "Replay"), answerRevealed = false)
        fun vm(handle: SavedStateHandle = SavedStateHandle()) = PracticeViewModel(useCase, handle, analytics)
            .also { stores += ViewModelStore().apply { put("practice", it) } }
        fun calls(path: String) = requests.count { it.url.encodedPath == path }
        fun start(vm: PracticeViewModel, answer: String = "My answer") {
            vm.open(question())
            vm.selectGrade(PracticeGrade.SENIOR)
            vm.continueToAnswer()
            vm.setMode(AnswerMode.TEXT)
            vm.updateAnswer(answer)
        }
    }

    @Test fun gradeSelectionDoesNotCallTheServerAndKeepsTheLastSelectedGrade() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        vm.open(f.question())
        assertEquals(PracticeGrade.MIDDLE, vm.state.value.grade)
        vm.selectGrade(PracticeGrade.JUNIOR)
        vm.continueToAnswer()
        assertEquals(PracticePage.ANSWERING, vm.state.value.page)
        vm.back()
        assertEquals(PracticeGrade.JUNIOR, vm.state.value.grade)
        vm.close()
        vm.open(f.question())
        assertEquals(PracticeGrade.JUNIOR, vm.state.value.grade)
        runCurrent()
        assertTrue(f.requests.isEmpty())
    }

    @Test fun evaluatePostsTheChosenGradeAndTextAndOnlySpendsAiCredit() = runTest(dispatcher) {
        val f = Fixture()
        f.usage.refresh()
        val vm = f.vm()
        f.start(vm, "  StateFlow keeps state. SharedFlow broadcasts events.  ")
        vm.evaluateAnswer()
        assertEquals(PracticePage.EVALUATING, vm.state.value.page)
        vm.evaluateAnswer()
        val result = vm.state.first { it.page == PracticePage.REVIEW }.result!!
        f.usage.observe().first { it?.aiEvaluations?.remaining == 11 }
        val request = f.requests.single { it.url.encodedPath == "/questions/4/evaluate" }
        val payload = Gson().fromJson(Buffer().also { request.body!!.writeTo(it) }.readUtf8(), JsonObject::class.java)
        assertEquals("POST", request.method)
        assertEquals("Bearer access", request.header("Authorization"))
        assertNull(request.url.query)
        assertEquals("SENIOR", payload["grade"].asString)
        assertEquals("StateFlow keeps state. SharedFlow broadcasts events.", payload["answer"].asString)
        assertEquals(82, result.displayedScore)
        assertEquals("Feedback", result.overallFeedback)
        assertEquals("Reference answer", result.referenceAnswer)
        assertTrue(result.referenceAnswerAvailable)
        assertEquals(listOf("Clear definitions"), result.strengths)
        assertEquals(listOf("Explain replay"), result.improvements)
        assertEquals("KP_CURRENT_VALUE", result.keyPoints.single().id)
        assertEquals("STRONG", result.keyPoints.single().status)
        assertNull(result.interviewId)
        assertFalse(vm.state.value.question!!.answerRevealed)
        assertEquals(8, f.usage.current()!!.answerReveals!!.remaining)
        assertEquals(1, f.attempts)
        assertEquals(1, f.analytics.count(AnalyticsEvent.AI_EVALUATION_STARTED))
        assertEquals(mapOf("context" to "practice", "grade" to "SENIOR", "input_mode" to "text"),
            f.analytics.events.single().parameters)
        assertEquals(0, f.calls("/questions/4/answer"))
        assertTrue(vm.takeContentRefresh())
        assertFalse(vm.takeContentRefresh())
    }

    @Test fun blankAnswersCannotBeSubmitted() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.start(vm, " \n ")
        assertFalse(vm.state.value.canEvaluate)
        vm.evaluateAnswer()
        runCurrent()
        assertEquals(PracticePage.ANSWERING, vm.state.value.page)
        assertTrue(f.requests.isEmpty())
        assertTrue(f.analytics.events.isEmpty())
    }

    @Test fun recordingAndTranscribingBlockEvaluationAndEditingKeepsTheTranscript() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.start(vm, "")
        vm.setMode(AnswerMode.VOICE)
        vm.recordingStarted(1000)
        vm.updateAnswer("Recognised speech")
        assertFalse(vm.state.value.canEvaluate)
        vm.transcriptionStarted()
        assertFalse(vm.state.value.canEvaluate)
        vm.recordingFinished()
        assertTrue(vm.state.value.canEvaluate)
        vm.setMode(AnswerMode.TEXT)
        assertEquals("Recognised speech", vm.state.value.answer)
        vm.updateAnswer("Edited transcript")
        vm.setMode(AnswerMode.VOICE)
        assertEquals("Edited transcript", vm.state.value.answer)
        assertFalse(vm.state.value.isRecording)
        assertTrue(f.requests.isEmpty())
    }

    @Test fun busyEvaluationBlocksBackEditsAndDuplicateSubmits() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply { beforeEvaluate = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) } }
        val vm = f.vm()
        f.start(vm)
        try {
            vm.evaluateAnswer()
            started.await()
            vm.back()
            vm.close()
            vm.selectGrade(PracticeGrade.JUNIOR)
            vm.updateAnswer("Another answer")
            vm.setMode(AnswerMode.VOICE)
            vm.evaluateAnswer()
            assertEquals(PracticePage.EVALUATING, vm.state.value.page)
            assertEquals(PracticeGrade.SENIOR, vm.state.value.grade)
            assertEquals("My answer", vm.state.value.answer)
            assertEquals(AnswerMode.TEXT, vm.state.value.mode)
            assertEquals(1, f.calls("/questions/4/evaluate"))
        } finally { release.countDown() }
        vm.state.first { it.page == PracticePage.REVIEW }
    }

    @Test fun technicalErrorKeepsTheDraftAndRetryIsAnExplicitNewAttempt() = runTest(dispatcher) {
        val f = Fixture().apply { status = 503 }
        val vm = f.vm()
        f.start(vm)
        vm.evaluateAnswer()
        vm.state.first { it.answerError != null }
        assertEquals(PracticePage.ANSWERING, vm.state.value.page)
        assertEquals("My answer", vm.state.value.answer)
        assertEquals(PracticeGrade.SENIOR, vm.state.value.grade)
        assertEquals(0, f.attempts)
        assertTrue(vm.state.value.canEvaluate)
        f.status = 200
        vm.evaluateAnswer()
        vm.state.first { it.page == PracticePage.REVIEW }
        assertEquals(2, f.calls("/questions/4/evaluate"))
        assertEquals(1, f.attempts)
    }

    @Test fun quotaErrorIsRecognisedByCustomCodeAcrossHttpStatusesAndDoesNotExpireSession() = runTest(dispatcher) {
        for (status in listOf(400, 401, 403, 429)) {
            val f = Fixture().apply { this.status = status; errorCode = "AI_EVALUATION_LIMIT_EXCEEDED" }
            val vm = f.vm()
            f.start(vm)
            vm.evaluateAnswer()
            vm.state.first { it.page == PracticePage.LIMIT }
            f.usage.observe().first { it?.aiEvaluations?.remaining == 0 }
            assertFalse(vm.state.value.sessionExpired)
            assertEquals("access", f.tokens.accessToken)
            assertEquals("My answer", vm.state.value.answer)
            assertNull(vm.state.value.result)
            assertFalse(vm.takeContentRefresh())
            assertEquals(0, f.calls("/auth/refresh"))
            assertEquals(0, f.attempts)
            vm.close()
            assertFalse(vm.state.value.isOpen)
        }
    }

    @Test fun aLateQuotaErrorFromAnOldSessionIsDiscardedAfterAnotherUserSignsIn() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply {
            status = 403
            errorCode = "AI_EVALUATION_LIMIT_EXCEEDED"
            beforeEvaluate = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) }
        }
        val attempt = async { f.useCase(EvaluateQuestionRequest(4, "My answer", PracticeGrade.MIDDLE)) }
        try {
            started.await()
            f.tokens.saveSession("other-user-access", "other-user-refresh")
            release.countDown()
            assertTrue(runCatching { attempt.await() }.exceptionOrNull() is CancellationException)
        } finally { release.countDown() }
        assertEquals("other-user-access", f.tokens.accessToken)
        assertEquals(0, f.calls("/auth/refresh"))
    }

    @Test fun subscriptionRequiredIsParsedAndExposedByTheUseCase() = runTest(dispatcher) {
        val f = Fixture().apply { status = 403; errorCode = "AI_EVALUATION_LIMIT_EXCEEDED" }
        val result = f.useCase(EvaluateQuestionRequest(4, "My answer", PracticeGrade.MIDDLE))
        assertTrue(result is EvaluationResult.LimitReached)
        assertTrue((result as EvaluationResult.LimitReached).subscriptionRequired)
        assertEquals("Request failed", result.message)
    }

    @Test fun anotherForbiddenErrorShowsInlineErrorWithoutOpeningTheLimitScreen() = runTest(dispatcher) {
        val f = Fixture().apply { status = 403; errorCode = "SOMETHING_ELSE" }
        val vm = f.vm()
        f.start(vm)
        vm.evaluateAnswer()
        vm.state.first { it.answerError != null }
        assertEquals(PracticePage.ANSWERING, vm.state.value.page)
        assertFalse(vm.state.value.sessionExpired)
        assertNull(vm.state.value.result)
    }

    @Test fun expiredAccessTokenRefreshesBeforeReplayingEvaluationWithoutDuplicateCharge() = runTest(dispatcher) {
        val f = Fixture().apply { expireAccess = true }
        val vm = f.vm()
        f.start(vm)
        vm.evaluateAnswer()
        vm.state.first { it.page == PracticePage.REVIEW }
        assertEquals(2, f.calls("/questions/4/evaluate"))
        assertEquals(1, f.calls("/auth/refresh"))
        assertEquals(1, f.attempts)
        assertEquals("renewed", f.tokens.accessToken)
        assertFalse(vm.state.value.sessionExpired)
    }

    @Test fun rejectedRefreshExpiresSessionWithoutRetryLoop() = runTest(dispatcher) {
        val f = Fixture().apply { expireAccess = true; refreshStatus = 401 }
        val vm = f.vm()
        f.start(vm)
        vm.evaluateAnswer()
        vm.state.first { it.sessionExpired }
        assertEquals(1, f.calls("/questions/4/evaluate"))
        assertEquals(1, f.calls("/auth/refresh"))
        assertNull(f.tokens.accessToken)
        assertNull(vm.state.value.result)
    }

    @Test fun restoredDraftDoesNotAutomaticallyResubmitAPaidEvaluation() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val f = Fixture().apply { beforeEvaluate = { started.complete(Unit); check(release.await(5, TimeUnit.SECONDS)) } }
        val handle = SavedStateHandle()
        val vm = f.vm(handle)
        f.start(vm)
        try {
            vm.evaluateAnswer()
            started.await()
            val restored = f.vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
            assertEquals(PracticePage.ANSWERING, restored.state.value.page)
            assertEquals(PracticeGrade.SENIOR, restored.state.value.grade)
            assertEquals(AnswerMode.TEXT, restored.state.value.mode)
            assertEquals("My answer", restored.state.value.answer)
            runCurrent()
            assertEquals(1, f.calls("/questions/4/evaluate"))
        } finally { release.countDown() }
        vm.state.first { it.page == PracticePage.REVIEW }
    }

    @Test fun anEvaluationForAnotherQuestionIsNotDisplayed() = runTest(dispatcher) {
        val f = Fixture().apply { responseQuestionId = 99 }
        val vm = f.vm()
        f.start(vm)
        vm.evaluateAnswer()
        vm.state.first { it.answerError != null }
        assertNull(vm.state.value.result)
        assertEquals("Evaluation belongs to another question", vm.state.value.answerError)
        assertEquals("My answer", vm.state.value.answer)
    }

    @Test fun closingAReviewDoesNotMarkAManualRevealOrLoseTheSelectedGrade() = runTest(dispatcher) {
        val f = Fixture()
        val vm = f.vm()
        f.start(vm)
        vm.evaluateAnswer()
        vm.state.first { it.page == PracticePage.REVIEW }
        vm.close()
        vm.close()
        assertEquals(PracticePage.CLOSED, vm.state.value.page)
        assertEquals(PracticePage.REVIEW, vm.state.value.previousPage)
        assertFalse(vm.state.value.question!!.answerRevealed)
        vm.open(f.question())
        assertEquals(PracticeGrade.SENIOR, vm.state.value.grade)
        assertEquals("", vm.state.value.answer)
        assertNull(vm.state.value.result)
    }
}
