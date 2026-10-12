package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.LoginResponse
import com.oniolla.interviewer.data.model.ResendCodeResponse
import com.oniolla.interviewer.data.model.SignupResponse
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.ResendCodePayload
import com.oniolla.interviewer.data.payload.SignupPayload
import io.reactivex.rxjava3.core.Single
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface Api {

    @POST("auth/login")
    suspend fun login(@Body loginPayload: LoginPayload): LoginResponse

    @POST("auth/register")
    fun signup(@Body signupPayload: SignupPayload): Single<SignupResponse>

    @POST("/auth/email/resend-code")
    suspend fun resendVerificationCode(@Body resendCodePayload: ResendCodePayload): ResendCodeResponse
}