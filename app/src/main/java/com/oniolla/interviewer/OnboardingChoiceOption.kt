package com.oniolla.interviewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
internal fun OnboardingChoiceOption(
    modifier: Modifier = Modifier,
    badge: String,
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    height: Dp = 82.dp,
    titleTop: Dp = 12.dp,
    descriptionTop: Dp = 55.dp,
    descriptionSize: TextUnit = 9.sp,
    descriptionLineHeight: TextUnit = 12.sp
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> AppTheme.colors.leftGradient.copy(alpha = if (enabled) 0.1f else 0.04f)
            enabled -> AppTheme.colors.container
            else -> AppTheme.colors.containerDisabled
        }, animationSpec = tween(300))
    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> AppTheme.colors.leftGradient.copy(alpha = if (enabled) 1f else 0.42f)
            enabled -> AppTheme.colors.stroke
            else -> AppTheme.colors.strokeDisabled
        }, animationSpec = tween(300))
    val titleColor by animateColorAsState(
        targetValue = AppTheme.colors.primaryText.copy(alpha = if (enabled) 1f else 0.42f),
        animationSpec = tween(300))
    val descriptionColor by animateColorAsState(
        targetValue = if (enabled) AppTheme.colors.secondaryText else AppTheme.colors.secondaryDisabledText,
        animationSpec = tween(300))
    val badgeColor by animateColorAsState(
        targetValue = AppTheme.colors.success.copy(alpha = if (enabled) 0.14f else 0.06f),
        animationSpec = tween(300))

    Surface(
        selected = selected, onClick = onClick, enabled = enabled,
        modifier = modifier.height(height).semantics { role = Role.RadioButton },
        shape = RoundedCornerShape(16.dp), color = containerColor,
        contentColor = titleColor, border = BorderStroke(1.dp, borderColor)
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.padding(start = 12.dp, top = 10.dp).size(32.dp)
                    .background(badgeColor, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(badge, color = titleColor, fontSize = 9.sp, lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(
                modifier = Modifier.padding(start = 50.dp, end = if (selected) 40.dp else 12.dp, top = titleTop),
                text = title, color = titleColor, fontSize = 13.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = descriptionTop),
                text = description, color = descriptionColor,
                fontSize = descriptionSize, lineHeight = descriptionLineHeight,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            AnimatedVisibility(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 12.dp),
                visible = selected, enter = fadeIn(), exit = fadeOut()
            ) {
                Box(
                    Modifier.size(24.dp).background(
                        AppTheme.colors.leftGradient.copy(alpha = if (enabled) 0.15f else 0.06f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.specialization_check),
                        contentDescription = null,
                        tint = AppTheme.colors.leftGradient.copy(alpha = if (enabled) 1f else 0.42f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
