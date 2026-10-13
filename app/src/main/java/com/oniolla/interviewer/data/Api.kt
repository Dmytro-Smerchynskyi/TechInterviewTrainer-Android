package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionsResponse
import com.oniolla.interviewer.data.model.ForgotPasswordResponse
import com.oniolla.interviewer.data.model.LoginResponse
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.model.PreparationLanguagesResponse
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
import retrofit2.http.Tag

interface Api {

    @GET("dashboard")
    suspend fun dashboard(@Tag session: SessionSnapshot? = null): DashboardResponse

    @GET("questions")
    suspend fun questions(
        @Query("topicId") topicId: Long? = null,
        @Query("difficulty") difficulty: String? = null,
        @Query("grade") grade: String? = null,
        @Query("saved") saved: Boolean? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
        @Tag session: SessionSnapshot? = null
    ): QuestionsResponse

    @GET("topics")
    suspend fun topics(@Query("type") type: String? = null, @Tag session: SessionSnapshot? = null): List<TopicResponse>

    @GET("preparation-languages")
    suspend fun preparationLanguages(@Tag session: SessionSnapshot? = null): PreparationLanguagesResponse

    @PUT("me/preparation-language")
    suspend fun selectPreparationLanguage(@Body payload: PreparationLanguagePayload, @Tag session: SessionSnapshot? = null): MeResponse

    @PUT("me/specialization")
    suspend fun selectSpecialization(@Body payload: SpecializationPayload, @Tag session: SessionSnapshot? = null): MeResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body refreshPayload: RefreshPayload): LoginResponse

    @GET("me")
    suspend fun me(@Tag session: SessionSnapshot? = null): MeResponse

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