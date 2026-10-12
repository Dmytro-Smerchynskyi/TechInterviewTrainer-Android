package com.oniolla.interviewer.data

sealed interface AuthState {
    object Authorized : AuthState
    object Unauthorized : AuthState
    object EmailVerificationNeeded : AuthState
    data class VerificationEmailSent(val email: String, val message: String? = null) : AuthState
    data class EmailVerificationPending(val email: String, val message: String? = null) : AuthState
    data class EmailVerificationFailed(val message: String? = null) : AuthState
    object OnboardingRequired : AuthState
    data class PasswordResetLinkSent(val message: String, val email: String) : AuthState
    data class Loading(val progress: Float) : AuthState
    data class Error(val message: String? = null) : AuthState
    object OfflineAuthorized : AuthState
}

fun ApiException.toAuthState(): AuthState {
    return when (this) {
        is ApiException.BadRequest -> AuthState.Error(customError.message)
        is ApiException.Unauthorized -> AuthState.Error(customError.message)
        is ApiException.Forbidden -> {
            if (customError.code == "EMAIL_NOT_VERIFIED")
                AuthState.EmailVerificationNeeded
            else
                AuthState.Error(customError.message)
        }
        is ApiException.NotFound -> AuthState.Error(customError.message)
        is ApiException.ServerError -> AuthState.Error(customError.message)
        is ApiException.NetworkError -> AuthState.Error("Something unexpected happened")
        is ApiException.Unknown -> AuthState.Error(customError.message)
    }
}