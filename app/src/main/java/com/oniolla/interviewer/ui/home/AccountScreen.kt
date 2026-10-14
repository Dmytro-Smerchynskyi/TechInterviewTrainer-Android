package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.AppTextField
import com.oniolla.interviewer.EmailConfirmation
import com.oniolla.interviewer.MainButton
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.theme.AppTheme
import java.util.Locale

@Composable
fun AccountScreens(
    state: AccountViewState,
    onBack: () -> Unit = {},
    onEdit: (AccountPage) -> Unit = {},
    onUpdate: (AccountField, String) -> Unit = { _, _ -> },
    onFocused: (AccountField) -> Unit = {},
    onSubmit: () -> Unit = {},
    onResend: () -> Unit = {},
    onChangeEmailAddress: () -> Unit = {},
    onPaste: (String?) -> Unit = {},
    onRetryProfile: () -> Unit = {}
) {
    AnimatedContent(state.page, modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
        transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(250))).using(null) },
        label = "account pages") { page ->
        when (page) {
            AccountPage.OVERVIEW -> AccountOverview(state, onBack, onEdit, onRetryProfile)
            AccountPage.CODE -> EmailConfirmation(state.email, state.code, state.errors[AccountField.CODE],
                state.isSaving, state.isResending, { onUpdate(AccountField.CODE, it) },
                { onFocused(AccountField.CODE) }, onSubmit, onResend, onChangeEmailAddress, onPaste)
            else -> AccountEditor(state.copy(page = page), onBack, onUpdate, onFocused, onSubmit)
        }
    }
}

