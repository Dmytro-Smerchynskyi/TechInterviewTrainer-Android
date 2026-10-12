package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.R
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
internal fun TopicsScreen(state: ContentViewState<List<TopicResponse>>, onRetry: () -> Unit) {
    AnimatedContent(targetState = state, contentKey = { it::class },
        transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) }, label = "topics loading") { current ->
        when (current) {
            ContentViewState.NotLoaded, ContentViewState.Loading -> TopicsSkeleton()
            is ContentViewState.Failed -> ContentError(current.message, onRetry)
            is ContentViewState.Ready -> {
                if (current.content.isEmpty()) EmptyTab(stringResource(R.string.no_topics))
                else LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(current.content, key = { it.id }) { TopicCard(it) }
                }
            }
        }
    }
}

@Composable
private fun TopicCard(topic: TopicResponse) {
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
        shape = RoundedCornerShape(15.dp),
        color = AppTheme.colors.container,
        contentColor = AppTheme.colors.primaryText,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
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
    }
}

@Composable
private fun TopicsSkeleton() {
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
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBlock(Modifier.size(34.dp), RoundedCornerShape(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimmerBlock(Modifier.fillMaxWidth().height(12.dp))
                            ShimmerBlock(Modifier.fillMaxWidth().height(8.dp))
                            ShimmerBlock(Modifier.fillMaxWidth(0.7f).height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
