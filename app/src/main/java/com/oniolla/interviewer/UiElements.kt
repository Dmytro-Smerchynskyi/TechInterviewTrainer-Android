package com.oniolla.interviewer

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun GradientLinearProgress(
    modifier: Modifier,
    brush: Brush,
    trackColor: Color,
    progress: () -> Float,
) {
    Canvas(
        modifier = modifier
    ) {
        val radius = size.height / 2f

        // Track
        drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(radius, radius)
        )

        // Progress
        drawRoundRect(
            brush = brush,
            size = Size(
                width = size.width * progress().coerceIn(0f, 1f),
                height = size.height
            ),
            cornerRadius = CornerRadius(radius, radius)
        )
    }
}