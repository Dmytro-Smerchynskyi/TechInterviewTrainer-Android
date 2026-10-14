package com.oniolla.interviewer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import com.oniolla.interviewer.ui.home.ShimmerAnimation
import com.oniolla.interviewer.ui.home.ShimmerBlock
import com.oniolla.interviewer.ui.theme.AppTheme
import java.util.Locale

@Composable
fun PreparationLanguageSelection(
    state: AuthViewState.PreparationLanguageSelection,
    onBack: () -> Unit = {},
    onLanguageSelected: (String) -> Unit = {},
    onContinue: () -> Unit = {},
    onRetry: () -> Unit = {},
    backDescriptionResource: Int = R.string.language_back_to_specialization
) {
    val canContinue = !state.isLoadingLanguages && state.languagesError == null &&
        state.languages.any { it.code == state.selectedLanguage }
    Scaffold(containerColor = AppTheme.colors.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                val backDescription = stringResource(backDescriptionResource)
                Surface(
                    onClick = onBack, enabled = !state.isSaving,
                    modifier = Modifier.size(24.dp).semantics { contentDescription = backDescription },
                    shape = CircleShape, color = Color.Transparent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = AppTheme.colors.secondaryText.copy(alpha = if (state.isSaving) 0.42f else 1f),
                            fontSize = 28.sp, lineHeight = 32.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.preparation_language), color = AppTheme.colors.primaryText,
                    fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp),
                shape = RoundedCornerShape(20.dp), color = Color.Transparent,
                border = BorderStroke(1.dp, AppTheme.colors.stroke)
            ) {
                Column(
                    Modifier.background(Brush.horizontalGradient(listOf(AppTheme.colors.heroStart, AppTheme.colors.heroEnd)))
                        .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 10.dp)
                ) {
                    Icon(ImageVector.vectorResource(R.drawable.specialization_grade), null,
                        Modifier.size(34.dp), tint = AppTheme.colors.info)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.choose_preparation_language), color = AppTheme.colors.primaryText,
                        fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.preparation_language_subtext), color = AppTheme.colors.secondaryText,
                        fontSize = 9.sp, lineHeight = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.choose_one_specialization), color = AppTheme.colors.primaryText,
                fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                targetState = state.isLoadingLanguages,
                transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(200))) },
                label = "preparation languages loading"
            ) { loading ->
                when {
                    loading -> LanguageOptionsSkeleton()
                    state.languagesError != null -> LanguageOptionsError(state.languagesError, onRetry)
                    state.languages.isEmpty() -> Text(
                        stringResource(R.string.preparation_languages_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        color = AppTheme.colors.secondaryText, fontSize = 12.sp,
                        lineHeight = 16.sp, textAlign = TextAlign.Center
                    )
                    else -> Column(
                        Modifier.fillMaxWidth().selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.languages.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { language ->
                                    OnboardingChoiceOption(
                                        modifier = Modifier.weight(1f),
                                        badge = language.code.substringBefore('-').uppercase(Locale.ROOT).take(3),
                                        title = language.nativeName.ifBlank { language.name },
                                        description = language.name,
                                        selected = state.selectedLanguage == language.code,
                                        enabled = !state.isSaving,
                                        onClick = { onLanguageSelected(language.code) },
                                        height = 72.dp, titleTop = 10.dp, descriptionTop = 49.dp,
                                        descriptionSize = 8.sp, descriptionLineHeight = 11.sp
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Box(
                Modifier.padding(top = 28.dp).fillMaxWidth().heightIn(min = 48.dp)
                    .background(AppTheme.colors.info.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 25.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.language_feedback_info), color = AppTheme.colors.secondaryText,
                    fontSize = 10.sp, lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
            AnimatedVisibility(
                visible = state.error != null, enter = fadeIn(), exit = fadeOut() + shrinkVertically()
            ) {
                Text(state.error.orEmpty(), modifier = Modifier.padding(top = 8.dp),
                    color = AppTheme.colors.red, fontSize = 10.sp, lineHeight = 14.sp)
            }
            MainButton(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp)
                    .alpha(if (canContinue || state.isSaving) 1f else 0.42f),
                buttonText = stringResource(R.string.continue_onboarding),
                loadingButtonText = stringResource(R.string.saving_specialization),
                isLoading = state.isSaving, enabled = canContinue, textSize = 12.sp,
                onClick = onContinue
            )
            Text(
                stringResource(R.string.language_translation_info),
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                color = AppTheme.colors.primaryText, fontSize = 9.sp, lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun LanguageOptionsError(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = AppTheme.colors.red, fontSize = 12.sp, lineHeight = 16.sp,
            textAlign = TextAlign.Center)
        LinkText(text = stringResource(R.string.retry), onClick = onRetry)
    }
}

@Composable
private fun LanguageOptionsSkeleton() {
    val loadingDescription = stringResource(R.string.content_loading)
    ShimmerAnimation {
        Column(Modifier.fillMaxWidth().semantics { stateDescription = loadingDescription },
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) {
                        Surface(
                            modifier = Modifier.weight(1f).height(72.dp),
                            shape = RoundedCornerShape(16.dp), color = AppTheme.colors.container,
                            border = BorderStroke(1.dp, AppTheme.colors.stroke)
                        ) {
                            Box {
                                ShimmerBlock(Modifier.padding(start = 12.dp, top = 10.dp).size(32.dp),
                                    RoundedCornerShape(14.dp))
                                ShimmerBlock(Modifier.padding(start = 50.dp, top = 12.dp).width(70.dp).height(12.dp))
                                ShimmerBlock(Modifier.padding(start = 12.dp, top = 49.dp).width(95.dp).height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
