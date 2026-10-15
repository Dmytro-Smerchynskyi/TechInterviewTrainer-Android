package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.theme.AppTheme
import kotlin.math.roundToInt

@Composable
internal fun InterviewSummaryScreen(state: InterviewViewState, actions: InterviewActions) {
    val interview = state.interview ?: return
    val score = interview.overallScore?.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.roundToInt() ?: 0
    val known = interview.questions.count { (it.score ?: it.evaluation?.displayedScore?.toDouble() ?: 0.0) >= 80 }
    val partly = interview.questions.count { (it.score ?: it.evaluation?.displayedScore?.toDouble() ?: 0.0) in 50.0..<80.0 }
    val missed = interview.questions.size - known - partly
    val answered = interview.questions.count { it.evaluation != null || it.answer != null || it.score != null }
    val colors = AppTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.interview_summary), color = colors.primaryText, fontSize = 26.sp,
            lineHeight = 33.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
        Text(stringResource(R.string.interview_summary_subtitle), color = colors.secondaryText,
            fontSize = 11.sp, lineHeight = 14.sp)
        Spacer(Modifier.height(16.dp))
        PracticeCard(hero = true, corner = 20.dp, contentPadding = PaddingValues(20.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Image(painterResource(R.drawable.interview_summary_glow), null,
                    Modifier.size(100.dp).align(Alignment.TopEnd))
                Column {
                    Text(stringResource(R.string.interview_summary_score, score), color = colors.primaryText,
                        fontSize = 38.sp, lineHeight = 49.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (score >= 70) R.string.interview_progress_good else R.string.interview_progress_keep),
                        color = colors.info, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.interview_summary_answered, answered, interview.questions.size),
                        color = colors.secondaryText, fontSize = 11.sp, lineHeight = 14.sp)
                    Spacer(Modifier.height(18.dp))
                    MasteryBar(score.toDouble(), colors.success)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(19.dp)) {
            SummaryCount(Modifier.weight(1f), known, R.string.interview_knew, R.drawable.interview_check, colors.success)
            SummaryCount(Modifier.weight(1f), partly, R.string.interview_partly, R.drawable.interview_partly, colors.warning)
            SummaryCount(Modifier.weight(1f), missed, R.string.interview_missed, R.drawable.question_clear_topic, colors.red)
        }
        when (val summary = state.summary) {
            ContentViewState.Loading, ContentViewState.NotLoaded -> SummarySkeleton()
            is ContentViewState.Failed -> {
                Spacer(Modifier.height(24.dp))
                Surface(onClick = actions.retrySummary, shape = RoundedCornerShape(14.dp), color = colors.container) {
                    Text(stringResource(R.string.interview_summary_retry), Modifier.fillMaxWidth().padding(16.dp),
                        color = colors.link, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            is ContentViewState.Ready -> {
                val weak = summary.content.weakTopics
                val due = summary.content.summary.dueCount
                if (weak.isNotEmpty()) {
                    SummaryHeading(stringResource(R.string.interview_weakest))
                    // Wrap instead of squeezing or truncating a large number of topics into one row.
                    weak.take(3).chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { topic ->
                                val tint = topicTint(topic.visual)
                                Surface(onClick = { actions.topic(topic.topicId, topic.name) },
                                    modifier = Modifier.weight(1f), color = colors.container,
                                    shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, colors.stroke)) {
                                    Column(Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TopicIcon(topic.visual, topic.name, Modifier.size(18.dp), badge = false)
                                            Spacer(Modifier.width(6.dp))
                                            Text(topic.name, color = colors.primaryText, fontSize = 11.sp,
                                                lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        Text("${percent(topic.mastery)}%", color = tint, fontSize = 17.sp,
                                            lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(6.dp))
                                        MasteryBar(topic.mastery, tint)
                                        Spacer(Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (weak.isNotEmpty() || due > 0) {
                    SummaryHeading(stringResource(R.string.interview_recommended))
                    if (weak.isNotEmpty()) {
                        SummaryRecommendation(stringResource(R.string.interview_review_weak),
                            stringResource(R.string.interview_weak_count, weak.size), "code", colors.link, actions.weak)
                    }
                    if (due > 0) {
                        if (weak.isNotEmpty()) Spacer(Modifier.height(10.dp))
                        SummaryRecommendation(stringResource(R.string.interview_spaced),
                            stringResource(R.string.interview_due_count, due), "calendar", colors.warning, actions.due)
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        MainButton(Modifier.fillMaxWidth().height(40.dp), actions.close,
            stringResource(R.string.interview_finish), textSize = 11.sp)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SummaryCount(modifier: Modifier, count: Int, label: Int, icon: Int, color: Color) {
    PracticeCard(modifier.heightIn(min = 66.dp), corner = 14.dp, contentPadding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PracticeIcon(icon, 18.dp, color)
            Spacer(Modifier.width(8.dp))
            Text(count.toString(), color = color, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
        }
        Text(stringResource(label), Modifier.padding(start = 26.dp, top = 4.dp),
            color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
    }
}

@Composable
private fun SummaryHeading(text: String) {
    Text(text, color = AppTheme.colors.primaryText, fontSize = 15.sp, lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 28.dp, bottom = 12.dp))
}

@Composable
private fun SummaryRecommendation(title: String, subtitle: String, icon: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = AppTheme.colors.container,
        shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Row(Modifier.heightIn(min = 64.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) { FigmaIcon(icon, Modifier.size(20.dp), color) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = AppTheme.colors.primaryText, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
            }
            FigmaIcon("chevron", Modifier.size(16.dp), AppTheme.colors.homeIcon)
        }
    }
}

@Composable
private fun SummarySkeleton() {
    ShimmerAnimation {
        Column(Modifier.padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBlock(Modifier.width(110.dp).height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) { ShimmerBlock(Modifier.weight(1f).height(92.dp), RoundedCornerShape(14.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            ShimmerBlock(Modifier.width(140.dp).height(18.dp))
            repeat(2) { ShimmerBlock(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(13.dp)) }
        }
    }
}
