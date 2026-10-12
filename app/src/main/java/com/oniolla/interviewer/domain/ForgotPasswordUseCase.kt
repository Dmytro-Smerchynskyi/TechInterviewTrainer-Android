package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class ForgotPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) : UseCase<String, AuthState> {
    override suspend fun invoke(input: String): AuthState {
        return try {
            val email = input.trim()
            val response = authRepository.forgotPassword(email)
            AuthState.PasswordResetLinkSent(response.message, email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.toApiException()
            when (val state = error.toAuthState()) {
                AuthState.EmailVerificationNeeded -> AuthState.Error(error.message)
                else -> state
            }
        }
    }
}
