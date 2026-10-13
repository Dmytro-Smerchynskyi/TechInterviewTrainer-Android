package com.oniolla.interviewer.ui.home

import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.data.model.QuestionTopicAccess
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.DashboardContent
import com.oniolla.interviewer.domain.TopicsFilter
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
    val appendError: String? = null
)

data class HomeViewState(
    val tab: HomeTab = HomeTab.HOME,
    val dashboard: ContentViewState<DashboardContent> = ContentViewState.Loading,
    val topics: ContentViewState<List<TopicResponse>> = ContentViewState.NotLoaded,
    val topicsFilter: TopicsFilter = TopicsFilter.ALL,
    val questionSearch: String = "",
    val questionTopic: QuestionTopicFilter? = null,
    val questions: ContentViewState<QuestionsContent> = ContentViewState.NotLoaded,
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
