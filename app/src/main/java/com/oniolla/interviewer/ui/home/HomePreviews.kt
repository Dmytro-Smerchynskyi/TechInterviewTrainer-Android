package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.domain.DashboardContent
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme
import com.oniolla.interviewer.ui.theme.AppTheme

private val previewVisuals = listOf(
    TopicVisual("preview://figma/code", 1, "#18D9FF", "#0D8EF0"),
    TopicVisual("preview://figma/rxjava", 1, "#F44AA7", "#EE7E14"),
    TopicVisual("preview://figma/flow", 1, "#10A8FF", "#0D8EF0")
)

private val previewTopics = listOf(
    TopicResponse(1, "ANDROID", "kotlin", "Kotlin", "Language essentials",
        visual = previewVisuals[0]),
    TopicResponse(2, "ANDROID", "coroutines", "Coroutines", "Async programming",
        visual = previewVisuals[2]),
    TopicResponse(3, "ANDROID", "flow", "Flow", "Reactive streams",
        visual = previewVisuals[2]),
    TopicResponse(4, "ANDROID", "rxjava", "RxJava", "Reactive programming",
        visual = previewVisuals[1])
)

private val previewDashboard = DashboardContent(
    profile = MeResponse("preview", "developer@example.com", "Dmitry", true, "ANDROID",
        "", "", true, listOf("PASSWORD"), "en"),
    dashboard = DashboardResponse(
        readiness = Readiness(score = 67.0, answered = 124, practicedTopics = 8, totalTopics = 8),
        dueCount = 18,
        weakTopics = listOf(
            WeakTopic(1, "Coroutines", mastery = 42.0, visual = previewVisuals[0]),
            WeakTopic(2, "RxJava", mastery = 55.0, visual = previewVisuals[1]),
            WeakTopic(3, "Flow", mastery = 61.0, visual = previewVisuals[2])
        ),
        todayReview = listOf(
            QuestionResponse(1, "Cold vs Hot Flow", "ANDROID",
                QuestionTopic(3, "flow", "Flow", previewVisuals[2]), "MEDIUM", "JUNIOR", "SENIOR"),
            QuestionResponse(2, "inline vs crossinline", "ANDROID",
                QuestionTopic(1, "kotlin", "Kotlin", previewVisuals[0]), "MEDIUM", "JUNIOR", "SENIOR"),
            QuestionResponse(3, "StateFlow vs SharedFlow", "ANDROID",
                QuestionTopic(3, "flow", "Flow", previewVisuals[2]), "EASY", "JUNIOR", "SENIOR")
        ),
        usage = DashboardUsage("FREE", null, AiEvaluationsUsage(8, 0, 20, 12, null, null), null)
    )
)

private val previewQuestions = listOf(
    QuestionResponse(1, "Cold vs Hot Flow", "ANDROID",
        QuestionTopic(3, "flow", "Flow", previewVisuals[2]), "MEDIUM", "JUNIOR", "MIDDLE"),
    QuestionResponse(2, "StateFlow vs SharedFlow", "ANDROID",
        QuestionTopic(3, "flow", "Flow", previewVisuals[2]), "EASY", "JUNIOR", "JUNIOR", saved = true),
    QuestionResponse(3, "inline vs crossinline", "ANDROID",
        QuestionTopic(1, "kotlin", "Kotlin", previewVisuals[0]), "MEDIUM", "MIDDLE", "SENIOR"),
    QuestionResponse(4, "What is backpressure in RxJava?", "ANDROID",
        QuestionTopic(4, "rxjava", "RxJava", previewVisuals[1]), "HARD", "MIDDLE", "SENIOR"),
    QuestionResponse(5, "When to use suspend vs Flow?", "ANDROID",
        QuestionTopic(3, "flow", "Flow", previewVisuals[2]), "EASY", "JUNIOR", "MIDDLE")
)

@Composable
private fun PreviewContent(tab: HomeTab = HomeTab.HOME, loading: Boolean = false, empty: Boolean = false,
    topic: QuestionTopicFilter? = null, appending: Boolean = false, appendError: String? = null) {
    // Preview renderer does not supply the status-bar inset.
    Box(Modifier.background(AppTheme.colors.background).padding(top = 24.dp)) {
        HomeScreen(
            state = HomeViewState(
                tab = tab,
                dashboard = if (loading) ContentViewState.Loading
                    else ContentViewState.Ready(if (empty) previewDashboard.copy(dashboard = DashboardResponse()) else previewDashboard),
                topics = if (loading) ContentViewState.Loading
                    else ContentViewState.Ready(if (empty) emptyList() else previewTopics),
                questionSearch = if (tab == HomeTab.QUESTIONS && topic == null) "flow" else "",
                questionTopic = topic,
                questions = if (loading) ContentViewState.Loading else ContentViewState.Ready(QuestionsContent(
                    items = if (empty) emptyList() else previewQuestions.filter { topic == null || it.topic.id == topic.id },
                    nextCursor = if (appending || appendError != null) "preview_cursor" else null,
                    access = if (empty) null else QuestionTopicAccess(3, 45, 5, 40, true),
                    isAppending = appending, appendError = appendError
                ))
            ),
            onTabSelected = {}, onTopicQuestions = {}, onQuestionSearch = {},
            onRetryDashboard = {}, onRetryTopics = {},
            onQuestionTopicSelected = { _, _ -> }, onClearQuestionTopic = {},
            onRetryQuestions = {}, onLoadMoreQuestions = {}, onRetryQuestionPage = {}
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun HomeDarkPreview() { InterviewTrainerTheme(darkTheme = true) { PreviewContent() } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun HomeLightPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent() } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun TopicsDarkPreview() { InterviewTrainerTheme(darkTheme = true) { PreviewContent(HomeTab.TOPICS) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun TopicsLightPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent(HomeTab.TOPICS) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun HomeLoadingPreview() { InterviewTrainerTheme(darkTheme = true) { PreviewContent(loading = true) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun TopicsLoadingPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent(HomeTab.TOPICS, loading = true) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun HomeEmptyPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent(empty = true) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun HomeQuestionsPreview() { InterviewTrainerTheme(darkTheme = true) { PreviewContent(HomeTab.QUESTIONS) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun QuestionsLightPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent(HomeTab.QUESTIONS) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun QuestionsFilteredPreview() { InterviewTrainerTheme(darkTheme = false) {
    PreviewContent(HomeTab.QUESTIONS, topic = QuestionTopicFilter(3, "Flow"))
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun QuestionsLoadingPreview() { InterviewTrainerTheme(darkTheme = true) {
    PreviewContent(HomeTab.QUESTIONS, loading = true)
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun QuestionsEmptyPreview() { InterviewTrainerTheme(darkTheme = false) {
    PreviewContent(HomeTab.QUESTIONS, empty = true)
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun QuestionsPageErrorPreview() { InterviewTrainerTheme(darkTheme = true) {
    PreviewContent(HomeTab.QUESTIONS, appendError = "Connection unavailable")
} }
