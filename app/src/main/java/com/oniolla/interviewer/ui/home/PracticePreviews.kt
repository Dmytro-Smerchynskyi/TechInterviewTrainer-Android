package com.oniolla.interviewer.ui.home

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme

private val practicePreview = PracticeViewState(page = PracticePage.GRADE, reviewsRemaining = 12,
    question = QuestionResponse(4, "Explain StateFlow vs SharedFlow and when you would use each.", "ANDROID",
        QuestionTopic(7, "flow", "Flow", TopicVisual(darkColor = "#10A8FF", lightColor = "#178BFF")),
        "MEDIUM", "JUNIOR", "SENIOR", listOf("Architecture")),
    answer = "StateFlow keeps the latest value and requires an initial value. SharedFlow is better when multiple collectors receive events.")
private val reviewPreview = practicePreview.copy(page = PracticePage.REVIEW, result = QuestionEvaluationResponse(
    id = "preview", questionId = 4, score = 82, overallScore = 82,
    userAnswer = practicePreview.answer, grade = PracticeGrade.MIDDLE,
    overallFeedback = "Good overall answer. You explained the core behavior well, but should go deeper on replay and clearly distinguish state from event use cases.",
    keyPoints = listOf(
        EvaluationKeyPoint("current", "Current state behavior", 100.0, "Correctly explained.", "STRONG"),
        EvaluationKeyPoint("initial", "Initial value requirement", 85.0, "Good coverage.", "STRONG"),
        EvaluationKeyPoint("replay", "SharedFlow replay", 55.0, "Explain replay and buffer capacity.", "PARTIAL"),
        EvaluationKeyPoint("events", "State vs event use cases", 35.0, "Include a practical example.", "WEAK")),
    referenceAnswerAvailable = true, referenceAnswer = "StateFlow always has a current value and requires an initial value. SharedFlow is configurable with replay/buffering and is useful for broadcasting events."))
private val voicePreview get() = practicePreview.copy(page = PracticePage.ANSWERING,
    isRecording = true, voiceLevel = 0.7f, recordingStartedAt = SystemClock.elapsedRealtime() - 36_000)

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeGradeDarkPreview() { InterviewTrainerTheme(darkTheme = true) { PracticeScreens(practicePreview) } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeGradeLightPreview() { InterviewTrainerTheme(darkTheme = false) { PracticeScreens(practicePreview) } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeVoiceDarkPreview() { InterviewTrainerTheme(darkTheme = true) { PracticeScreens(voicePreview) } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeVoiceLightPreview() { InterviewTrainerTheme(darkTheme = false) { PracticeScreens(voicePreview) } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeTypingDarkPreview() { InterviewTrainerTheme(darkTheme = true) {
    PracticeScreens(practicePreview.copy(page = PracticePage.ANSWERING, mode = AnswerMode.TEXT))
} }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeTypingLightPreview() { InterviewTrainerTheme(darkTheme = false) {
    PracticeScreens(practicePreview.copy(page = PracticePage.ANSWERING, mode = AnswerMode.TEXT))
} }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeEvaluatingDarkPreview() { InterviewTrainerTheme(darkTheme = true) {
    PracticeScreens(practicePreview.copy(page = PracticePage.EVALUATING))
} }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeEvaluatingLightPreview() { InterviewTrainerTheme(darkTheme = false) {
    PracticeScreens(practicePreview.copy(page = PracticePage.EVALUATING))
} }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeReviewDarkPreview() { InterviewTrainerTheme(darkTheme = true) { PracticeScreens(reviewPreview) } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeReviewLightPreview() { InterviewTrainerTheme(darkTheme = false) { PracticeScreens(reviewPreview) } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeLimitDarkPreview() { InterviewTrainerTheme(darkTheme = true) {
    PracticeScreens(practicePreview.copy(page = PracticePage.LIMIT, reviewsRemaining = 0))
} }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PracticeLimitLightPreview() { InterviewTrainerTheme(darkTheme = false) {
    PracticeScreens(practicePreview.copy(page = PracticePage.LIMIT, reviewsRemaining = 0))
} }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PaywallDarkPreview() { InterviewTrainerTheme(darkTheme = true) { PaywallScreen() } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun PaywallLightPreview() { InterviewTrainerTheme(darkTheme = false) { PaywallScreen() } }
@Preview(widthDp = 320, heightDp = 640)
@Composable
fun PracticeCompactPreview() { InterviewTrainerTheme(darkTheme = false) {
    PracticeScreens(practicePreview.copy(page = PracticePage.ANSWERING, mode = AnswerMode.TEXT,
        question = practicePreview.question!!.copy(tags = listOf("Architecture", "StateFlow", "Replay", "Buffering", "Events"))))
} }
