package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.domain.TopicsFilter
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
internal fun TopicsScreen(
    state: ContentViewState<List<TopicResponse>>,
    filter: TopicsFilter,
    onFilterSelected: (TopicsFilter) -> Unit,
    onRetry: () -> Unit,
    onTopicSelected: (TopicResponse) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopicsFilter.entries.forEach { option ->
                TopicFilterChip(option, option == filter) { onFilterSelected(option) }
            }
        }
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
            label = "topics loading"
        ) { current ->
            when (current) {
                ContentViewState.NotLoaded, ContentViewState.Loading -> TopicsSkeleton(filter == TopicsFilter.WEAK)
                is ContentViewState.Failed -> ContentError(current.message, onRetry)
                is ContentViewState.Ready -> {
                    if (current.content.isEmpty()) EmptyTab(
                        stringResource(if (filter == TopicsFilter.WEAK) R.string.no_weak_topics_list else R.string.no_topics),
                        textAlign = TextAlign.Center
                    )
                    else LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(current.content, key = { it.id }) { topic ->
                            TopicCard(topic, showMastery = filter == TopicsFilter.WEAK) { onTopicSelected(topic) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicFilterChip(filter: TopicsFilter, selected: Boolean, onClick: () -> Unit) {
    HomeFilterChip(
        stringResource(if (filter == TopicsFilter.ALL) R.string.topics_filter_all else R.string.topics_filter_weak),
        selected, if (filter == TopicsFilter.ALL) 48.dp else 54.dp, onClick
    )
}

@Composable
internal fun HomeFilterChip(label: String, selected: Boolean, minWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit) {
    val accent = if (selected) AppTheme.colors.link else AppTheme.colors.leftGradient
    val background by animateColorAsState(accent.copy(alpha = if (selected) 0.12f else 0.04f),
        tween(300), label = "topic filter background")
    val stroke by animateColorAsState(accent.copy(alpha = if (selected) 1f else 0.55f),
        tween(300), label = "topic filter stroke")
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.widthIn(min = minWidth).height(24.dp)
            .clip(shape).background(background).border(1.dp, stroke, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label,
            color = AppTheme.colors.primaryText, fontSize = 10.sp, lineHeight = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun TopicCard(topic: TopicResponse, showMastery: Boolean, onClick: () -> Unit) {
    val tint = topicTint(topic.visual)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
        shape = RoundedCornerShape(15.dp),
        color = AppTheme.colors.container,
        contentColor = AppTheme.colors.primaryText,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                TopicIcon(topic.visual, topic.name, Modifier.size(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(topic.name, color = AppTheme.colors.primaryText, fontSize = 12.sp, lineHeight = 15.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    topic.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 11.sp,
                            modifier = Modifier.padding(top = 4.dp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (showMastery) topic.mastery?.let { mastery ->
                Spacer(Modifier.height(14.dp))
                MasteryBar(mastery, tint)
                Text("${percent(mastery)}% ${stringResource(R.string.mastery)}",
                    color = AppTheme.colors.secondaryText, fontSize = 8.sp, lineHeight = 10.sp,
                    modifier = Modifier.padding(top = 5.dp))
            }
        }
    }
}

@Composable
private fun TopicsSkeleton(showMastery: Boolean) {
    val loadingDescription = stringResource(R.string.content_loading)
    ShimmerAnimation {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().semantics { stateDescription = loadingDescription },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(8) {
                SkeletonSurface(Modifier.fillMaxWidth().height(94.dp), 15) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimmerBlock(Modifier.size(34.dp), RoundedCornerShape(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShimmerBlock(Modifier.fillMaxWidth().height(12.dp))
                                ShimmerBlock(Modifier.fillMaxWidth().height(8.dp))
                                if (!showMastery) ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(8.dp))
                            }
                        }
                        if (showMastery) {
                            Spacer(Modifier.height(14.dp))
                            ShimmerBlock(Modifier.fillMaxWidth().height(5.dp))
                            ShimmerBlock(Modifier.padding(top = 5.dp).width(56.dp).height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
