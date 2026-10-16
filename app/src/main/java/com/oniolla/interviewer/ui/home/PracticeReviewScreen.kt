package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.EvaluationKeyPoint
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
internal fun PracticeReviewScreen(state: PracticeViewState, onBack: () -> Unit,
    onContinue: () -> Unit = onBack, continueText: String? = null, loading: Boolean = false,
    header: (@Composable () -> Unit)? = null, error: String? = null) {
    val result = state.result ?: return
    // Each evaluation opens at the top; do not restore another visit's saved scroll position.
    val scrollState = remember(result.id) { ScrollState(0) }
    Scaffold(containerColor = AppTheme.colors.background,
        bottomBar = { ReviewActions {
            error?.let { Text(it, color = AppTheme.colors.red, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp)) }
            MainButton(Modifier.fillMaxWidth().height(50.dp), buttonText = continueText ?: stringResource(R.string.practice_back_to_question),
                onClick = onContinue, isLoading = loading, textSize = 11.sp)
        } },
        topBar = { if (header != null) header() else PracticeHeader(stringResource(R.string.practice_review_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(scrollState).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(17.dp))
            PracticeScoreCard(state)
            Spacer(Modifier.height(18.dp))
            if (result.keyPoints.isNotEmpty()) {
                PracticeCard(corner = 15.dp, contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)) {
                    Text(stringResource(R.string.practice_key_points), color = AppTheme.colors.primaryText,
                        fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    result.keyPoints.forEachIndexed { index, point ->
                        key(point.id, index) { KeyPointRow(point) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            ReviewAnswer(state, result.userAnswer ?: state.answer)
            if (!result.overallFeedback.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                PracticeInfo(stringResource(R.string.practice_feedback), result.overallFeedback)
            }
            if (result.strengths.isNotEmpty() || result.improvements.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                PracticeCard(corner = 15.dp) {
                    if (result.strengths.isNotEmpty()) FeedbackList(stringResource(R.string.practice_strengths), result.strengths)
                    if (result.strengths.isNotEmpty() && result.improvements.isNotEmpty()) Spacer(Modifier.height(16.dp))
                    if (result.improvements.isNotEmpty()) FeedbackList(stringResource(R.string.practice_improvements), result.improvements)
                }
            }
            if (result.referenceAnswerAvailable && !result.referenceAnswer.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                PracticeCard(Modifier.fillMaxWidth().heightIn(min = 102.dp), corner = 15.dp) {
                    Text(stringResource(R.string.practice_reference), color = AppTheme.colors.primaryText,
                        fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Text(result.referenceAnswer, color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
internal fun PracticeLimitScreen(state: PracticeViewState, onBack: () -> Unit, onPlans: () -> Unit,
    continueText: String? = null, loading: Boolean = false, header: (@Composable () -> Unit)? = null,
    error: String? = null) {
    Scaffold(containerColor = AppTheme.colors.background,
        bottomBar = { ReviewActions {
            MainButton(Modifier.fillMaxWidth().height(50.dp), buttonText = stringResource(R.string.practice_view_plans),
                onClick = onPlans, textSize = 11.sp)
            Spacer(Modifier.height(16.dp))
            error?.let { Text(it, color = AppTheme.colors.red, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp)) }
            if (loading) Box(Modifier.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = AppTheme.colors.info)
            } else Surface(onClick = onBack, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(23.dp),
                color = AppTheme.colors.container, border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(continueText ?: stringResource(R.string.practice_back_to_question), color = AppTheme.colors.primaryText,
                        fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(stringResource(R.string.practice_upgrade_anytime), Modifier.fillMaxWidth().padding(top = 14.dp),
                color = AppTheme.colors.primaryText, fontSize = 8.5.sp, lineHeight = 11.sp, textAlign = TextAlign.Center)
        } },
        topBar = { if (header != null) header() else PracticeHeader(stringResource(R.string.practice_review_title), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(17.dp))
            PracticeScoreCard(state, limit = true)
            Spacer(Modifier.height(18.dp))
            ReviewAnswer(state, state.answer)
            Spacer(Modifier.height(16.dp))
            PracticeInfo(stringResource(R.string.practice_feedback_locked), stringResource(R.string.practice_upgrade_description))
            Spacer(Modifier.height(14.dp))
            PracticeCard(Modifier.fillMaxWidth().heightIn(min = 84.dp), corner = 15.dp) {
                Text(stringResource(R.string.practice_reference_locked), color = AppTheme.colors.primaryText,
                    fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.practice_reference_locked_description), color = AppTheme.colors.secondaryText,
                    fontSize = 8.5.sp, lineHeight = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ReviewActions(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = AppTheme.colors.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
            content = content)
    }
}

@Composable
private fun PracticeScoreCard(state: PracticeViewState, limit: Boolean = false) {
    val score = state.result?.displayedScore ?: 0
    val track = AppTheme.colors.homeTrack
    PracticeCard(Modifier.fillMaxWidth().heightIn(min = if (limit) 166.dp else 142.dp), hero = true, corner = 20.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawCircle(track, radius = 37.dp.toPx(), style = Stroke(7.dp.toPx())) }
                PracticeIcon(R.drawable.practice_review_ring, 92.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (limit) "0" else score.toString(), color = AppTheme.colors.primaryText,
                        fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                    Text(if (limit) stringResource(R.string.practice_reviews_left) else "/100",
                        color = AppTheme.colors.secondaryText, fontSize = if (limit) 8.sp else 9.sp, lineHeight = 12.sp)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PracticeIcon(R.drawable.practice_ai_small, 18.dp)
                    Text(stringResource(if (limit) R.string.practice_limit_label else R.string.practice_evaluated),
                        color = AppTheme.colors.info, fontSize = 9.sp, lineHeight = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (limit) R.string.practice_limit_title else when {
                    score >= 90 -> R.string.practice_score_excellent
                    score >= 80 -> R.string.practice_score_good
                    score >= 50 -> R.string.practice_score_improving
                    else -> R.string.practice_score_practicing
                }), color = AppTheme.colors.primaryText, fontSize = if (limit) 18.sp else 21.sp,
                    lineHeight = if (limit) 22.sp else 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(if (limit) stringResource(R.string.practice_limit_description) else state.result?.overallFeedback.orEmpty(),
                    color = AppTheme.colors.secondaryText, fontSize = if (limit) 8.5.sp else 10.sp,
                    lineHeight = if (limit) 12.sp else 14.sp, maxLines = if (limit) 6 else 3,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ReviewAnswer(state: PracticeViewState, answer: String) {
    PracticeCard(Modifier.fillMaxWidth().heightIn(min = 78.dp), corner = 15.dp,
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.practice_your_answer), Modifier.weight(1f),
                color = AppTheme.colors.primaryText, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(if (state.mode == AnswerMode.VOICE) R.string.practice_voice_transcript else R.string.practice_typed_answer),
                color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(answer, color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun KeyPointRow(point: EvaluationKeyPoint) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val coverage = point.coveragePercent.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.toInt() ?: 0
    val color = when (point.status) {
        "STRONG" -> AppTheme.colors.success
        "PARTIAL" -> AppTheme.colors.info
        "WEAK" -> AppTheme.colors.warning
        "MISSING" -> AppTheme.colors.red
        else -> AppTheme.colors.secondaryText
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .clickable(enabled = !point.comment.isNullOrBlank()) { expanded = !expanded }.padding(vertical = 6.5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text(point.description, Modifier.weight(1f), color = AppTheme.colors.secondaryText, fontSize = 10.sp, lineHeight = 13.sp)
            Text(stringResource(R.string.practice_coverage, coverage), color = color, fontSize = 11.sp, lineHeight = 13.sp)
        }
        AnimatedVisibility(expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(point.comment.orEmpty(), Modifier.padding(top = 8.dp, bottom = 6.dp),
                color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun FeedbackList(title: String, items: List<String>) {
    Text(title, color = AppTheme.colors.primaryText, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
    items.filter { it.isNotBlank() }.forEach { item ->
        Text("• $item", Modifier.padding(top = 8.dp), color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 13.sp)
    }
}
