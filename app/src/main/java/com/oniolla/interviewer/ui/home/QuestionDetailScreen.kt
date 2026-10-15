package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.LinkText
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.data.model.QuestionProgress
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionDetailScreen(
    state: QuestionDetailState,
    onBack: () -> Unit = {},
    onReveal: () -> Unit = {},
    onRetryUsage: () -> Unit = {},
    onToggleSaved: (Long) -> Unit = {},
    onPractice: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {}
) {
    Scaffold(containerColor = AppTheme.colors.background,
        topBar = { QuestionDetailHeader(onBack) }, bottomBar = bottomBar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailChip(state.question.topic.name, topicTint(state.question.topic.visual))
                    DetailChip(difficultyLabel(state.question.difficulty), difficultyTint(state.question.difficulty))
                }
                DetailLastScore(state.question.progress, Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(14.dp))
            QuestionHero(state, onReveal, onToggleSaved, onPractice)
            state.usageError?.let { message ->
                Column(Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = AppTheme.colors.red, fontSize = 11.sp, lineHeight = 15.sp)
                    LinkText(text = stringResource(R.string.retry), onClick = onRetryUsage)
                }
            }
            AnimatedVisibility(
                visible = state.isAnswerVisible,
                enter = expandVertically(tween(300), expandFrom = Alignment.Top) + fadeIn(tween(250)),
                exit = shrinkVertically(tween(300), shrinkTowards = Alignment.Top) + fadeOut(tween(200))
            ) {
                Column(Modifier.padding(top = 22.dp)) { QuestionAnswerCard(state, onReveal) }
            }
            val tags = state.question.tags.filter { it.isNotBlank() }.distinct()
            if (tags.isNotEmpty()) {
                Text(stringResource(R.string.question_related_tags), color = AppTheme.colors.primaryText,
                    fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val colors = listOf(AppTheme.colors.info, AppTheme.colors.link, Color(0xFFF44AA7))
                    tags.forEachIndexed { index, tag -> DetailChip(tag, colors[index % colors.size]) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun QuestionDetailHeader(onBack: () -> Unit) {
    val backDescription = stringResource(R.string.question_detail_back)
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onBack, modifier = Modifier.size(28.dp).semantics {
            contentDescription = backDescription
        }, color = Color.Transparent, shape = CircleShape) {
            Box(contentAlignment = Alignment.Center) {
                FigmaIcon("chevron", Modifier.size(18.dp).rotate(180f), AppTheme.colors.secondaryText)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.question_detail_title), color = AppTheme.colors.primaryText,
            fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailChip(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f))) {
        Box(Modifier.heightIn(min = 24.dp).padding(horizontal = 9.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center) {
            Text(label, color = AppTheme.colors.primaryText, fontSize = 10.sp, lineHeight = 13.sp,
                fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DetailLastScore(progress: QuestionProgress?, modifier: Modifier = Modifier) {
    if (progress == null || progress.attempts <= 0 || !progress.lastScore.isFinite()) return
    val score = progress.lastScore.coerceIn(0.0, 100.0).roundToInt()
    val color = when {
        score >= 80 -> AppTheme.colors.success
        score >= 50 -> AppTheme.colors.warning
        else -> AppTheme.colors.red
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.heightIn(min = 24.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp).background(color, CircleShape))
            Text(stringResource(R.string.question_last_score), color = AppTheme.colors.secondaryText,
                fontSize = 9.sp, lineHeight = 13.sp, maxLines = 1)
            Text(stringResource(R.string.question_last_score_value, score), color = color,
                fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun QuestionHero(state: QuestionDetailState, onReveal: () -> Unit, onToggleSaved: (Long) -> Unit, onPractice: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFF47629A))) {
        Box(Modifier.background(Brush.horizontalGradient(listOf(AppTheme.colors.heroStart, AppTheme.colors.heroEnd)))) {
            Image(painterResource(R.drawable.home_figma_glow), null,
                Modifier.align(Alignment.TopEnd).padding(top = 17.dp, end = 18.dp).size(110.dp))
            Column(Modifier.fillMaxWidth().heightIn(min = 167.dp).padding(20.dp)) {
                Text(state.question.question, color = AppTheme.colors.primaryText,
                    fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                QuestionActions(state, onReveal, onToggleSaved, onPractice)
            }
        }
    }
}

@Composable
private fun QuestionActions(state: QuestionDetailState, onReveal: () -> Unit, onToggleSaved: (Long) -> Unit, onPractice: () -> Unit) {
    val reveal: @Composable (Modifier) -> Unit = { modifier ->
        val hideAnswer = state.isAnswerVisible && state.answer is ContentViewState.Ready
        DetailAction(modifier,
            stringResource(when {
                hideAnswer -> R.string.question_hide_answer
                state.question.answerRevealed -> R.string.question_show_answer
                else -> R.string.question_reveal_answer
            }),
            if (state.question.answerRevealed) stringResource(R.string.question_already_revealed)
            else stringResource(R.string.question_reveals_remaining, state.revealsRemaining),
            enabled = state.canReveal, loading = state.answer == ContentViewState.Loading,
            usageLoading = state.isUsageLoading && !state.question.answerRevealed,
            hideIcon = hideAnswer, onClick = onReveal)
    }
    val practice: @Composable (Modifier) -> Unit = { modifier ->
        DetailAction(modifier, stringResource(R.string.question_ai_practice),
            stringResource(R.string.question_ai_reviews_remaining, state.aiReviewsRemaining),
            enabled = state.canPractice, usageLoading = state.isUsageLoading, onClick = onPractice)
    }
    val saved: @Composable () -> Unit = {
        Box(Modifier.padding(top = 3.dp)) {
            QuestionSaveButton(state.question, onToggleSaved, iconSize = 20.dp, touchSize = 32.dp)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 290.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    reveal(Modifier.weight(1f))
                    saved()
                }
                practice(Modifier.fillMaxWidth().padding(end = 40.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reveal(Modifier.weight(1.05f))
                practice(Modifier.weight(1f))
                saved()
            }
        }
    }
}

@Composable
private fun DetailAction(modifier: Modifier, label: String, remaining: String, enabled: Boolean,
    loading: Boolean = false, usageLoading: Boolean, hideIcon: Boolean = false, onClick: () -> Unit) {
    val opacity by animateFloatAsState(if (enabled || loading) 1f else 0.4f, tween(200), label = "question action enabled")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        MainButton(Modifier.fillMaxWidth().height(38.dp).alpha(opacity), onClick = onClick,
            buttonText = label, loadingButtonText = stringResource(R.string.question_revealing_answer),
            isLoading = loading, enabled = enabled, textSize = 11.sp,
            leadingIcon = {
                if (hideIcon) Image(painterResource(R.drawable.question_clear_topic), null,
                    Modifier.size(20.dp), colorFilter = ColorFilter.tint(Color.White))
                else FigmaIcon("practice", Modifier.size(20.dp))
            },
            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp))
        Spacer(Modifier.height(4.dp))
        if (usageLoading) ShimmerAnimation { ShimmerBlock(Modifier.width(70.dp).height(9.dp)) }
        else Text(remaining, fontSize = 7.5.sp, lineHeight = 9.sp,
            color = AppTheme.colors.secondaryText.copy(alpha = 0.92f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun QuestionAnswerCard(state: QuestionDetailState, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().animateContentSize(tween(250)),
        shape = RoundedCornerShape(16.dp), color = AppTheme.colors.container,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        AnimatedContent(state.answer, contentKey = { it::class },
            transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(250))).using(null) },
            label = "reference answer") { answer ->
            when (answer) {
                ContentViewState.NotLoaded, ContentViewState.Loading -> AnswerSkeleton()
                is ContentViewState.Ready -> Column(Modifier.fillMaxWidth().heightIn(min = 186.dp).padding(16.dp)) {
                    Text(stringResource(R.string.question_answer_title), color = AppTheme.colors.primaryText,
                        fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(18.dp))
                    AnswerText(answer.content.referenceAnswer)
                }
                is ContentViewState.Failed -> Column(Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.question_answer_failed), color = AppTheme.colors.primaryText,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(answer.message, color = AppTheme.colors.red, fontSize = 11.sp, lineHeight = 15.sp)
                    LinkText(text = stringResource(R.string.retry), enabled = state.canReveal, onClick = onRetry)
                }
            }
        }
    }
}

@Composable
private fun AnswerText(answer: String) {
    val lines = answer.lines().filter { it.isNotBlank() }
    val bulletList = lines.isNotEmpty() && lines.all {
        it.trimStart().startsWith("- ") || it.trimStart().startsWith("* ") || it.trimStart().startsWith("• ")
    }
    if (bulletList) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 5.dp).size(6.dp).background(AppTheme.colors.info, CircleShape))
                    Text(line.trimStart().drop(2), color = AppTheme.colors.secondaryText,
                        fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    } else Text(answer, color = AppTheme.colors.secondaryText, fontSize = 11.sp, lineHeight = 17.sp)
}

@Composable
private fun AnswerSkeleton() {
    ShimmerAnimation {
        Column(Modifier.fillMaxWidth().height(186.dp).padding(16.dp)) {
            ShimmerBlock(Modifier.width(75.dp).height(20.dp))
            Spacer(Modifier.height(18.dp))
            repeat(3) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerBlock(Modifier.fillMaxWidth().height(12.dp))
                    ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(10.dp))
                }
                if (it < 2) Spacer(Modifier.height(12.dp))
            }
        }
    }
}
