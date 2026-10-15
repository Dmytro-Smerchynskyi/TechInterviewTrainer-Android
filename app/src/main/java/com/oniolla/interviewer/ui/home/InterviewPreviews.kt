package com.oniolla.interviewer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme

private val interviewQuestion = QuestionResponse(4,
    "Explain StateFlow vs SharedFlow and when you would use each.", "ANDROID",
    QuestionTopic(7, "flow", "Flow", TopicVisual(darkColor = "#10A8FF", lightColor = "#178BFF")),
    "MEDIUM", "JUNIOR", "SENIOR", listOf("Architecture"), language = "en")
private val interviewEvaluation = QuestionEvaluationResponse(id = "preview", questionId = 4, score = 82,
    overallFeedback = "You covered the main ideas, but missed a few important details.",
    userAnswer = "StateFlow keeps the latest value and requires an initial value. SharedFlow is better when multiple collectors receive events.",
    keyPoints = listOf(
        EvaluationKeyPoint("current", "Current state behavior", 100.0, "Correctly explained.", "STRONG"),
        EvaluationKeyPoint("initial", "Initial value requirement", 85.0, "Good coverage.", "STRONG"),
        EvaluationKeyPoint("replay", "SharedFlow replay", 55.0, "Explain replay and buffering.", "PARTIAL"),
        EvaluationKeyPoint("events", "State vs event use cases", 35.0, "Add practical examples.", "WEAK")),
    referenceAnswerAvailable = true,
    referenceAnswer = "StateFlow always has a current value and requires an initial value. SharedFlow is configurable with replay and buffering.")
private val interviewModel = InterviewResponse("preview", "ANDROID", "MIDDLE", "IN_PROGRESS",
    questions = (1..20).map { InterviewQuestion(it, it.toLong(), score = if (it <= 9) 85.0 else if (it <= 14) 60.0 else null) })
private val interviewPreview = InterviewViewState(page = InterviewPage.ANSWERING,
    interview = interviewModel, position = 4,
    practice = PracticeViewState(page = PracticePage.ANSWERING, question = interviewQuestion,
        answer = interviewEvaluation.userAnswer.orEmpty(), result = interviewEvaluation))
private val interviewStats = StatsResponse(summary = StatsSummary(dueCount = 12),
    weakTopics = listOf(
        WeakTopic(7, "Flow", mastery = 42.0, visual = TopicVisual(iconUrl = "preview://figma/flow", darkColor = "#10A8FF", lightColor = "#178BFF")),
        WeakTopic(8, "RxJava", mastery = 55.0, visual = TopicVisual(iconUrl = "preview://figma/rxjava", darkColor = "#F44AA7", lightColor = "#FF39A0")),
        WeakTopic(9, "Coroutines", mastery = 61.0, visual = TopicVisual(iconUrl = "preview://figma/code", darkColor = "#18D9FF", lightColor = "#06AED4"))))
@Composable
private fun InterviewPreviewPage(page: InterviewPage, dark: Boolean, typing: Boolean = false, loading: Boolean = false) {
    InterviewTrainerTheme(darkTheme = dark) {
        InterviewScreens(interviewPreview.copy(page = page, actionLoading = loading,
            practice = interviewPreview.practice.copy(mode = if (typing) AnswerMode.TEXT else AnswerMode.VOICE),
            interview = if (page == InterviewPage.SUMMARY) interviewModel.copy(status = "FINISHED", overallScore = 70.0) else interviewModel,
            summary = ContentViewState.Ready(interviewStats)), InterviewActions())
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewSetupDarkPreview() { InterviewPreviewPage(InterviewPage.SETUP, true, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewSetupLightPreview() { InterviewPreviewPage(InterviewPage.SETUP, false, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewVoiceDarkPreview() { InterviewPreviewPage(InterviewPage.ANSWERING, true, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewVoiceLightPreview() { InterviewPreviewPage(InterviewPage.ANSWERING, false, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewTypingDarkPreview() { InterviewPreviewPage(InterviewPage.ANSWERING, true, typing = true) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewTypingLightPreview() { InterviewPreviewPage(InterviewPage.ANSWERING, false, typing = true) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewEvaluatingDarkPreview() { InterviewPreviewPage(InterviewPage.EVALUATING, true, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewEvaluatingLightPreview() { InterviewPreviewPage(InterviewPage.EVALUATING, false, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewReviewDarkPreview() { InterviewPreviewPage(InterviewPage.REVIEW, true, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewReviewLightPreview() { InterviewPreviewPage(InterviewPage.REVIEW, false, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewLimitDarkPreview() { InterviewPreviewPage(InterviewPage.LIMIT, true, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewLimitLightPreview() { InterviewPreviewPage(InterviewPage.LIMIT, false, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewSummaryDarkPreview() { InterviewPreviewPage(InterviewPage.SUMMARY, true, typing = false) }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun InterviewSummaryLightPreview() { InterviewPreviewPage(InterviewPage.SUMMARY, false, typing = false) }

@Preview(widthDp = 320, heightDp = 640)
@Composable
fun InterviewSetupLoadingPreview() { InterviewPreviewPage(InterviewPage.SETUP, false, loading = true) }
