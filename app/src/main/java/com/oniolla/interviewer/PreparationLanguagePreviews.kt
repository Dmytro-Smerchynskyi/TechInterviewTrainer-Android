package com.oniolla.interviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oniolla.interviewer.data.model.PreparationLanguage
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme

private val previewLanguages = listOf(
    PreparationLanguage("en", "English", "English", true),
    PreparationLanguage("ru", "Russian", "Русский", true),
    PreparationLanguage("uk", "Ukrainian", "Українська", true),
    PreparationLanguage("cs", "Czech", "Čeština", true),
    PreparationLanguage("de", "German", "Deutsch", true),
    PreparationLanguage("es", "Spanish", "Español", true),
    PreparationLanguage("fr", "French", "Français", true),
    PreparationLanguage("pt", "Portuguese", "Português", true)
)

private val languagePreviewState = AuthViewState.PreparationLanguageSelection(
    specialization = Specialization.ANDROID, languages = previewLanguages,
    selectedLanguage = "en", isLoadingLanguages = false
)

@Composable
private fun LanguagePreview(state: AuthViewState.PreparationLanguageSelection = languagePreviewState) {
    Box(Modifier.background(AppTheme.colors.background).padding(top = 24.dp)) {
        PreparationLanguageSelection(state)
    }
}

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun PreparationLanguageLightPreview() { InterviewTrainerTheme(darkTheme = false) { LanguagePreview() } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun PreparationLanguageDarkPreview() { InterviewTrainerTheme(darkTheme = true) { LanguagePreview() } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun PreparationLanguageLoadingPreview() { InterviewTrainerTheme(darkTheme = true) {
    LanguagePreview(AuthViewState.PreparationLanguageSelection(Specialization.IOS))
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun PreparationLanguageSavingPreview() { InterviewTrainerTheme(darkTheme = false) {
    LanguagePreview(languagePreviewState.copy(selectedLanguage = "uk", isSaving = true))
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun PreparationLanguageErrorPreview() { InterviewTrainerTheme(darkTheme = true) {
    LanguagePreview(languagePreviewState.copy(languages = emptyList(), selectedLanguage = null,
        languagesError = "Unable to load available languages"))
} }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun PreparationLanguageEmptyPreview() { InterviewTrainerTheme(darkTheme = false) {
    LanguagePreview(languagePreviewState.copy(languages = emptyList(), selectedLanguage = null))
} }
