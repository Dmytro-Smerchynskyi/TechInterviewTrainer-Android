package com.oniolla.interviewer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oniolla.interviewer.data.ThemeMode
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.ui.theme.AppTheme
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme

private val profilePreview = ProfileViewState(
    page = ProfilePage.PROFILE,
    profile = ContentViewState.Ready(MeResponse("user", "developer@example.com", "Dmitry S.",
        true, "ANDROID", "", "", true, listOf("PASSWORD"), "en")),
    streakDays = 7, theme = ThemeMode.SYSTEM
)

@Composable
private fun ProfilePreview(state: ProfileViewState = profilePreview) {
    Box(Modifier.background(AppTheme.colors.background).padding(top = 24.dp)) {
        ProfileScreens(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun ProfileDarkPreview() { InterviewTrainerTheme(darkTheme = true) { ProfilePreview() } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun ProfileLightPreview() { InterviewTrainerTheme(darkTheme = false) { ProfilePreview() } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun ProfileThemePreview() { InterviewTrainerTheme(darkTheme = true) { ProfilePreview(profilePreview.copy(themeDialog = true)) } }

@Preview(widthDp = 390, heightDp = 844, showSystemUi = true)
@Composable
fun ProfileSignOutPreview() { InterviewTrainerTheme(darkTheme = false) { ProfilePreview(profilePreview.copy(isSigningOut = true)) } }
