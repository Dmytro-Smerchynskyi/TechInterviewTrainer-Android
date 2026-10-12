package com.oniolla.interviewer.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.oniolla.interviewer.ui.theme.AppTheme

private val LocalShimmerPhase = staticCompositionLocalOf<State<Float>?> { null }

@Composable
internal fun ShimmerAnimation(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "content shimmer")
    val phase = transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer phase"
    )
    CompositionLocalProvider(LocalShimmerPhase provides phase, content = content)
}

@Composable
internal fun ShimmerBlock(
    modifier: Modifier,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    val phase = LocalShimmerPhase.current
    val base = AppTheme.colors.shimmerBase
    val highlight = AppTheme.colors.shimmerHighlight
    Box(modifier.clip(shape).clearAndSetSemantics {}.drawBehind {
        val position = (phase?.value ?: 0f) * size.width
        drawRect(Brush.linearGradient(
            listOf(base, highlight, base),
            start = Offset(position - size.width, 0f),
            end = Offset(position, size.height)
        ))
    })
}
