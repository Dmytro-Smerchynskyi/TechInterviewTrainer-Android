package com.oniolla.interviewer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.theme.AppTheme

@Composable
internal fun GoogleAuthButton(enabled: Boolean, loading: Boolean, error: String?, onClick: () -> Unit,
    compact: Boolean = false) {
    val colors = AppTheme.colors
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column {
        Row(Modifier.fillMaxWidth().padding(top = if (compact) 12.dp else 18.dp,
            bottom = if (compact) 6.dp else 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(colors.stroke))
            Text(stringResource(R.string.auth_or), color = colors.secondaryText, fontSize = 10.sp, lineHeight = 13.sp)
            Box(Modifier.weight(1f).height(1.dp).background(colors.stroke))
        }
        Surface(onClick = { focus.clearFocus(); keyboard?.hide(); onClick() },
            enabled = enabled && !loading, modifier = Modifier.fillMaxWidth().height(50.dp),
            color = colors.container, shape = RoundedCornerShape(25.dp),
            border = BorderStroke(1.dp, colors.stroke)) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = colors.link, strokeWidth = 2.dp)
                else Image(painterResource(R.drawable.auth_google_logo), null, Modifier.size(24.dp))
                Spacer(Modifier.width(16.dp))
                Text(stringResource(if (loading) R.string.google_signing_in else R.string.google_continue),
                    color = colors.primaryText, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        AnimatedVisibility(error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(error.orEmpty(), Modifier.fillMaxWidth().padding(top = 10.dp), color = colors.red,
                fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}
