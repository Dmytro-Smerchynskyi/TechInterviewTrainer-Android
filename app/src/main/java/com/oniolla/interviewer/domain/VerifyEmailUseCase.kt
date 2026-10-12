package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.payload.VerifyEmailPayload
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class VerifyEmailUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val resolveAuthStateUseCase: ResolveAuthStateUseCase
) : UseCase<VerifyEmailPayload, AuthState> {
    override suspend fun invoke(input: VerifyEmailPayload): AuthState {
        try {
            authRepository.verifyEmail(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.toApiException()
            val message = (error.toAuthState() as? AuthState.Error)?.message ?: error.message
            return AuthState.EmailVerificationFailed(message)
        }
        return resolveAuthStateUseCase(Unit)
    }
}
