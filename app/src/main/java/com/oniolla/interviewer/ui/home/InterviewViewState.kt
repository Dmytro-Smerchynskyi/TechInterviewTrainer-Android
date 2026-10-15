package com.oniolla.interviewer.ui.home

import com.oniolla.interviewer.data.model.*

enum class InterviewPage { CLOSED, SETUP, LOADING, ANSWERING, EVALUATING, REVIEW, LIMIT, READY_TO_FINISH, SUMMARY, ERROR }

data class InterviewViewState(
    val page: InterviewPage = InterviewPage.CLOSED,
    val previousPage: InterviewPage = InterviewPage.SETUP,
    val history: ContentViewState<LastInterview?> = ContentViewState.Loading,
    val interview: InterviewResponse? = null,
    val position: Int = 0,
    val practice: PracticeViewState = PracticeViewState(),
    val actionLoading: Boolean = false,
    val error: String? = null,
    val summary: ContentViewState<StatsResponse> = ContentViewState.NotLoaded,
    val sessionExpired: Boolean = false
) {
    val isOpen get() = page != InterviewPage.CLOSED
    val isBusy get() = actionLoading || page == InterviewPage.EVALUATING || page == InterviewPage.LOADING
    val total get() = interview?.questions?.size ?: 0
    val isLast get() = position >= (interview?.questions?.maxOfOrNull { it.position } ?: 0)
    val activeId get() = (history as? ContentViewState.Ready)?.content?.id
}
