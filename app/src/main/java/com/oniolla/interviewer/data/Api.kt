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
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET

interface Api {

    @POST("auth/refresh")
    suspend fun refresh(@Body refreshPayload: RefreshPayload): LoginResponse

    @GET("me")
    suspend fun me(): MeResponse

    @POST("auth/email/verify")
    suspend fun verifyEmail(@Body verifyEmailPayload: VerifyEmailPayload): LoginResponse

    @POST("auth/password/forgot")
    suspend fun forgotPassword(@Body forgotPasswordPayload: ForgotPasswordPayload): ForgotPasswordResponse

    @POST("auth/login")
    suspend fun login(@Body loginPayload: LoginPayload): LoginResponse

    @POST("auth/register")
    fun signup(@Body signupPayload: SignupPayload): Single<SignupResponse>

    @POST("/auth/email/resend-code")
    suspend fun resendVerificationCode(@Body resendCodePayload: ResendCodePayload): ResendCodeResponse
}