package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.ForgotPasswordResponse
import com.oniolla.interviewer.data.model.LoginResponse
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.model.ResendCodeResponse
import com.oniolla.interviewer.data.model.SignupResponse
import com.oniolla.interviewer.data.payload.ForgotPasswordPayload
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.RefreshPayload
import com.oniolla.interviewer.data.payload.ResendCodePayload
import com.oniolla.interviewer.data.payload.SignupPayload
import com.oniolla.interviewer.data.payload.VerifyEmailPayload
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val tokenStorage: SessionTokenStorage,
    private val api: Api
) {

    suspend fun refreshSession(): Boolean {
        val refreshToken = tokenStorage.refreshToken
        if (refreshToken.isNullOrBlank()) {
            clearSession()
            return false
        }
        apiCall { api.refresh(RefreshPayload(refreshToken)) }.also(::saveTokens)
        return true
    }

    fun clearSession() {
        tokenStorage.clear()
    }

    suspend fun login(loginPayload: LoginPayload): LoginResponse =
        apiCall { api.login(loginPayload) }.also(::saveTokens)

    suspend fun verifyEmail(payload: VerifyEmailPayload): LoginResponse =
        apiCall { api.verifyEmail(payload) }.also(::saveTokens)

    suspend fun getMe(): MeResponse = apiCall { api.me() }

    private fun saveTokens(response: LoginResponse) {
        tokenStorage.accessToken = response.accessToken
        tokenStorage.refreshToken = response.refreshToken
    }

    suspend fun forgotPassword(email: String): ForgotPasswordResponse = apiCall {
        api.forgotPassword(ForgotPasswordPayload(email))
    }

    suspend fun sendVerificationEmail(email: String): ResendCodeResponse = apiCall {
        api.resendVerificationCode(ResendCodePayload(email))
    }

    suspend fun signup(signupPayload: SignupPayload): Single<SignupResponse> = apiCall {
        api.signup(signupPayload)
    }
}