package com.oniolla.interviewer.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.oniolla.interviewer.BuildConfig
import com.oniolla.interviewer.LinkText
import com.oniolla.interviewer.PreparationLanguageSelection
import com.oniolla.interviewer.R
import com.oniolla.interviewer.SpecializationSelection
import com.oniolla.interviewer.badge
import com.oniolla.interviewer.titleResource
import com.oniolla.interviewer.data.ThemeMode
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.ui.theme.AppTheme
import java.util.Locale

@Composable
fun ProfileScreens(
    state: ProfileViewState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSpecialization: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    onDismissTheme: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    onSelectSpecialization: (Specialization) -> Unit,
    onSaveSpecialization: () -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSaveLanguage: () -> Unit,
    onRetryLanguages: () -> Unit,
    onAccount: () -> Unit = {},
    onRetryUsage: () -> Unit = {}
) {
    // Keep the outgoing profile visible while its parent fades to Home or Account.
    val visiblePage = when (state.page) {
        ProfilePage.SPECIALIZATION, ProfilePage.LANGUAGE -> state.page
        else -> ProfilePage.PROFILE
    }
    AnimatedContent(targetState = visiblePage, modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
        transitionSpec = { fadeIn(tween(250)).togetherWith(fadeOut(tween(250))).using(null) },
        label = "profile settings pages") { page ->
        when (page) {
            ProfilePage.SPECIALIZATION -> SpecializationSelection(
                state.specialization.selectedSpecialization, state.specialization.isLoading,
                state.specialization.error, onSelectSpecialization, onSaveSpecialization, onBack
            )
            ProfilePage.LANGUAGE -> PreparationLanguageSelection(state.language, onBack,
                onSelectLanguage, onSaveLanguage, onRetryLanguages, R.string.profile_back)
            ProfilePage.PROFILE -> ProfileScreen(state, onBack, onRetry, onSpecialization, onLanguage, onTheme, onSignOut, onAccount, onRetryUsage)
            ProfilePage.CLOSED, ProfilePage.ACCOUNT -> Unit
        }
    }
    if (state.themeDialog) ThemeDialog(state.theme, onDismissTheme, onSelectTheme)
}

