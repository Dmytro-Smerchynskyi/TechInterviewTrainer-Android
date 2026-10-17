package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.domain.DashboardContent
import com.oniolla.interviewer.domain.TopicsFilter
import com.oniolla.interviewer.domain.FavoritesFilter
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme
import com.oniolla.interviewer.ui.theme.AppTheme

private val previewVisuals = listOf(
    TopicVisual("preview://figma/code", 1, "#18D9FF", "#0D8EF0"),
    TopicVisual("preview://figma/rxjava", 1, "#F44AA7", "#EE7E14"),
    TopicVisual("preview://figma/flow", 1, "#10A8FF", "#0D8EF0")
)

private val previewTopics = listOf(
    TopicResponse(1, "ANDROID", "kotlin", "Kotlin", "Language essentials",
        visual = previewVisuals[0], answered = 128, averageScore = 74.0, mastery = 68.0),
    TopicResponse(2, "ANDROID", "coroutines", "Coroutines", "Async programming",
        visual = previewVisuals[2], answered = 142, averageScore = 80.0, mastery = 61.0),
    TopicResponse(3, "ANDROID", "flow", "Flow", "Reactive streams",
        visual = previewVisuals[2], answered = 142, averageScore = 80.0, mastery = 61.0),
    TopicResponse(4, "ANDROID", "rxjava", "RxJava", "Reactive programming",
        visual = previewVisuals[1], answered = 115, averageScore = 60.0, mastery = 55.0)
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

private val previewStatistics = StatsResponse(
    readiness = Readiness(score = 78.0, previousWeekScore = 66.0, changeSinceLastWeekPoints = 12.0),
    summary = StatsSummary(answered = 124, streakDays = 7, dueCount = 18, averageStudySeconds = null),
    weekStart = "2026-10-12", weekEnd = "2026-10-18", timeZone = "Europe/Prague",
    studyProgress = listOf(6, 9, 12, 15, 18, 21, 24).mapIndexed { index, answered ->
        StudyProgressDay("2026-10-${12 + index}", answered)
    },
    topics = listOf("Kotlin", "Coroutines", "Flow", "RxJava", "Compose", "Architecture")
        .mapIndexed { index, name ->
            val colors = listOf("#7C4DFF", "#18D9FF", "#10A8FF", "#F44AA7", "#13D68B", "#FFB13B")
            WeakTopic(index + 1L, name, mastery = listOf(85.0, 72.0, 68.0, 55.0, 62.0, 59.0)[index],
                visual = TopicVisual(null, 1, colors[index], colors[index]))
        }
)

@Composable
private fun PreviewContent(tab: HomeTab = HomeTab.HOME, loading: Boolean = false, empty: Boolean = false,
    topic: QuestionTopicFilter? = null, appending: Boolean = false, appendError: String? = null,
    topicsFilter: TopicsFilter = TopicsFilter.ALL, favoritesFilter: FavoritesFilter = FavoritesFilter.SAVED,
    statisticsResponse: StatsResponse? = null) {
    // Preview renderer does not supply the status-bar inset.
    Box(Modifier.background(AppTheme.colors.background).padding(top = 24.dp)) {
        HomeScreen(
            state = HomeViewState(
                tab = tab,
                statistics = if (loading) ContentViewState.Loading else ContentViewState.Ready(StatisticsContent(
                    response = statisticsResponse ?: if (empty) StatsResponse() else previewStatistics,
                    weekStart = "2026-10-12", currentWeekStart = "2026-10-12"
                )),
                dashboard = if (loading) ContentViewState.Loading
                    else ContentViewState.Ready(if (empty) previewDashboard.copy(dashboard = DashboardResponse()) else previewDashboard),
                topics = if (loading) ContentViewState.Loading
                    else ContentViewState.Ready(if (empty) emptyList() else previewTopics),
                topicCatalog = ContentViewState.Ready(previewTopics),
                questionSearch = if (tab == HomeTab.QUESTIONS && topic == null) "flow" else "",
                questionTopic = topic,
                topicsFilter = topicsFilter,
                favoritesFilter = favoritesFilter,
                favorites = if (loading) ContentViewState.Loading else ContentViewState.Ready(QuestionsContent(
                    items = if (empty) emptyList() else previewQuestions.mapIndexed { index, question ->
                        question.copy(saved = true, progress = if (favoritesFilter == FavoritesFilter.TRAINED)
                            QuestionProgress(question.id, 1, listOf(92.0, 65.0, 28.0, 0.0, 100.0)[index],
                                50.0, null, null, false, null) else question.progress)
                    },
                    nextCursor = if (appending || appendError != null) "preview_cursor" else null,
                    isAppending = appending, appendError = appendError
                )),
                questions = if (loading) ContentViewState.Loading else ContentViewState.Ready(QuestionsContent(
                    items = if (empty) emptyList() else previewQuestions.filter { topic == null || it.topic.id == topic.id },
                    nextCursor = if (appending || appendError != null) "preview_cursor" else null,
                    access = if (empty) null else QuestionTopicAccess(3, 45, 5, 40, true),
                    isAppending = appending, appendError = appendError
                ))
            ),
            onTabSelected = {}, onTopicQuestions = { _, _ -> }, onQuestionSearch = {},
            onRetryDashboard = {}, onRetryTopics = {},
            onQuestionTopicSelected = { _, _ -> }, onClearQuestionTopic = {},
            onOpenQuestionTopic = { _, _ -> }, onQuestionDifficultySelected = {},
            onRetryQuestions = {}, onLoadMoreQuestions = {}, onRetryQuestionPage = {},
            onTopicsFilterSelected = {}, onSeeWeakTopics = {}, onRetryTopicCatalog = {},
            onToggleQuestionSaved = {}, onFavoritesFilterSelected = {},
            onRetryFavorites = {}, onLoadMoreFavorites = {}, onRetryFavoritePage = {},
            onRetryStats = {}, onStatsWeekChanged = {}
        )
    }
}

@Composable
private fun QuestionTopicMenuPreview() {
    Surface(color = AppTheme.colors.container, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Column(Modifier.width(230.dp).padding(vertical = 8.dp)) {
            QuestionTopicsMenuContent(QuestionTopicFilter(3, "Flow"),
                ContentViewState.Ready(previewTopics), { _, _ -> }, {}, {})
        }
    }
}

@Preview
@Composable
fun QuestionTopicMenuDarkPreview() { InterviewTrainerTheme(darkTheme = true) { QuestionTopicMenuPreview() } }

@Preview
@Composable
fun QuestionTopicMenuLightPreview() { InterviewTrainerTheme(darkTheme = false) { QuestionTopicMenuPreview() } }

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
fun WeakTopicsDarkPreview() { InterviewTrainerTheme(darkTheme = true) {
    PreviewContent(HomeTab.TOPICS, topicsFilter = TopicsFilter.WEAK)
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun WeakTopicsLightPreview() { InterviewTrainerTheme(darkTheme = false) {
    PreviewContent(HomeTab.TOPICS, topicsFilter = TopicsFilter.WEAK)
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun WeakTopicsEmptyPreview() { InterviewTrainerTheme(darkTheme = false) {
    PreviewContent(HomeTab.TOPICS, empty = true, topicsFilter = TopicsFilter.WEAK)
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun WeakTopicsLoadingPreview() { InterviewTrainerTheme(darkTheme = true) {
    PreviewContent(HomeTab.TOPICS, loading = true, topicsFilter = TopicsFilter.WEAK)
} }

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

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun FavoritesDarkPreview() { InterviewTrainerTheme(darkTheme = true) { PreviewContent(HomeTab.FAVORITES) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun FavoritesLightPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent(HomeTab.FAVORITES) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun FavoritesLoadingPreview() { InterviewTrainerTheme(darkTheme = true) { PreviewContent(HomeTab.FAVORITES, loading = true) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun FavoritesEmptyPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent(HomeTab.FAVORITES, empty = true) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun FavoritesDueEmptyPreview() { InterviewTrainerTheme(darkTheme = true) {
    PreviewContent(HomeTab.FAVORITES, empty = true, favoritesFilter = FavoritesFilter.DUE)
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun FavoritesMasteredEmptyPreview() { InterviewTrainerTheme(darkTheme = false) {
    PreviewContent(HomeTab.FAVORITES, empty = true, favoritesFilter = FavoritesFilter.MASTERED)
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun FavoritesTrainedEmptyPreview() { InterviewTrainerTheme(darkTheme = true) {
    PreviewContent(HomeTab.FAVORITES, empty = true, favoritesFilter = FavoritesFilter.TRAINED)
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun FavoritesTrainedScoresPreview() { InterviewTrainerTheme(darkTheme = true) {
    PreviewContent(HomeTab.FAVORITES, favoritesFilter = FavoritesFilter.TRAINED)
} }

@Preview(widthDp = 320, heightDp = 760, showSystemUi = true)
@Composable
fun FavoritesTrainedScoresNarrowPreview() { InterviewTrainerTheme(darkTheme = false) {
    PreviewContent(HomeTab.FAVORITES, favoritesFilter = FavoritesFilter.TRAINED)
} }

@Preview(widthDp = 320, heightDp = 160)
@Composable
fun FavoriteTrainedLongTopicPreview() { InterviewTrainerTheme(darkTheme = false) {
    Box(Modifier.background(AppTheme.colors.background).padding(20.dp)) {
        val question = previewQuestions.first()
        FavoriteQuestionCard(question.copy(
            topic = question.topic.copy(name = "Coroutines and concurrency"),
            progress = QuestionProgress(question.id, 1, 92.0, 50.0, null, null, false, null)
        ), onToggleSaved = {}, showLastScore = true)
    }
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun StatisticsDarkPreview() { InterviewTrainerTheme(darkTheme = true) { PreviewContent(HomeTab.STATS) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun StatisticsLightPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent(HomeTab.STATS) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun StatisticsLoadingPreview() { InterviewTrainerTheme(darkTheme = true) { PreviewContent(HomeTab.STATS, loading = true) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun StatisticsEmptyPreview() { InterviewTrainerTheme(darkTheme = false) { PreviewContent(HomeTab.STATS, empty = true) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun StatisticsNoComparisonPreview() { InterviewTrainerTheme(darkTheme = true) {
    PreviewContent(HomeTab.STATS, statisticsResponse = previewStatistics.copy(readiness = Readiness(score = 78.0)))
} }
