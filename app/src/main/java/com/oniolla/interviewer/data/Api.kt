package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionsResponse
import com.oniolla.interviewer.data.model.ForgotPasswordResponse
import com.oniolla.interviewer.data.model.LoginResponse
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.model.ResendCodeResponse
import com.oniolla.interviewer.data.model.SignupResponse
import com.oniolla.interviewer.data.payload.ForgotPasswordPayload
import com.oniolla.interviewer.data.payload.LoginPayload
import com.oniolla.interviewer.data.payload.PreparationLanguagePayload
import com.oniolla.interviewer.data.payload.RefreshPayload
import com.oniolla.interviewer.data.payload.ResendCodePayload
import com.oniolla.interviewer.data.payload.SignupPayload
import com.oniolla.interviewer.data.payload.SpecializationPayload
import com.oniolla.interviewer.data.payload.VerifyEmailPayload
import io.reactivex.rxjava3.core.Single
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Query

interface Api {

    @GET("dashboard")
    suspend fun dashboard(): DashboardResponse

    @GET("questions")
    suspend fun questions(
        @Query("topicId") topicId: Long? = null,
        @Query("difficulty") difficulty: String? = null,
        @Query("grade") grade: String? = null,
        @Query("saved") saved: Boolean? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null
    ): QuestionsResponse

    @GET("topics")
    suspend fun topics(): List<TopicResponse>

    @PUT("me/preparation-language")
    suspend fun selectPreparationLanguage(@Body payload: PreparationLanguagePayload): MeResponse

    @PUT("me/specialization")
    suspend fun selectSpecialization(@Body payload: SpecializationPayload): MeResponse

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