@Composable
private fun AccountOverview(state: AccountViewState, onBack: () -> Unit,
    onEdit: (AccountPage) -> Unit, onRetryProfile: () -> Unit) {
    Scaffold(containerColor = AppTheme.colors.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AccountBack(onBack, !state.isSaving)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_settings), fontSize = 18.sp, lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryText)
            }
            Text(stringResource(R.string.account_subtitle), fontSize = 10.sp, lineHeight = 14.sp,
                color = AppTheme.colors.secondaryText, modifier = Modifier.padding(top = 8.dp))
            when (val profile = state.profile) {
                is ContentViewState.Ready -> {
                    AccountSection(stringResource(R.string.account_section))
                    AccountRow(stringResource(R.string.name), profile.content.displayName,
                        enabled = !state.isSaving, onClick = { onEdit(AccountPage.NAME) })
                    Spacer(Modifier.height(8.dp))
                    AccountRow(stringResource(R.string.email), profile.content.email,
                        enabled = !state.isSaving, onClick = { onEdit(AccountPage.EMAIL) })
                    Spacer(Modifier.height(8.dp))
                    AccountRow(stringResource(R.string.password), if (state.hasPassword) "••••••••••"
                        else stringResource(R.string.account_password_not_set),
                        enabled = !state.isSaving, onClick = { onEdit(AccountPage.PASSWORD) })
                }
                is ContentViewState.Failed -> Box(Modifier.fillMaxWidth().height(160.dp)) {
                    ContentError(profile.message, onRetryProfile)
                }
                else -> ShimmerAnimation {
                    Spacer(Modifier.height(22.dp))
                    repeat(3) {
                        ShimmerBlock(Modifier.fillMaxWidth().height(54.dp), RoundedCornerShape(13.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            AccountSection(stringResource(R.string.account_sign_in_methods))
            val profile = (state.profile as? ContentViewState.Ready)?.content
            if (profile != null) {
                val providers = profile.linkedProviders.map { it.uppercase(Locale.ROOT) }.toSet()
                val methods = (listOf("GOOGLE", "APPLE") + providers).distinct()
                methods.forEachIndexed { index, method ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    val title = when (method) {
                        "GOOGLE" -> stringResource(R.string.account_google)
                        "APPLE" -> stringResource(R.string.account_apple)
                        "PASSWORD" -> stringResource(R.string.password)
                        else -> method.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                    }
                    AccountRow(title, stringResource(if (method in providers)
                        R.string.account_connected else R.string.account_not_connected),
                        enabled = !state.isSaving)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AccountSection(title: String) {
    Text(title, fontSize = 13.sp, lineHeight = 18.sp, color = AppTheme.colors.primaryText,
        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
}

@Composable
private fun AccountRow(title: String, subtitle: String, enabled: Boolean, onClick: (() -> Unit)? = null) {
    SettingsRowSurface(Modifier.fillMaxWidth().heightIn(min = 54.dp), onClick, enabled) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.profile_icon_settings), null, Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(AppTheme.colors.homeIcon))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = AppTheme.colors.primaryText, fontSize = 11.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
            }
            if (onClick != null) FigmaIcon("chevron", Modifier.size(18.dp), AppTheme.colors.homeIcon)
        }
    }
}

@Composable
private fun AccountBack(onClick: () -> Unit, enabled: Boolean) {
    val description = stringResource(R.string.account_back)
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)
        .semantics { contentDescription = description }, shape = CircleShape, color = Color.Transparent) {
        Box(contentAlignment = Alignment.Center) {
            FigmaIcon("chevron", Modifier.size(18.dp).rotate(180f), AppTheme.colors.secondaryText)
        }
    }
}

@Composable
private fun AccountEditor(state: AccountViewState, onBack: () -> Unit,
    onUpdate: (AccountField, String) -> Unit, onFocused: (AccountField) -> Unit, onSubmit: () -> Unit) {
    val password = state.page == AccountPage.PASSWORD
    val title = when (state.page) {
        AccountPage.NAME -> R.string.account_change_name
        AccountPage.EMAIL -> R.string.account_change_email
        else -> if (state.hasPassword) R.string.account_change_password else R.string.account_set_password
    }
    val subtitle = when (state.page) {
        AccountPage.NAME -> R.string.account_name_description
        AccountPage.EMAIL -> R.string.account_email_description
        else -> R.string.account_password_description
    }
    val button = when (state.page) {
        AccountPage.NAME -> R.string.account_save_name
        AccountPage.EMAIL -> R.string.account_send_code
        else -> if (state.hasPassword) R.string.account_change_password_button else R.string.account_set_password_button
    }
    Scaffold(modifier = Modifier.fillMaxSize().imePadding(), containerColor = AppTheme.colors.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) { AccountBack(onBack, !state.isSaving) }
            Spacer(Modifier.height(44.dp))
            Box(Modifier.size(80.dp).background(AppTheme.colors.link.copy(alpha = 0.11f), CircleShape)
                .clip(CircleShape), contentAlignment = Alignment.Center) {
                val icon = when (state.page) {
                    AccountPage.NAME -> R.drawable.account_user_mark
                    AccountPage.EMAIL -> R.drawable.account_mail_mark
                    else -> R.drawable.account_lock_mark
                }
                Surface(Modifier.fillMaxSize(), shape = CircleShape, color = Color.Transparent,
                    border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), null, Modifier.size(32.dp),
                            tint = if (state.page == AccountPage.EMAIL) AppTheme.colors.link else AppTheme.colors.secondaryText)
                    }
                }
            }
            Spacer(Modifier.height(if (password) 4.dp else 34.dp))
            Text(stringResource(title), color = AppTheme.colors.primaryText, fontSize = 24.sp, lineHeight = 29.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(11.dp))
            Text(stringResource(subtitle), Modifier.widthIn(max = 300.dp), color = AppTheme.colors.secondaryText,
                fontSize = 10.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(if (password) 48.dp else 56.dp))
            when (state.page) {
                AccountPage.NAME -> AccountInput(state, AccountField.NAME, state.name, R.string.name,
                    R.string.name_placeholder, R.drawable.mail, KeyboardType.Text, ImeAction.Done,
                    onUpdate, onFocused, onSubmit)
                AccountPage.EMAIL -> AccountInput(state, AccountField.EMAIL, state.email, R.string.account_new_email,
                    R.string.email_placeholder, R.drawable.mail, KeyboardType.Email, ImeAction.Done,
                    onUpdate, onFocused, onSubmit)
                AccountPage.PASSWORD -> {
                    if (state.hasPassword) {
                        AccountInput(state, AccountField.CURRENT_PASSWORD, state.currentPassword, R.string.account_current_password,
                            R.string.password_placeholder, R.drawable.lock, KeyboardType.Password, ImeAction.Next,
                            onUpdate, onFocused, onSubmit)
                        Spacer(Modifier.height(10.dp))
                    }
                    AccountInput(state, AccountField.NEW_PASSWORD, state.newPassword, R.string.account_new_password,
                        R.string.password_placeholder, R.drawable.lock, KeyboardType.Password, ImeAction.Next,
                        onUpdate, onFocused, onSubmit)
                    Spacer(Modifier.height(10.dp))
                    AccountInput(state, AccountField.CONFIRM_PASSWORD, state.confirmPassword, R.string.account_confirm_password,
                        R.string.account_repeat_password, R.drawable.lock, KeyboardType.Password, ImeAction.Done,
                        onUpdate, onFocused, onSubmit)
                }
                else -> Unit
            }
            MainButton(Modifier.fillMaxWidth().padding(top = if (password) 42.dp else 16.dp),
                onClick = onSubmit, buttonText = stringResource(button),
                loadingButtonText = stringResource(if (state.page == AccountPage.EMAIL)
                    R.string.sending_verification_code else R.string.account_saving), isLoading = state.isSaving)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AccountInput(state: AccountViewState, field: AccountField, value: String, label: Int,
    placeholder: Int, icon: Int, type: KeyboardType, action: ImeAction,
    onUpdate: (AccountField, String) -> Unit, onFocused: (AccountField) -> Unit, onSubmit: () -> Unit) {
    val text = remember(value) { mutableStateOf(value) }
    AppTextField(Modifier.fillMaxWidth(), text, stringResource(label), ImageVector.vectorResource(icon),
        KeyboardOptions(keyboardType = type, imeAction = action), passwordVisible = type != KeyboardType.Password,
        error = state.errors[field], onFocused = { onFocused(field) }, onDone = onSubmit,
        enabled = !state.isSaving, placeholder = stringResource(placeholder),
        onValueChanged = { onUpdate(field, text.value) }, fieldHeight = 50.dp, headerSpacing = 7.dp, headerLineHeight = 13.sp)
}
