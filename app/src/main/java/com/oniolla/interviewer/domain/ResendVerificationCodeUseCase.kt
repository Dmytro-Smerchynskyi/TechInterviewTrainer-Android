package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.toAuthState
import javax.inject.Inject

class ResendVerificationCodeUseCase @Inject constructor(
    private val authRepository: AuthRepository
) : UseCase<String, AuthState> {
    override suspend fun invoke(input: String): AuthState {
        try {
            authRepository.sendVerificationEmail(input)
            return AuthState.VerificationEmailSent
        } catch (e: ApiException) {
            return e.toAuthState()
        }
    }
}