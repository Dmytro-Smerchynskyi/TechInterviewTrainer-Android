package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.ForgotPasswordResponse
import com.oniolla.interviewer.data.model.LoginResponse
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.model.PreparationLanguagesResponse
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.data.model.ResendCodeResponse
import com.oniolla.interviewer.data.model.SignupResponse
import com.oniolla.interviewer.data.payload.ForgotPasswordPayload
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.PreparationLanguagePayload
import com.oniolla.interviewer.data.payload.ResendCodePayload
import com.oniolla.interviewer.data.payload.SignupPayload
import com.oniolla.interviewer.data.payload.SpecializationPayload
import com.oniolla.interviewer.data.payload.VerifyEmailPayload
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val tokenStorage: SessionTokenStorage,
    private val api: Api,
    private val sessionRefresh: SessionRefreshCoordinator
) {

    suspend fun refreshSession(): Boolean = sessionRefresh.restoreSession()

    fun clearSession() {
        tokenStorage.clear()
    }

    suspend fun login(loginPayload: LoginPayload): LoginResponse =
        apiCall { api.login(loginPayload) }.also(::saveTokens)

    suspend fun verifyEmail(payload: VerifyEmailPayload): LoginResponse =
        apiCall { api.verifyEmail(payload) }.also(::saveTokens)

    suspend fun getMe(): MeResponse = sessionRefresh.authorizedApiCall { api.me(it) }

    suspend fun selectSpecialization(specialization: Specialization): MeResponse = sessionRefresh.authorizedApiCall {
        api.selectSpecialization(SpecializationPayload(specialization.code), it)
    }

    suspend fun getPreparationLanguages(): PreparationLanguagesResponse = sessionRefresh.authorizedApiCall { api.preparationLanguages(it) }

    suspend fun selectPreparationLanguage(language: String): MeResponse = sessionRefresh.authorizedApiCall {
        api.selectPreparationLanguage(PreparationLanguagePayload(language), it)
    }

    private fun saveTokens(response: LoginResponse) {
        tokenStorage.saveSession(response.accessToken, response.refreshToken)
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