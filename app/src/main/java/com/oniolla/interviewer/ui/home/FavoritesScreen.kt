package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.layout.Layout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.QuestionResponse
import com.oniolla.interviewer.domain.FavoritesFilter
import com.oniolla.interviewer.ui.theme.AppTheme
import kotlin.math.roundToInt

@Composable
internal fun FavoritesScreen(
    state: HomeViewState,
    onFilterSelected: (FavoritesFilter) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryPage: () -> Unit,
    onToggleSaved: (Long) -> Unit,
    onOpenQuestion: (QuestionResponse) -> Unit = {},
    onPro: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FavoritesFilter.entries.forEach { filter ->
                HomeFilterChip(stringResource(when (filter) {
                    FavoritesFilter.SAVED -> R.string.favorites_filter_saved
                    FavoritesFilter.DUE -> R.string.favorites_filter_due
                    FavoritesFilter.TRAINED -> R.string.favorites_filter_trained
                    FavoritesFilter.MASTERED -> R.string.favorites_filter_mastered
                }), selected = filter == state.favoritesFilter,
                    minWidth = when (filter) {
                        FavoritesFilter.SAVED -> 60.dp
                        FavoritesFilter.DUE -> 48.dp
                        FavoritesFilter.TRAINED -> 66.dp
                        FavoritesFilter.MASTERED -> 76.dp
                    }) { onFilterSelected(filter) }
            }
        }
        val dueCount = (state.dashboard as? ContentViewState.Ready)?.content?.dashboard?.dueCount ?: 0
        val bannerLoading = state.dashboard == ContentViewState.Loading
        if (state.favoritesFilter != FavoritesFilter.DUE && (bannerLoading || dueCount > 0)) {
            if (bannerLoading) ShimmerAnimation {
                SkeletonSurface(Modifier.fillMaxWidth().height(74.dp), 16) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        ShimmerBlock(Modifier.size(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            ShimmerBlock(Modifier.width(170.dp).height(13.dp))
                            ShimmerBlock(Modifier.width(130.dp).height(9.dp))
                        }
                    }
                }
            } else DueReviewBanner(dueCount) {
                onFilterSelected(FavoritesFilter.DUE)
            }
            Spacer(Modifier.height(54.dp))
        }
        val emptyMessage = stringResource(when (state.favoritesFilter) {
            FavoritesFilter.SAVED -> R.string.favorites_empty
            FavoritesFilter.DUE -> R.string.favorites_due_empty
            FavoritesFilter.TRAINED -> R.string.favorites_trained_empty
            FavoritesFilter.MASTERED -> R.string.favorites_mastered_empty
        })
        key(state.favoritesFilter) {
            AnimatedContent(
                targetState = state.favorites,
                contentKey = { it::class },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
                label = "favorites loading"
            ) { current ->
                when (current) {
                    ContentViewState.NotLoaded, ContentViewState.Loading -> FavoritesSkeleton()
                    is ContentViewState.Failed -> ContentError(current.message, onRetry)
                    is ContentViewState.Ready -> QuestionsList(current.content, onLoadMore, onRetryPage,
                        onToggleSaved, compact = true, emptyMessage = emptyMessage, onOpenQuestion = onOpenQuestion, onPro = onPro,
                        showLastScore = state.favoritesFilter == FavoritesFilter.TRAINED)
                }
            }
        }
    }
}

@Composable
private fun DueReviewBanner(dueCount: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 74.dp),
        shape = RoundedCornerShape(16.dp), color = AppTheme.colors.container,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Row(Modifier.background(Brush.horizontalGradient(
            listOf(AppTheme.colors.heroStart, AppTheme.colors.heroEnd)
        )).padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FigmaIcon("calendar", Modifier.padding(top = 4.dp).size(24.dp), AppTheme.colors.warning)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.favorites_due_banner, dueCount.coerceAtLeast(0)),
                    color = AppTheme.colors.primaryText, fontSize = 13.sp, lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.favorites_due_banner_description),
                    color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
            }
            FigmaIcon("chevron", Modifier.padding(top = 11.dp).size(18.dp), AppTheme.colors.homeIcon)
        }
    }
}

@Composable
internal fun FavoriteQuestionCard(question: QuestionResponse, onToggleSaved: (Long) -> Unit,
    onOpenQuestion: (QuestionResponse) -> Unit = {}, showLastScore: Boolean = false) {
    Surface(onClick = { onOpenQuestion(question) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(13.dp), color = AppTheme.colors.container,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TopicIcon(question.topic.visual, question.topic.name, Modifier.size(40.dp), iconSize = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(question.question, color = AppTheme.colors.primaryText, fontSize = 12.sp,
                    lineHeight = 15.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val score = question.progress?.takeIf {
                    showLastScore && it.attempts > 0 && it.lastScore.isFinite()
                }?.lastScore?.coerceIn(0.0, 100.0)?.roundToInt()
                FavoriteQuestionMetadata(
                    metadata = "${question.topic.name} • ${difficultyLabel(question.difficulty)}",
                    lastScore = score?.let { stringResource(R.string.favorites_last_score, it) }
                )
            }
            Spacer(Modifier.width(4.dp))
            QuestionSaveButton(question, onToggleSaved)
            FigmaIcon("chevron", Modifier.size(16.dp), AppTheme.colors.homeIcon)
        }
    }
}

@Composable
private fun FavoriteQuestionMetadata(metadata: String, lastScore: String?) {
    val style = LocalTextStyle.current.copy(color = AppTheme.colors.secondaryText,
        fontSize = 9.sp, lineHeight = 12.sp)
    if (lastScore == null) {
        Text(metadata, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    // Measure whole labels together, so the score never wraps between its words.
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        Text(metadata, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(" • ", style = style, maxLines = 1, softWrap = false)
        Text(lastScore, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val topic = measurables[0].measure(loose)
        val separator = measurables[1].measure(loose)
        val score = measurables[2].measure(loose)
        val fits = topic.width + separator.width + score.width <= constraints.maxWidth
        val height = if (fits) maxOf(topic.height, separator.height, score.height) else topic.height + score.height
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            topic.placeRelative(0, 0)
            if (fits) separator.placeRelative(topic.width, 0)
            score.placeRelative(if (fits) topic.width + separator.width else 0, if (fits) 0 else topic.height)
        }
    }
}

@Composable
private fun FavoritesSkeleton() {
    val description = stringResource(R.string.content_loading)
    ShimmerAnimation {
        LazyColumn(Modifier.fillMaxSize().semantics { stateDescription = description },
            contentPadding = PaddingValues(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(7) { FavoriteSkeletonRow() }
        }
    }
}

@Composable
internal fun FavoriteSkeletonRow() {
    SkeletonSurface(Modifier.fillMaxWidth().height(64.dp), 13) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ShimmerBlock(Modifier.size(40.dp), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBlock(Modifier.fillMaxWidth().height(12.dp))
                ShimmerBlock(Modifier.fillMaxWidth(0.55f).height(9.dp))
            }
            ShimmerBlock(Modifier.size(16.dp), CircleShape)
            ShimmerBlock(Modifier.size(16.dp))
        }
    }
}
