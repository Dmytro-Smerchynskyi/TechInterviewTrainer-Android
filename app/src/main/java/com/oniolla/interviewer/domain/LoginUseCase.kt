package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val resendVerificationCodeUseCase: ResendVerificationCodeUseCase,
    private val resolveAuthStateUseCase: ResolveAuthStateUseCase
) : UseCase<LoginPayload, AuthState> {
    override suspend fun invoke(input: LoginPayload): AuthState {
        try {
            authRepository.login(input)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return when (val state = e.toApiException().toAuthState()) {
                AuthState.EmailVerificationNeeded -> {
                    when (val result = resendVerificationCodeUseCase(input.email)) {
                        is AuthState.Error -> AuthState.EmailVerificationPending(input.email, result.message)
                        else -> result
                    }
                }
                else -> state
            }
        }
        return resolveAuthStateUseCase(Unit)
    }
}
