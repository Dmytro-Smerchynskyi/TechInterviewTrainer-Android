package com.oniolla.interviewer.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.PracticeGrade

internal data class InterviewActions(
    val close: () -> Unit = {},
    val grade: (PracticeGrade) -> Unit = {},
    val create: () -> Unit = {},
    val retry: () -> Unit = {},
    val evaluate: () -> Unit = {},
    val next: () -> Unit = {},
    val finish: () -> Unit = {},
    val plans: () -> Unit = {},
    val retrySummary: () -> Unit = {},
    val mode: (AnswerMode) -> Unit = {},
    val answer: (String) -> Unit = {},
    val recordingStarted: (Long) -> Unit = {},
    val transcribing: () -> Unit = {},
    val recordingFinished: (String?) -> Unit = {},
    val level: (Float) -> Unit = {},
    val topic: (Long, String) -> Unit = { _, _ -> },
    val weak: () -> Unit = {},
    val due: () -> Unit = {}
)

@Composable
internal fun InterviewScreens(state: InterviewViewState, actions: InterviewActions) {
    val page = if (state.page == InterviewPage.CLOSED) state.previousPage else state.page
    PracticeTextTheme {
        Box(Modifier.fillMaxSize().background(com.oniolla.interviewer.ui.theme.AppTheme.colors.background)) {
            AnimatedContent(page, transitionSpec = {
                fadeIn(tween(250)).togetherWith(fadeOut(tween(250))).using(null)
            }, label = "interview navigation") { current ->
                when (current) {
                    InterviewPage.SETUP -> PracticeGradeScreen(state.practice, actions.close, actions.grade,
                        actions.create, interview = true, loading = state.actionLoading, error = state.error)
                    InterviewPage.ANSWERING -> androidx.compose.runtime.key(state.interview?.id, state.position) {
                        PracticeAnswerScreen(state.practice, actions.close, actions.mode, actions.answer,
                            actions.evaluate, actions.recordingStarted, actions.transcribing,
                            actions.recordingFinished, actions.level, interview = true,
                            header = { close -> InterviewHeader(state, close) })
                    }
                    InterviewPage.EVALUATING -> PracticeEvaluatingScreen(state.practice, interview = true,
                        header = { InterviewHeader(state, {}, enabled = false) })
                    InterviewPage.REVIEW -> PracticeReviewScreen(state.practice, actions.close,
                        onContinue = actions.next,
                        continueText = stringResource(if (state.isLast) R.string.interview_results else R.string.interview_next),
                        loading = state.actionLoading,
                        header = { InterviewHeader(state, actions.close, review = true, enabled = !state.isBusy) },
                        error = state.error)
                    InterviewPage.LIMIT -> PracticeLimitScreen(state.practice, actions.finish, actions.plans,
                        continueText = stringResource(R.string.interview_results), loading = state.actionLoading,
                        header = { InterviewHeader(state, actions.close, review = true, enabled = !state.isBusy) },
                        error = state.error)
                    InterviewPage.SUMMARY -> InterviewSummaryScreen(state, actions)
                    InterviewPage.READY_TO_FINISH -> InterviewFinishScreen(state, actions)
                    InterviewPage.ERROR -> InterviewErrorScreen(state, actions)
                    InterviewPage.LOADING -> InterviewSkeleton(state)
                    InterviewPage.CLOSED -> Unit
                }
            }
            if (state.actionLoading) {
                // Keep the visible button spinner while blocking all other controls during the mutation.
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                })
            }
        }
    }
}

@Composable
internal fun InterviewHeader(state: InterviewViewState, onClose: () -> Unit,
    review: Boolean = false, enabled: Boolean = true) {
    val colors = com.oniolla.interviewer.ui.theme.AppTheme.colors
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 20.dp, top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onClose, enabled = enabled, color = Color.Transparent, shape = CircleShape,
                modifier = Modifier.size(36.dp).semantics { contentDescription = "Close interview" }) {
                Box(contentAlignment = Alignment.Center) {
                    if (review) FigmaIcon("chevron", Modifier.size(18.dp).rotate(180f), colors.secondaryText)
                    else PracticeIcon(R.drawable.question_clear_topic, 20.dp, colors.secondaryText)
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(if (review) R.string.interview_review else R.string.interview_session),
                color = colors.primaryText, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            if (state.total > 0) Text(stringResource(R.string.interview_position,
                state.position.coerceIn(1, state.total), state.total), color = colors.secondaryText,
                fontSize = 10.sp, lineHeight = 13.sp)
        }
        Box(Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp).fillMaxWidth().height(5.dp)
            .clip(RoundedCornerShape(99.dp)).background(colors.homeTrack)) {
            val progress = if (state.total == 0) 0f else state.position.toFloat() / state.total
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(colors.link))
        }
    }
}

@Composable
private fun InterviewFinishScreen(state: InterviewViewState, actions: InterviewActions) {
    Scaffold(containerColor = com.oniolla.interviewer.ui.theme.AppTheme.colors.background,
        topBar = { InterviewHeader(state, actions.close, enabled = !state.isBusy) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.Center) {
            PracticeCard(hero = true) {
                Text(stringResource(R.string.interview_all_answered),
                    color = com.oniolla.interviewer.ui.theme.AppTheme.colors.primaryText,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.interview_all_answered_description),
                    color = com.oniolla.interviewer.ui.theme.AppTheme.colors.secondaryText)
            }
            state.error?.let { Text(it, color = com.oniolla.interviewer.ui.theme.AppTheme.colors.red,
                modifier = Modifier.padding(top = 16.dp)) }
            Spacer(Modifier.height(20.dp))
            MainButton(Modifier.fillMaxWidth(), actions.finish, stringResource(R.string.interview_results),
                isLoading = state.actionLoading, textSize = 12.sp)
        }
    }
}

@Composable
private fun InterviewErrorScreen(state: InterviewViewState, actions: InterviewActions) {
    Scaffold(containerColor = com.oniolla.interviewer.ui.theme.AppTheme.colors.background,
        topBar = { PracticeHeader(stringResource(R.string.interview_session), actions.close) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(state.error.orEmpty(), color = com.oniolla.interviewer.ui.theme.AppTheme.colors.secondaryText)
            Spacer(Modifier.height(20.dp))
            MainButton(Modifier.fillMaxWidth(), actions.retry, stringResource(R.string.retry), textSize = 12.sp)
        }
    }
}

@Composable
private fun InterviewSkeleton(state: InterviewViewState) {
    Scaffold(containerColor = com.oniolla.interviewer.ui.theme.AppTheme.colors.background,
        topBar = { InterviewHeader(state, {}, enabled = false) }) { padding ->
        ShimmerAnimation {
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SkeletonSurface(Modifier.fillMaxWidth().height(170.dp), 20) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ShimmerBlock(Modifier.width(110.dp).height(12.dp))
                        ShimmerBlock(Modifier.fillMaxWidth().height(22.dp))
                        ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(22.dp))
                    }
                }
                ShimmerBlock(Modifier.fillMaxWidth().height(42.dp), RoundedCornerShape(15.dp))
                SkeletonSurface(Modifier.fillMaxWidth().height(278.dp), 18) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        ShimmerBlock(Modifier.width(120.dp).height(16.dp))
                        ShimmerBlock(Modifier.fillMaxWidth().height(180.dp), RoundedCornerShape(12.dp))
                    }
                }
                ShimmerBlock(Modifier.fillMaxWidth().height(48.dp), RoundedCornerShape(24.dp))
            }
        }
    }
}
