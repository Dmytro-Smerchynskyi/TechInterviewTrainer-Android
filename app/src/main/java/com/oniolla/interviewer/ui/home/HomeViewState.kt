package com.oniolla.interviewer.ui.home

import com.oniolla.interviewer.data.model.StatsResponse
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.data.model.QuestionAnswerResponse
import com.oniolla.interviewer.data.model.DashboardUsage
import com.oniolla.interviewer.data.model.QuestionTopicAccess
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.DashboardContent
import com.oniolla.interviewer.domain.TopicsFilter
import com.oniolla.interviewer.domain.QuestionDifficultyFilter
import com.oniolla.interviewer.domain.FavoritesFilter
import java.util.Locale

enum class HomeTab { HOME, TOPICS, QUESTIONS, STATS, FAVORITES }

sealed interface ContentViewState<out T> {
    data object NotLoaded : ContentViewState<Nothing>
    data object Loading : ContentViewState<Nothing>
    data class Ready<T>(val content: T) : ContentViewState<T>
    data class Failed(val message: String) : ContentViewState<Nothing>
}

data class QuestionTopicFilter(val id: Long, val name: String)

data class QuestionsContent(
    val items: List<QuestionResponse> = emptyList(),
    val nextCursor: String? = null,
    val topics: List<QuestionTopicAccess> = emptyList(),
    val access: QuestionTopicAccess? = null,
    val isAppending: Boolean = false,
    val appendError: String? = null,
    val resultRevision: Int = 0
)

data class QuestionDetailState(
    val question: QuestionResponse,
    val answer: ContentViewState<QuestionAnswerResponse> = ContentViewState.NotLoaded,
    val usage: DashboardUsage? = null,
    val isUsageLoading: Boolean = true,
    val usageError: String? = null,
    val isAnswerVisible: Boolean = false
) {
    val revealsRemaining get() = usage?.answerReveals?.remaining?.coerceAtLeast(0) ?: 0
    val aiReviewsRemaining get() = usage?.aiEvaluations?.remaining?.coerceAtLeast(0) ?: 0
    val canReveal get() = answer != ContentViewState.Loading &&
        (answer is ContentViewState.Ready || question.answerRevealed ||
            !isUsageLoading && usageError == null && revealsRemaining > 0)
    val canPractice get() = !isUsageLoading && usageError == null && aiReviewsRemaining > 0 &&
        answer != ContentViewState.Loading
    private val canCheckLimits get() = !isUsageLoading && usageError == null && answer != ContentViewState.Loading
    val revealLimitReached get() = canCheckLimits && !canReveal &&
        usage?.answerReveals?.remaining?.let { it <= 0 } == true
    val practiceLimitReached get() = canCheckLimits &&
        usage?.aiEvaluations?.remaining?.let { it <= 0 } == true
}

data class StatisticsContent(
    val response: StatsResponse,
    val weekStart: String,
    val currentWeekStart: String,
    val isChartLoading: Boolean = false,
    val chartError: String? = null
) {
    val canGoToNextWeek: Boolean get() = weekStart < currentWeekStart
}

data class HomeViewState(
    val tab: HomeTab = HomeTab.HOME,
    val dashboard: ContentViewState<DashboardContent> = ContentViewState.Loading,
    val topics: ContentViewState<List<TopicResponse>> = ContentViewState.NotLoaded,
    val topicsFilter: TopicsFilter = TopicsFilter.ALL,
    val topicCatalog: ContentViewState<List<TopicResponse>> = ContentViewState.Loading,
    val questionSearch: String = "",
    val questionDifficulty: QuestionDifficultyFilter = QuestionDifficultyFilter.ALL,
    val isQuestionSearchPending: Boolean = false,
    val questionTopic: QuestionTopicFilter? = null,
    val questions: ContentViewState<QuestionsContent> = ContentViewState.NotLoaded,
    val favoritesFilter: FavoritesFilter = FavoritesFilter.SAVED,
    val favorites: ContentViewState<QuestionsContent> = ContentViewState.NotLoaded,
    val statistics: ContentViewState<StatisticsContent> = ContentViewState.NotLoaded,
    val questionDetail: QuestionDetailState? = null,
    val paywallOpen: Boolean = false,
    val sessionExpired: Boolean = false
) {
    val displayName: String
        get() = (dashboard as? ContentViewState.Ready)?.content?.profile?.displayName.orEmpty().trim()
}

internal fun <T> ContentResult<T>.toViewState(): ContentViewState<T> = when (this) {
    is ContentResult.Success -> ContentViewState.Ready(content)
    is ContentResult.Error -> ContentViewState.Failed(message)
    ContentResult.SessionExpired -> ContentViewState.NotLoaded
}

fun displayNameInitials(displayName: String): String {
    val words = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val initials = if (words.size > 1) {
        words.first().first().toString() + words.last().first()
    } else {
        words.firstOrNull()?.take(2).orEmpty()
    }
    return initials.uppercase(Locale.ROOT)
}
