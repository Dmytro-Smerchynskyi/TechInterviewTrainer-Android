package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.ForgotPasswordResponse
import com.oniolla.interviewer.data.model.LoginResponse
import com.oniolla.interviewer.data.model.ResendCodeResponse
import com.oniolla.interviewer.data.model.SignupResponse
import com.oniolla.interviewer.data.payload.ForgotPasswordPayload
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.ResendCodePayload
import com.oniolla.interviewer.data.payload.SignupPayload
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

class AuthRepository @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val api: Api
) {

    suspend fun refreshSession(): Flow<AuthState> = flow {
        for (i in 0..100) {
            emit(AuthState.Loading(i / 100f))
            delay(10.milliseconds)
        }
        emit(AuthState.Unauthorized)
    }

    suspend fun login(loginPayload: LoginPayload): LoginResponse = apiCall {
        api.login(loginPayload)
    }.apply {
        tokenStorage.accessToken = accessToken
        tokenStorage.refreshToken = refreshToken
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