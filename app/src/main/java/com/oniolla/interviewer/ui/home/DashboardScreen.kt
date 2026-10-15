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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.LinkText
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.data.model.WeakTopic
import com.oniolla.interviewer.domain.DashboardContent
import com.oniolla.interviewer.ui.theme.AppTheme
import java.util.Locale

@Composable
internal fun DashboardScreen(
    state: ContentViewState<DashboardContent>,
    onTopic: (String) -> Unit,
    onSeeTopics: () -> Unit,
    onSeeQuestions: () -> Unit,
    onRetry: () -> Unit,
    onOpenQuestion: (QuestionResponse) -> Unit = {},
    onStartInterview: () -> Unit = {},
    interviewHistory: ContentViewState<com.oniolla.interviewer.data.model.LastInterview?> = ContentViewState.Ready(null)
) {
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
        label = "dashboard loading"
    ) { current ->
        when (current) {
            ContentViewState.Loading, ContentViewState.NotLoaded -> DashboardSkeleton()
            is ContentViewState.Failed -> ContentError(current.message, onRetry)
            is ContentViewState.Ready -> {
                val dashboard = current.content.dashboard
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    item(key = "readiness") { ReadinessCard(current.content, onStartInterview, interviewHistory) }
                    item(key = "summary") {
                        Row(Modifier.padding(top = 18.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(19.dp)) {
                            SummaryCard(Modifier.weight(1f), "code", dashboard.readiness.answered,
                                stringResource(R.string.questions))
                            SummaryCard(Modifier.weight(1f), "topics", dashboard.readiness.practicedTopics,
                                stringResource(R.string.practiced_topics))
                            SummaryCard(Modifier.weight(1f), "calendar", dashboard.dueCount,
                                stringResource(R.string.due_today))
                        }
                    }
                    item(key = "weak_header") {
                        SectionHeader(stringResource(R.string.weak_topics), Modifier.padding(top = 14.dp), onSeeTopics)
                    }
                    item(key = "weak_topics") {
                        if (dashboard.weakTopics.isEmpty()) {
                            EmptySection(stringResource(R.string.no_weak_topics))
                        } else {
                            LazyRow(
                                modifier = Modifier.padding(top = 18.dp),
                                horizontalArrangement = Arrangement.spacedBy(13.dp)
                            ) {
                                items(dashboard.weakTopics, key = { it.topicId }) { topic ->
                                    WeakTopicCard(topic) { onTopic(topic.name) }
                                }
                            }
                        }
                    }
                    item(key = "review_header") {
                        SectionHeader(stringResource(R.string.todays_review), Modifier.padding(top = 24.dp), onSeeQuestions)
                        Spacer(Modifier.height(8.dp))
                    }
                    if (dashboard.todayReview.isEmpty()) {
                        item(key = "reviews_empty") { EmptySection(stringResource(R.string.no_reviews_due)) }
                    } else {
                        items(dashboard.todayReview, key = { "review_${it.id}" }) { question ->
                            ReviewCard(question) { onOpenQuestion(question) }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadinessCard(content: DashboardContent, onStartInterview: () -> Unit,
    interviewHistory: ContentViewState<com.oniolla.interviewer.data.model.LastInterview?>) {
    val colors = AppTheme.colors
    val dashboard = content.dashboard
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.stroke)
    ) {
        Row(
            modifier = Modifier.background(Brush.horizontalGradient(listOf(colors.heroStart, colors.heroEnd)))
                .heightIn(min = 176.dp).padding(top = 16.dp, bottom = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReadinessRing(dashboard.readiness.score)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                val label = when (dashboard.readiness.label) {
                    "BUILDING_FOUNDATIONS" -> stringResource(R.string.readiness_building_foundations)
                    else -> stringResource(R.string.readiness_keep_going)
                }
                Text(label, color = colors.primaryText, fontSize = 17.sp,
                    lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                val specialization = when (content.profile.specialization) {
                    "ANDROID" -> "Android"
                    "IOS" -> "iOS"
                    "DOTNET" -> ".NET"
                    "DATA_ML" -> "Data / ML"
                    else -> content.profile.specialization.orEmpty().lowercase(Locale.ROOT)
                        .replaceFirstChar { it.titlecase(Locale.ROOT) }
                }
                Text(stringResource(R.string.readiness_subtitle, specialization),
                    color = colors.secondaryText, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(20.dp))
                MainButton(
                    modifier = Modifier.widthIn(max = 160.dp).fillMaxWidth().height(38.dp),
                    buttonText = stringResource(when {
                        interviewHistory is ContentViewState.Failed -> R.string.interview_history_retry
                        (interviewHistory as? ContentViewState.Ready)?.content != null -> R.string.interview_continue
                        else -> R.string.start_interview
                    }), textSize = 11.sp,
                    onClick = onStartInterview,
                    isLoading = interviewHistory == ContentViewState.Loading,
                    enabled = interviewHistory != ContentViewState.NotLoaded,
                    gradientColors = if ((interviewHistory as? ContentViewState.Ready)?.content != null)
                        listOf(colors.success, colors.info) else null,
                    leadingIcon = { FigmaIcon("practice", Modifier.size(20.dp)) }
                )
                dashboard.usage?.aiEvaluations?.let { usage ->
                    Text(stringResource(R.string.ai_evaluations_available, usage.remaining),
                        modifier = Modifier.widthIn(max = 160.dp).fillMaxWidth().padding(top = 8.dp), color = colors.secondaryText,
                        fontSize = 8.sp, lineHeight = 10.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ReadinessRing(score: Double) {
    val glow = painterResource(R.drawable.home_figma_glow)
    val progress by animateFloatAsState(percent(score) / 100f, tween(600), label = "readiness score")
    val colors = AppTheme.colors
    Box(Modifier.size(126.dp), contentAlignment = Alignment.Center) {
        Image(glow, contentDescription = null, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.size(90.dp)) {
            val stroke = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(colors.homeTrack, -90f, 360f, false, style = stroke)
            val gradient = Brush.sweepGradient(listOf(colors.leftGradient, colors.rightGradient, colors.leftGradient))
            drawArc(gradient, -90f, progress * 360f, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${percent(score)}%", color = colors.primaryText, fontSize = 20.sp,
                lineHeight = 26.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.readiness), color = colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, icon: String, number: Int, label: String) {
    Surface(modifier.heightIn(min = 64.dp), color = AppTheme.colors.container,
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FigmaIcon(icon, Modifier.size(18.dp),
                if (icon == "calendar") AppTheme.colors.warning else AppTheme.colors.homeIcon)
            Column {
                Text(number.toString(), color = AppTheme.colors.primaryText,
                    fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                Text(label, color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier, onSeeAll: () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = AppTheme.colors.primaryText, fontSize = 15.sp, lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold)
        LinkText(text = stringResource(R.string.see_all), onClick = onSeeAll, lineHeight = 13.sp)
    }
}

@Composable
private fun EmptySection(message: String) {
    Text(message, color = AppTheme.colors.secondaryText, fontSize = 11.sp, lineHeight = 16.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp))
}

@Composable
private fun WeakTopicCard(topic: WeakTopic, onClick: () -> Unit) {
    val color = topicTint(topic.visual)
    Surface(
        onClick = onClick,
        modifier = Modifier.width(108.dp).heightIn(min = 104.dp),
        color = AppTheme.colors.container,
        contentColor = AppTheme.colors.primaryText,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AppTheme.colors.stroke)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TopicIcon(topic.visual, topic.name, Modifier.size(18.dp), badge = false)
                Text(topic.name, color = AppTheme.colors.primaryText, fontSize = 11.sp,
                    lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Text("${percent(topic.mastery)}%", modifier = Modifier.padding(top = 12.dp),
                color = color, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            MasteryBar(topic.mastery, color)
            Text(stringResource(R.string.mastery), color = AppTheme.colors.secondaryText,
                modifier = Modifier.padding(top = 5.dp), fontSize = 8.sp, lineHeight = 10.sp)
        }
    }
}

@Composable
internal fun MasteryBar(mastery: Double, color: Color) {
    val fraction by animateFloatAsState(percent(mastery) / 100f, tween(600), label = "topic mastery")
    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp))
        .background(AppTheme.colors.homeTrack)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
    }
}

@Composable
private fun ReviewCard(question: QuestionResponse, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = AppTheme.colors.container,
        contentColor = AppTheme.colors.primaryText,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, AppTheme.colors.stroke)
    ) {
        Row(Modifier.heightIn(min = 58.dp).padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TopicIcon(question.topic.visual, question.topic.name, Modifier.size(38.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(question.question, color = AppTheme.colors.primaryText, fontSize = 12.sp,
                    lineHeight = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text("${question.topic.name} • ${question.difficulty.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }}",
                    color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.due_today), color = AppTheme.colors.warning,
                modifier = Modifier.background(AppTheme.colors.warning.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 9.sp, lineHeight = 12.sp)
            Spacer(Modifier.width(8.dp))
            FigmaIcon("chevron", Modifier.size(18.dp), AppTheme.colors.homeIcon)
        }
    }
}

@Composable
private fun DashboardSkeleton() {
    val loadingDescription = stringResource(R.string.content_loading)
    ShimmerAnimation {
        LazyColumn(Modifier.fillMaxSize().semantics { stateDescription = loadingDescription },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            item {
                SkeletonSurface(Modifier.fillMaxWidth().height(176.dp), 20) {
                    Row(Modifier.fillMaxSize().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBlock(Modifier.size(90.dp), CircleShape)
                        Spacer(Modifier.width(34.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShimmerBlock(Modifier.fillMaxWidth().height(18.dp))
                            ShimmerBlock(Modifier.fillMaxWidth().height(30.dp))
                            ShimmerBlock(Modifier.fillMaxWidth().height(38.dp), RoundedCornerShape(19.dp))
                            ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(8.dp))
                        }
                    }
                }
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(19.dp)) {
                    repeat(3) {
                        SkeletonSurface(Modifier.weight(1f).height(64.dp), 14) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(16.dp))
                                ShimmerBlock(Modifier.fillMaxWidth().height(9.dp))
                            }
                        }
                    }
                }
                ShimmerBlock(Modifier.padding(top = 14.dp).width(100.dp).height(19.dp))
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    repeat(3) {
                        SkeletonSurface(Modifier.weight(1f).height(104.dp), 14) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ShimmerBlock(Modifier.fillMaxWidth().height(14.dp))
                                ShimmerBlock(Modifier.width(36.dp).height(20.dp))
                                ShimmerBlock(Modifier.fillMaxWidth().height(5.dp))
                                ShimmerBlock(Modifier.width(30.dp).height(8.dp))
                            }
                        }
                    }
                }
                ShimmerBlock(Modifier.padding(top = 24.dp, bottom = 8.dp).width(114.dp).height(19.dp))
            }
            items(3) {
                SkeletonSurface(Modifier.fillMaxWidth().height(58.dp), 13) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBlock(Modifier.size(38.dp), RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimmerBlock(Modifier.fillMaxWidth(0.8f).height(12.dp))
                            ShimmerBlock(Modifier.fillMaxWidth(0.55f).height(9.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
internal fun SkeletonSurface(modifier: Modifier, radius: Int, content: @Composable () -> Unit) {
    Surface(modifier, color = AppTheme.colors.container, shape = RoundedCornerShape(radius.dp),
        border = BorderStroke(1.dp, AppTheme.colors.stroke), content = content)
}
