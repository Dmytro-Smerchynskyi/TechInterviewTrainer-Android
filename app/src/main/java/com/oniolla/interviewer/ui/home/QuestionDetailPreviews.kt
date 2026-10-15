package com.oniolla.interviewer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme

private val detailPreview = QuestionDetailState(
    question = QuestionResponse(4, "What is the difference between cold and hot Flow?", "ANDROID",
        QuestionTopic(7, "flow", "Flow", TopicVisual(darkColor = "#10A8FF", lightColor = "#10A8FF")),
        "MEDIUM", "JUNIOR", "MIDDLE", listOf("StateFlow", "SharedFlow", "RxJava"),
        progress = QuestionProgress(4, 2, 85.0, 70.0, null, null, false, null)),
    usage = DashboardUsage("FREE", AnswerRevealsUsage(12,20,8),
        AiEvaluationsUsage(8,0,20,12,null,null), QuestionsUsage(true,null)),
    isUsageLoading = false
)

private val answerPreview = detailPreview.copy(
    question = detailPreview.question.copy(answerRevealed = true), isAnswerVisible = true,
    answer = ContentViewState.Ready(QuestionAnswerResponse(4,
    "- Cold Flow is lazy: work starts when it is collected.\n" +
    "- Hot Flow exists independently and may emit without collectors.\n" +
    "- StateFlow/SharedFlow are common hot Flow implementations.", "en", "en")))

@Composable
private fun DetailPreview(state: QuestionDetailState = detailPreview) {
    QuestionDetailScreen(state, bottomBar = { HomeNavigation(HomeTab.QUESTIONS, {}) })
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionDetailDarkPreview() { InterviewTrainerTheme(darkTheme = true) { DetailPreview() } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionDetailLightPreview() { InterviewTrainerTheme(darkTheme = false) { DetailPreview() } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionAnswerDarkPreview() { InterviewTrainerTheme(darkTheme = true) { DetailPreview(answerPreview) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionAnswerLightPreview() { InterviewTrainerTheme(darkTheme = false) { DetailPreview(answerPreview) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionAnswerLoadingPreview() { InterviewTrainerTheme(darkTheme = true) {
    DetailPreview(detailPreview.copy(answer = ContentViewState.Loading, isAnswerVisible = true))
} }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionDetailNoCreditsPreview() { InterviewTrainerTheme(darkTheme = false) {
    DetailPreview(detailPreview.copy(usage = DashboardUsage("FREE", AnswerRevealsUsage(20,20,0),
        AiEvaluationsUsage(20,0,20,0,null,null), QuestionsUsage(true,null))))
} }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionDetailEmptyTagsPreview() { InterviewTrainerTheme(darkTheme = true) {
    DetailPreview(detailPreview.copy(question = detailPreview.question.copy(tags = emptyList(), saved = true)))
} }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionAnswerErrorPreview() { InterviewTrainerTheme(darkTheme = false) {
    DetailPreview(detailPreview.copy(answer = ContentViewState.Failed("Unable to load reference answer"), isAnswerVisible = true))
} }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionAlreadyRevealedPreview() { InterviewTrainerTheme(darkTheme = true) {
    DetailPreview(answerPreview.copy(isAnswerVisible = false,
        usage = DashboardUsage("FREE", AnswerRevealsUsage(20,20,0),
            AiEvaluationsUsage(20,0,20,0,null,null), QuestionsUsage(true,null))))
} }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionDetailNoAttemptsPreview() { InterviewTrainerTheme(darkTheme = true) {
    DetailPreview(detailPreview.copy(question = detailPreview.question.copy(
        progress = detailPreview.question.progress!!.copy(attempts = 0))))
} }

@Preview(widthDp = 320, heightDp = 640)
@Composable
fun QuestionDetailLongTopicPreview() { InterviewTrainerTheme(darkTheme = false) {
    DetailPreview(detailPreview.copy(question = detailPreview.question.copy(
        topic = detailPreview.question.topic.copy(name = "Coroutines and concurrency"),
        progress = detailPreview.question.progress!!.copy(lastScore = 28.0))))
} }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun QuestionDetailMediumScorePreview() { InterviewTrainerTheme(darkTheme = true) {
    DetailPreview(detailPreview.copy(question = detailPreview.question.copy(
        progress = detailPreview.question.progress!!.copy(lastScore = 65.0))))
} }

@Preview(widthDp = 320, heightDp = 640)
@Composable
fun QuestionDetailCompactPreview() { InterviewTrainerTheme(darkTheme = false) {
    DetailPreview(answerPreview)
} }
