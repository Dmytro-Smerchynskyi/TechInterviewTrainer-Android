package com.oniolla.interviewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
fun MainButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    buttonText: String = "",
    loadingButtonText: String = buttonText,
    isLoading: Boolean = false,
    textSize: TextUnit = 10.sp,
    leadingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val buttonShape = RoundedCornerShape(50.dp)

    Box(
        modifier = modifier
            .height(50.dp)
            .clip(buttonShape)
            .background(
                brush = Brush.horizontalGradient(
                    Pair(0f, AppTheme.colors.leftGradient),
                    Pair(1f, AppTheme.colors.rightGradient)
                ),
                shape = buttonShape
            )
            .clickable(enabled = enabled && !isLoading, role = Role.Button) {
                focusManager.clearFocus()
                keyboardController?.hide()
                onClick()
            }
            .padding(ButtonDefaults.ContentPadding),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                }
            }

            if (!isLoading && leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(10.dp))
            }

            Text(
                text = if (isLoading) loadingButtonText else buttonText,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontSize = textSize,
                fontWeight = FontWeight.SemiBold
            )
        }

    }
}
