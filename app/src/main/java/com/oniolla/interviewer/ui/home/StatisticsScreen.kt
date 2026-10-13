package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.LinkText
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.Readiness
import com.oniolla.interviewer.data.model.StatsSummary
import com.oniolla.interviewer.data.model.WeakTopic
import com.oniolla.interviewer.domain.StatsDates
import com.oniolla.interviewer.ui.theme.AppTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun StatisticsScreen(
    state: ContentViewState<StatisticsContent>,
    onRetry: () -> Unit,
    onChangeWeek: (Int) -> Unit,
    onTopicSelected: (Long, String) -> Unit
) {
    AnimatedContent(targetState = state, contentKey = { it::class },
        transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
        label = "statistics loading") { current ->
        when (current) {
            ContentViewState.NotLoaded, ContentViewState.Loading -> StatisticsSkeleton()
            is ContentViewState.Failed -> ContentError(current.message, onRetry)
            is ContentViewState.Ready -> {
                val content = current.content
                LazyColumn(Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    item("readiness") { StatisticsReadiness(content.response.readiness) }
                    item("summary") {
                        StatisticsSummary(content.response.summary, Modifier.padding(top = 18.dp))
                    }
                    item("study_progress") {
                        StudyProgressChart(content, onChangeWeek, onRetry, Modifier.padding(top = 20.dp))
                    }
                    item("mastery_heading") {
                        Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.stats_topic_mastery),
                                color = AppTheme.colors.primaryText, fontSize = 15.sp,
                                lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.stats_tap_topic),
                                color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
                        }
                    }
                    if (content.response.topics.isEmpty()) {
                        item("mastery_empty") {
                            Text(stringResource(R.string.stats_topics_empty), fontSize = 11.sp, lineHeight = 16.sp,
                                color = AppTheme.colors.secondaryText, modifier = Modifier.padding(vertical = 16.dp))
                        }
                    } else items(content.response.topics.distinctBy { it.topicId }, key = { "topic_${it.topicId}" }) { topic ->
                        StatisticsTopicMastery(topic) { onTopicSelected(topic.topicId, topic.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsReadiness(readiness: Readiness) {
    Surface(Modifier.fillMaxWidth(), color = Color.Transparent, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Row(Modifier.background(Brush.horizontalGradient(
            listOf(AppTheme.colors.heroStart, AppTheme.colors.heroEnd)))
            .heightIn(min = 146.dp).padding(start = 6.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            StatisticsReadinessRing(readiness.score)
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.stats_overall_readiness), fontSize = 12.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium, color = AppTheme.colors.primaryText)
                readinessComparison(readiness)?.let { change ->
                    val signed = (if (change > 0) "+" else "") + formatStatDecimal(change)
                    Text(stringResource(R.string.stats_weekly_comparison, signed),
                        color = when {
                            change > 0 -> AppTheme.colors.success
                            change < 0 -> AppTheme.colors.red
                            else -> AppTheme.colors.secondaryText
                        }, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(stringResource(if (readiness.label == "BUILDING_FOUNDATIONS")
                    R.string.readiness_building_foundations else R.string.stats_keep_streak),
                    color = AppTheme.colors.secondaryText, fontSize = 10.sp, lineHeight = 13.sp)
            }
        }
    }
}

@Composable
private fun StatisticsReadinessRing(score: Double) {
    val colors = AppTheme.colors
    val progress by animateFloatAsState(percent(score) / 100f, tween(600), label = "statistics readiness")
    val description = stringResource(R.string.stats_readiness_percent, percent(score))
    Box(Modifier.size(120.dp).semantics { stateDescription = description }, contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.home_figma_glow), contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.size(96.dp)) {
            val stroke = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
            repeat(4) { segment ->
                val start = -85f + segment * 90f
                drawArc(colors.homeTrack, start, 80f, false, style = stroke)
                val fraction = (progress * 4 - segment).coerceIn(0f, 1f)
                if (fraction > 0) drawArc(colors.accent, start, fraction * 80f, false, style = stroke)
            }
        }
        Text("${percent(score)}%", color = colors.primaryText, fontSize = 20.sp, lineHeight = 26.sp,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatisticsSummary(summary: StatsSummary, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatisticsMetric(Modifier.weight(1f), "calendar", summary.streakDays.toString(),
            stringResource(R.string.stats_streak), AppTheme.colors.warning)
        StatisticsMetric(Modifier.weight(1f), "code", summary.answered.toString(),
            stringResource(R.string.stats_answered), AppTheme.colors.leftGradient)
        StatisticsMetric(Modifier.weight(1f), "bookmark", summary.dueCount.toString(),
            stringResource(R.string.favorites_filter_due), AppTheme.colors.link)
        val seconds = summary.averageStudySeconds
        val average = if (seconds == null || !seconds.isFinite() || seconds < 0)
            stringResource(R.string.stats_unavailable)
        else if (seconds < 60) stringResource(R.string.stats_seconds, seconds.roundToInt())
        else stringResource(R.string.stats_minutes, (seconds / 60).toInt())
        StatisticsMetric(Modifier.weight(1.1f), "stats", average,
            stringResource(R.string.stats_average), AppTheme.colors.accent,
            if (seconds == null) stringResource(R.string.stats_time_unavailable) else null)
    }
}

@Composable
private fun StatisticsMetric(modifier: Modifier, icon: String, value: String, label: String,
    color: Color, description: String? = null) {
    Surface(modifier.heightIn(min = 66.dp).semantics {
        if (description != null) contentDescription = description
    }, color = AppTheme.colors.container, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Column(Modifier.padding(start = 9.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FigmaIcon(icon, Modifier.size(18.dp), color)
                Text(value, modifier = Modifier.weight(1f), color = color, fontSize = 15.sp, lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(label, modifier = Modifier.fillMaxWidth(), color = AppTheme.colors.secondaryText,
                fontSize = 9.sp, lineHeight = 12.sp, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StudyProgressChart(content: StatisticsContent, onChangeWeek: (Int) -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = AppTheme.colors.container, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stats_study_progress), modifier = Modifier.weight(1f),
                    color = AppTheme.colors.primaryText, fontSize = 14.sp, lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold)
                WeekButton(-1, true) { onChangeWeek(-1) }
                Text(StatsDates.weekLabel(content.weekStart), color = AppTheme.colors.secondaryText,
                    fontSize = 9.sp, lineHeight = 12.sp, maxLines = 1)
                WeekButton(1, content.canGoToNextWeek) { onChangeWeek(1) }
            }
            when {
                content.isChartLoading -> ShimmerAnimation {
                    Row(Modifier.fillMaxWidth().height(114.dp), horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.Bottom) {
                        repeat(7) { ShimmerBlock(Modifier.width(24.dp).height((24 + it * 10).dp), RoundedCornerShape(6.dp)) }
                    }
                }
                content.chartError != null -> Column(Modifier.fillMaxWidth().heightIn(min = 114.dp).padding(12.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(content.chartError, color = AppTheme.colors.red, fontSize = 10.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    LinkText(text = stringResource(R.string.retry), onClick = onRetry)
                }
                else -> {
                    val days = StatsDates.weekDays(content.weekStart, content.response.studyProgress)
                    val maximum = days.maxOf { it.answered }.coerceAtLeast(1)
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth().height(90.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.Bottom) {
                                days.forEachIndexed { index, day ->
                                    val fraction by animateFloatAsState(day.answered.toFloat() / maximum,
                                        tween(600), label = "daily activity")
                                    val description = stringResource(R.string.stats_day_answered, day.date, day.answered)
                                    Column(Modifier.weight(1f).semantics { contentDescription = description },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (day.answered > 0) Text(day.answered.toString(), color = AppTheme.colors.secondaryText,
                                            fontSize = 8.sp, lineHeight = 10.sp)
                                        Box(Modifier.width(24.dp).height(if (day.answered == 0) 2.dp else (74 * fraction).dp)
                                            .background(if (day.answered == 0) AppTheme.colors.homeTrack
                                                else if (index < 4) AppTheme.colors.leftGradient else AppTheme.colors.link,
                                                RoundedCornerShape(6.dp)))
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
                                days.forEach { day ->
                                    Text(StatsDates.dayLabel(day.date), modifier = Modifier.weight(1f),
                                        color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 10.sp,
                                        textAlign = TextAlign.Center)
                                }
                            }
                        }
                        if (days.all { it.answered == 0 }) Text(stringResource(R.string.stats_week_empty),
                            modifier = Modifier.align(Alignment.Center).padding(bottom = 20.dp),
                            color = AppTheme.colors.secondaryText, fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekButton(direction: Int, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (direction < 0) R.string.stats_previous_week else R.string.stats_next_week)
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp).semantics {
        contentDescription = description
    }, shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) {
            FigmaIcon("chevron", Modifier.size(14.dp).rotate(if (direction < 0) 180f else 0f),
                AppTheme.colors.homeIcon.copy(alpha = if (enabled) 1f else 0.3f))
        }
    }
}

@Composable
private fun StatisticsTopicMastery(topic: WeakTopic, onClick: () -> Unit) {
    val description = stringResource(R.string.stats_topic_percent, topic.name, percent(topic.mastery))
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(27.dp).semantics { stateDescription = description },
        shape = RoundedCornerShape(8.dp), color = Color.Transparent) {
        Row(Modifier.heightIn(min = 27.dp).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(topic.name, modifier = Modifier.width(90.dp), color = AppTheme.colors.primaryText,
                fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) { MasteryBar(topic.mastery, topicTint(topic.visual)) }
            Text("${percent(topic.mastery)}%", modifier = Modifier.width(30.dp).padding(start = 4.dp),
                color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp, textAlign = TextAlign.End)
            Spacer(Modifier.width(4.dp))
            FigmaIcon("chevron", Modifier.size(14.dp), AppTheme.colors.homeIcon)
        }
    }
}

internal fun readinessComparison(readiness: Readiness): Double? =
    readiness.changeSinceLastWeekPoints?.takeIf {
        it.isFinite() && readiness.previousWeekScore?.isFinite() == true
    }

private fun formatStatDecimal(value: Double): String =
    if (value % 1 == 0.0) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

@Composable
private fun StatisticsSkeleton() {
    val description = stringResource(R.string.content_loading)
    ShimmerAnimation {
        LazyColumn(Modifier.fillMaxSize().semantics { stateDescription = description },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            item {
                SkeletonSurface(Modifier.fillMaxWidth().height(146.dp), 20) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        ShimmerBlock(Modifier.size(96.dp), CircleShape)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            ShimmerBlock(Modifier.fillMaxWidth().height(12.dp))
                            ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(11.dp))
                            ShimmerBlock(Modifier.fillMaxWidth().height(10.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) {
                        SkeletonSurface(Modifier.weight(1f).height(66.dp), 14) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShimmerBlock(Modifier.fillMaxWidth().height(18.dp))
                                ShimmerBlock(Modifier.fillMaxWidth().height(9.dp))
                            }
                        }
                    }
                }
                SkeletonSurface(Modifier.fillMaxWidth().padding(top = 20.dp).height(164.dp), 16) {
                    Column(Modifier.padding(14.dp)) {
                        ShimmerBlock(Modifier.width(120.dp).height(14.dp))
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.Bottom) {
                            repeat(7) { ShimmerBlock(Modifier.width(24.dp).height((24 + it * 10).dp), RoundedCornerShape(6.dp)) }
                        }
                    }
                }
                ShimmerBlock(Modifier.padding(top = 26.dp, bottom = 8.dp).width(120.dp).height(19.dp))
                repeat(6) {
                    Row(Modifier.fillMaxWidth().height(27.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerBlock(Modifier.width(90.dp).height(10.dp))
                        ShimmerBlock(Modifier.weight(1f).height(6.dp))
                        ShimmerBlock(Modifier.width(30.dp).height(9.dp))
                    }
                }
            }
        }
    }
}