@Composable
private fun ProfileScreen(state: ProfileViewState, onBack: () -> Unit, onRetry: () -> Unit,
    onSpecialization: () -> Unit, onLanguage: () -> Unit, onTheme: () -> Unit, onSignOut: () -> Unit, onAccount: () -> Unit, onRetryUsage: () -> Unit) {
    Scaffold(containerColor = AppTheme.colors.background,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                val backDescription = stringResource(R.string.profile_back)
                Surface(onClick = onBack, enabled = !state.isSigningOut,
                    modifier = Modifier.size(28.dp).semantics { contentDescription = backDescription },
                    color = Color.Transparent, shape = CircleShape) {
                    Box(contentAlignment = Alignment.Center) {
                        FigmaIcon("chevron", Modifier.size(18.dp).rotate(180f), AppTheme.colors.homeIcon)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_settings), fontSize = 26.sp, lineHeight = 33.sp,
                        fontWeight = FontWeight.Bold, color = AppTheme.colors.primaryText, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.profile_settings_subtitle), fontSize = 11.sp, lineHeight = 14.sp,
                        color = AppTheme.colors.secondaryText)
                }
            }
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)) {
                Text(stringResource(R.string.profile_data), color = AppTheme.colors.primaryText,
                    fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Surface(onClick = onSignOut, enabled = !state.isSigningOut,
                    modifier = Modifier.fillMaxWidth().height(38.dp), shape = RoundedCornerShape(19.dp),
                    color = AppTheme.colors.container, border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (state.isSigningOut) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = AppTheme.colors.link, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (state.isSigningOut) R.string.profile_signing_out else R.string.profile_sign_out),
                            fontSize = 11.sp, lineHeight = 14.sp, color = AppTheme.colors.secondaryText)
                    }
                }
            }
        }
    ) { padding ->
        when (val profile = state.profile) {
            ContentViewState.NotLoaded, ContentViewState.Loading -> Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)
            ) {
                ShimmerAnimation {
                    SkeletonSurface(Modifier.fillMaxWidth().height(104.dp), 18) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            ShimmerBlock(Modifier.size(64.dp), CircleShape)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                ShimmerBlock(Modifier.fillMaxWidth().height(18.dp))
                                ShimmerBlock(Modifier.width(90.dp).height(11.dp))
                                ShimmerBlock(Modifier.width(88.dp).height(20.dp))
                            }
                        }
                    }
                }
            }
            is ContentViewState.Failed -> Box(Modifier.fillMaxSize().padding(padding)) {
                ContentError(profile.message, onRetry)
            }
            is ContentViewState.Ready -> Column(Modifier.fillMaxSize().padding(padding)
                .consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                ProfileCard(profile.content, state.streakDays, !state.isSigningOut, onAccount)
                Text(stringResource(R.string.profile_preferences), modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
                    color = AppTheme.colors.primaryText, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                val specialization = Specialization.entries.firstOrNull { it.code == profile.content.specialization }
                val specializationText = specialization?.let { stringResource(it.titleResource) }
                    ?: profile.content.specialization.orEmpty()
                ProfilePreference(stringResource(R.string.specialization), specializationText,
                    enabled = !state.isSigningOut, onClick = onSpecialization) {
                    ProfileBadge(specialization?.badge ?: "?", AppTheme.colors.success)
                }
                Spacer(Modifier.height(6.dp))
                val language = profile.content.preparationLanguage.orEmpty()
                val locale = LocalConfiguration.current.locales[0]
                val languageName = if (language.isEmpty()) stringResource(R.string.profile_language_unselected)
                    else Locale.forLanguageTag(language).getDisplayName(locale)
                        .replaceFirstChar { it.titlecase(locale) }
                ProfilePreference(stringResource(R.string.preparation_language), languageName,
                    enabled = !state.isSigningOut, onClick = onLanguage) {
                    ProfileBadge(language.substringBefore('-').uppercase(Locale.ROOT).take(3), AppTheme.colors.info)
                }
                Spacer(Modifier.height(6.dp))
                ProfilePreference(stringResource(R.string.profile_theme), themeLabel(state.theme),
                    enabled = !state.isSigningOut, onClick = onTheme) {
                    Image(painterResource(R.drawable.profile_icon_settings), null, Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(AppTheme.colors.homeIcon))
                }
                Spacer(Modifier.height(6.dp))
                ProfilePreference(stringResource(R.string.profile_about),
                    stringResource(R.string.profile_version, BuildConfig.VERSION_NAME),
                    enabled = !state.isSigningOut, chevron = false, onClick = null) {
                    FigmaIcon("code", Modifier.size(20.dp), AppTheme.colors.homeIcon)
                }
                ProfileUsage(state.usage, !state.isSigningOut, onRetryUsage)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: MeResponse, streak: Int?, enabled: Boolean, onAccount: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.Transparent,
        border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
        Row(Modifier.background(Brush.horizontalGradient(listOf(AppTheme.colors.heroStart, AppTheme.colors.heroEnd)))
            .heightIn(min = 104.dp).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(
                Brush.horizontalGradient(listOf(AppTheme.colors.leftGradient, AppTheme.colors.rightGradient))),
                contentAlignment = Alignment.Center) {
                Text(displayNameInitials(profile.displayName), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, color = AppTheme.colors.primaryText, fontSize = 18.sp, lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val specialization = Specialization.entries.firstOrNull { it.code == profile.specialization }
                    Text(specialization?.let { stringResource(it.titleResource) } ?: profile.specialization.orEmpty(),
                        modifier = Modifier.weight(1f), color = AppTheme.colors.secondaryText, fontSize = 11.sp, lineHeight = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinkText(text = stringResource(R.string.profile_account_settings), lineHeight = 12.sp,
                        enabled = enabled, onClick = onAccount)
                }
                Spacer(Modifier.height(6.dp))
                Text(if (streak == null) stringResource(R.string.stats_unavailable)
                    else stringResource(R.string.profile_streak, streak), color = AppTheme.colors.warning,
                    fontSize = 9.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.background(AppTheme.colors.warning.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun ProfilePreference(title: String, subtitle: String, enabled: Boolean, onClick: (() -> Unit)?,
    chevron: Boolean = true, icon: @Composable () -> Unit) {
    SettingsRowSurface(Modifier.fillMaxWidth().heightIn(min = 50.dp), onClick, enabled) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = AppTheme.colors.primaryText, fontSize = 11.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = AppTheme.colors.secondaryText, fontSize = 9.sp, lineHeight = 12.sp)
            }
            if (chevron) FigmaIcon("chevron", Modifier.size(18.dp), AppTheme.colors.homeIcon)
        }
    }
}

@Composable
private fun ProfileBadge(text: String, color: Color) {
    Box(Modifier.size(20.dp).background(color.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
        Text(text, color = color, fontSize = 7.sp, lineHeight = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun themeLabel(theme: ThemeMode): String = stringResource(when (theme) {
    ThemeMode.LIGHT -> R.string.profile_theme_light
    ThemeMode.DARK -> R.string.profile_theme_dark
    ThemeMode.SYSTEM -> R.string.profile_theme_system
})

@Composable
private fun ThemeDialog(theme: ThemeMode, onDismiss: () -> Unit, onSelected: (ThemeMode) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = AppTheme.colors.container,
            border = BorderStroke(1.dp, AppTheme.colors.stroke)) {
            Column(Modifier.fillMaxWidth().padding(20.dp).selectableGroup()) {
                Text(stringResource(R.string.profile_theme), color = AppTheme.colors.primaryText,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                ThemeMode.entries.forEach { mode ->
                    val shape = RoundedCornerShape(12.dp)
                    Row(Modifier.fillMaxWidth().clip(shape).selectable(mode == theme, role = Role.RadioButton,
                        onClick = { onSelected(mode) }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = mode == theme, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = AppTheme.colors.link,
                                unselectedColor = AppTheme.colors.secondaryText))
                        Spacer(Modifier.width(8.dp))
                        Text(themeLabel(mode), color = AppTheme.colors.primaryText, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
