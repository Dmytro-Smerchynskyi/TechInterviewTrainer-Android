package com.oniolla.interviewer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.AppTextField
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme

@Composable
internal fun PasswordResetScreen(
    state: AuthViewModel.AuthViewState.PasswordReset,
    password: MutableState<String>,
    confirmation: MutableState<String>,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    Scaffold(Modifier.fillMaxSize().imePadding(), containerColor = AppTheme.colors.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val backDescription = stringResource(R.string.password_reset_back)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Surface(onClick = onBack, enabled = !state.isLoading,
                    modifier = Modifier.size(28.dp).semantics { contentDescription = backDescription },
                    shape = CircleShape, color = Color.Transparent) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.home_icon_chevron), null, Modifier.size(16.dp).rotate(180f),
                            tint = AppTheme.colors.secondaryText)
                    }
                }
            }
            Spacer(Modifier.height(44.dp))
            Surface(Modifier.size(80.dp).background(AppTheme.colors.link.copy(alpha = 0.11f), CircleShape),
                shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.account_lock_mark), null, Modifier.size(32.dp),
                        tint = AppTheme.colors.secondaryText)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.password_reset_title), color = AppTheme.colors.primaryText,
                fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(11.dp))
            Text(stringResource(R.string.password_reset_description), Modifier.widthIn(max = 300.dp),
                color = AppTheme.colors.secondaryText, fontSize = 10.sp, lineHeight = 14.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))
            AppTextField(Modifier.fillMaxWidth(), password, stringResource(R.string.account_new_password),
                ImageVector.vectorResource(R.drawable.lock),
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                passwordVisible = false, error = state.passwordError, enabled = !state.isLoading,
                onFocused = onEdit, onValueChanged = onEdit,
                placeholder = stringResource(R.string.password_placeholder),
                fieldHeight = 50.dp, headerSpacing = 7.dp, headerLineHeight = 13.sp)
            Spacer(Modifier.height(10.dp))
            AppTextField(Modifier.fillMaxWidth(), confirmation, stringResource(R.string.account_confirm_password),
                ImageVector.vectorResource(R.drawable.lock),
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                passwordVisible = false, error = state.confirmationError, enabled = !state.isLoading,
                onFocused = onEdit, onValueChanged = onEdit, onDone = onSubmit,
                placeholder = stringResource(R.string.account_repeat_password),
                fieldHeight = 50.dp, headerSpacing = 7.dp, headerLineHeight = 13.sp)
            AnimatedVisibility(state.error != null, enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()) {
                Text(state.error.orEmpty(), Modifier.fillMaxWidth().padding(top = 10.dp),
                    color = AppTheme.colors.red, fontSize = 10.sp, lineHeight = 14.sp)
            }
            Spacer(Modifier.height(42.dp))
            MainButton(Modifier.fillMaxWidth(), onClick = onSubmit,
                buttonText = stringResource(R.string.password_reset_title),
                loadingButtonText = stringResource(R.string.account_saving),
                isLoading = state.isLoading, enabled = !state.isLoading)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordResetLightPreview() {
    InterviewTrainerTheme(darkTheme = false) {
        PasswordResetScreen(AuthViewModel.AuthViewState.PasswordReset(), remember { mutableStateOf("") },
            remember { mutableStateOf("") }, {}, {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PasswordResetDarkPreview() {
    InterviewTrainerTheme(darkTheme = true) {
        PasswordResetScreen(AuthViewModel.AuthViewState.PasswordReset(isLoading = true), remember { mutableStateOf("") },
            remember { mutableStateOf("") }, {}, {}, {})
    }
}
