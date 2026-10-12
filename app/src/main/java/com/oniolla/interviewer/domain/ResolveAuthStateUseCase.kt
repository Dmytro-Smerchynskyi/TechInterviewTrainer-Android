package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class ResolveAuthStateUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val resendVerificationCodeUseCase: ResendVerificationCodeUseCase
) : UseCase<Unit, AuthState> {
    override suspend fun invoke(input: Unit): AuthState {
        val state = try {
            resolveProfile(authRepository.getMe())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.toApiException()
            if (error is ApiException.Unauthorized) {
                authRepository.clearSession()
                AuthState.Unauthorized
            } else {
                error.toAuthState()
            }
        }
        return when (state) {
            AuthState.EmailVerificationNeeded -> {
                authRepository.clearSession()
                AuthState.Unauthorized
            }
            else -> state
        }
    }

    suspend fun resolveProfile(profile: MeResponse): AuthState {
        val resolvedProfile = if (profile.toAuthState() == AuthState.PreparationLanguageSelectionRequired) {
            authRepository.selectPreparationLanguage("en")
        } else {
            profile
        }
        return when (val state = resolvedProfile.toAuthState()) {
            is AuthState.EmailVerificationPending -> requestEmailConfirmation(state.email)
            AuthState.PreparationLanguageSelectionRequired -> AuthState.Error("Preparation language was not saved")
            else -> state
        }
    }

    private suspend fun requestEmailConfirmation(email: String): AuthState {
        return when (val result = resendVerificationCodeUseCase(email)) {
            is AuthState.VerificationEmailSent -> result
            is AuthState.Error -> AuthState.EmailVerificationPending(email, result.message ?: "Unknown error")
            else -> AuthState.EmailVerificationPending(email, "Unable to send verification code")
        }
    }
}

fun MeResponse.toAuthState(): AuthState = when {
    !emailVerified -> AuthState.EmailVerificationPending(email)
    onboardingCompleted -> AuthState.Authorized
    specialization == null -> AuthState.SpecializationSelectionRequired
    preparationLanguage == null -> AuthState.PreparationLanguageSelectionRequired
    else -> AuthState.OnboardingRequired
}
