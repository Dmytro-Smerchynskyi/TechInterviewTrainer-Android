package com.oniolla.interviewer.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.oniolla.interviewer.Login
import com.oniolla.interviewer.SignUp
import com.oniolla.interviewer.ui.theme.InterviewTrainerTheme

@Preview(widthDp = 390, heightDp = 844)
@Composable
fun GoogleLoginDarkPreview() { InterviewTrainerTheme(darkTheme = true) { Login() } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun GoogleLoginLightPreview() { InterviewTrainerTheme(darkTheme = false) { Login() } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun GoogleRegistrationDarkPreview() { InterviewTrainerTheme(darkTheme = true) { SignUp() } }
@Preview(widthDp = 390, heightDp = 844)
@Composable
fun GoogleRegistrationLightPreview() { InterviewTrainerTheme(darkTheme = false) { SignUp() } }
@Preview(widthDp = 320, heightDp = 640)
@Composable
fun GoogleLoadingCompactPreview() { InterviewTrainerTheme(darkTheme = false) {
    Login(isLoading = true, isGoogleLoading = true)
} }
