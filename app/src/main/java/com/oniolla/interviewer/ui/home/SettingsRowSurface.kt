package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
internal fun SettingsRowSurface(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    val border = BorderStroke(1.dp, AppTheme.colors.stroke)
    if (onClick == null) {
        Surface(modifier = modifier, shape = shape, color = AppTheme.colors.container,
            border = border, content = content)
    } else {
        Surface(onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            color = AppTheme.colors.container, border = border, content = content)
    }
}
