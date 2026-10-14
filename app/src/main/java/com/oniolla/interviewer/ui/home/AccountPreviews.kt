package com.oniolla.interviewer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.oniolla.interviewer.data.model.*
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme

private val accountPreview = AccountViewState(
    profile = ContentViewState.Ready(MeResponse("user", "dmitry@example.com", "Dmitry S.",
        true, "ANDROID", "", "", true, listOf("PASSWORD", "GOOGLE"), "en")),
    name = "Dmitry S."
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun AccountDarkPreview() { InterviewTrainerTheme(darkTheme = true) { AccountScreens(accountPreview) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun AccountLightPreview() { InterviewTrainerTheme(darkTheme = false) { AccountScreens(accountPreview) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ChangeNameDarkPreview() { InterviewTrainerTheme(darkTheme = true) { AccountScreens(accountPreview.copy(page = AccountPage.NAME)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ChangeNameLightPreview() { InterviewTrainerTheme(darkTheme = false) { AccountScreens(accountPreview.copy(page = AccountPage.NAME)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ChangeEmailDarkPreview() { InterviewTrainerTheme(darkTheme = true) { AccountScreens(accountPreview.copy(page = AccountPage.EMAIL)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ChangeEmailLightPreview() { InterviewTrainerTheme(darkTheme = false) { AccountScreens(accountPreview.copy(page = AccountPage.EMAIL)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ChangePasswordDarkPreview() { InterviewTrainerTheme(darkTheme = true) { AccountScreens(accountPreview.copy(page = AccountPage.PASSWORD)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ChangePasswordLightPreview() { InterviewTrainerTheme(darkTheme = false) { AccountScreens(accountPreview.copy(page = AccountPage.PASSWORD)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun SetPasswordPreview() { InterviewTrainerTheme(darkTheme = false) { AccountScreens(accountPreview.copy(
    page = AccountPage.PASSWORD,
    profile = ContentViewState.Ready((accountPreview.profile as ContentViewState.Ready).content.copy(linkedProviders = listOf("GOOGLE")))
)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun AccountSavingPreview() { InterviewTrainerTheme(darkTheme = true) { AccountScreens(accountPreview.copy(
    page = AccountPage.PASSWORD, isSaving = true,
    errors = mapOf(AccountField.CONFIRM_PASSWORD to "Passwords do not match")
)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun AccountLoadingPreview() { InterviewTrainerTheme(darkTheme = false) { AccountScreens(accountPreview.copy(
    profile = ContentViewState.Loading
)) } }

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun ChangeEmailCodePreview() { InterviewTrainerTheme(darkTheme = true) { AccountScreens(accountPreview.copy(
    page = AccountPage.CODE, email = "new@example.com", code = "615806"
)) } }
