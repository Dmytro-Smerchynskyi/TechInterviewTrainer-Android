package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.toAuthState
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val resendVerificationCodeUseCase: ResendVerificationCodeUseCase
) : UseCase<LoginPayload, AuthState> {

    override suspend fun invoke(input: LoginPayload): AuthState {
        return try {
            authRepository.login(input)
            AuthState.Authorized
        } catch (e: ApiException) {
            e.toAuthState().let { authState ->
                if (authState is AuthState.EmailVerificationNeeded) {
                    resendVerificationCodeUseCase(input.email)
                } else {
                    authState
                }
            }
        }
    }
}