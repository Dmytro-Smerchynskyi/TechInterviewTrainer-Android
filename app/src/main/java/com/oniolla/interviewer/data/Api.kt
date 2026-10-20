package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.model.DashboardResponse
import com.oniolla.interviewer.data.model.SubscriptionResponse
import com.oniolla.interviewer.data.payload.VerifyGooglePurchasePayload
import com.oniolla.interviewer.data.payload.ChangeNamePayload
import com.oniolla.interviewer.data.payload.ChangeEmailPayload
import com.oniolla.interviewer.data.payload.ConfirmEmailChangePayload
import com.oniolla.interviewer.data.payload.ChangePasswordPayload
import com.oniolla.interviewer.data.payload.SetPasswordPayload
import com.oniolla.interviewer.data.model.StatsResponse
import com.oniolla.interviewer.data.model.TopicResponse
import com.oniolla.interviewer.data.model.QuestionsResponse
import com.oniolla.interviewer.data.model.QuestionAnswerResponse
import com.oniolla.interviewer.data.model.QuestionEvaluationResponse
import com.oniolla.interviewer.data.payload.EvaluateQuestionPayload
import com.oniolla.interviewer.data.model.ForgotPasswordResponse
import com.oniolla.interviewer.data.model.LoginResponse
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.model.PreparationLanguagesResponse
import com.oniolla.interviewer.data.model.ResendCodeResponse
import com.oniolla.interviewer.data.model.SignupResponse
import com.oniolla.interviewer.data.payload.ForgotPasswordPayload
import com.oniolla.interviewer.data.payload.ResetPasswordPayload
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
import retrofit2.http.Path
import retrofit2.http.DELETE
import retrofit2.http.HTTP

interface Api {

    @POST("auth/google")
    suspend fun googleSignIn(@Body payload: com.oniolla.interviewer.data.payload.GoogleSignInPayload): LoginResponse

    @GET("interviews/history")
    suspend fun interviewHistory(
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
        @Tag session: SessionSnapshot? = null
    ): com.oniolla.interviewer.data.model.InterviewHistoryResponse

    @POST("interviews")
    suspend fun createInterview(
        @Body payload: com.oniolla.interviewer.data.payload.CreateInterviewPayload,
        @Tag session: SessionSnapshot? = null
    ): com.oniolla.interviewer.data.model.InterviewResponse

    @GET("interviews/{id}")
    suspend fun interview(@Path("id") id: String, @Tag session: SessionSnapshot? = null):
        com.oniolla.interviewer.data.model.InterviewResponse

    @GET("interviews/{id}/current-question")
    suspend fun currentInterviewQuestion(@Path("id") id: String, @Tag session: SessionSnapshot? = null):
        com.oniolla.interviewer.data.model.CurrentInterviewQuestion

    @POST("interviews/{id}/answers")
    suspend fun answerInterview(
        @Path("id") id: String,
        @Body payload: com.oniolla.interviewer.data.payload.InterviewAnswerPayload,
        @Tag session: SessionSnapshot? = null
    ): QuestionEvaluationResponse

    @POST("interviews/{id}/finish")
    suspend fun finishInterview(@Path("id") id: String, @Tag session: SessionSnapshot? = null):
        com.oniolla.interviewer.data.model.InterviewResponse

    @GET("interviews/{id}/result")
    suspend fun interviewResult(@Path("id") id: String, @Tag session: SessionSnapshot? = null):
        com.oniolla.interviewer.data.model.InterviewResponse


    @GET("me/subscription")
    suspend fun subscription(@Tag session: SessionSnapshot? = null): SubscriptionResponse

    @POST("billing/google/verify")
    suspend fun verifyGooglePurchase(@Body payload: VerifyGooglePurchasePayload,
        @Tag session: SessionSnapshot? = null): SubscriptionResponse

    @PUT("me/name")
    suspend fun changeName(@Body payload: ChangeNamePayload, @Tag session: SessionSnapshot? = null): MeResponse

    @POST("auth/email/change/request")
    suspend fun requestEmailChange(@Body payload: ChangeEmailPayload, @Tag session: SessionSnapshot? = null)

    @POST("auth/email/change/confirm")
    suspend fun confirmEmailChange(@Body payload: ConfirmEmailChangePayload, @Tag session: SessionSnapshot? = null): LoginResponse

    @POST("auth/email/change/resend")
    suspend fun resendEmailChange(@Body payload: ChangeEmailPayload, @Tag session: SessionSnapshot? = null)

    @POST("auth/password/change")
    suspend fun changePassword(@Body payload: ChangePasswordPayload, @Tag session: SessionSnapshot? = null): LoginResponse

    @POST("auth/password/set")
    suspend fun setPassword(@Body payload: SetPasswordPayload, @Tag session: SessionSnapshot? = null): LoginResponse

    @GET("stats")
    suspend fun stats(
        @Query("timeZone") timeZone: String? = null,
        @Query("weekStart") weekStart: String? = null,
        @Tag session: SessionSnapshot? = null
    ): StatsResponse

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
        @Tag session: SessionSnapshot? = null,
        @Query("mastered") mastered: Boolean? = null,
        @Query("evaluated") evaluated: Boolean? = null
    ): QuestionsResponse

    @GET("questions/{id}")
    suspend fun question(@Path("id") questionId: Long, @Tag session: SessionSnapshot? = null):
        com.oniolla.interviewer.data.model.QuestionResponse

    @POST("questions/{id}/evaluate")
    suspend fun evaluateQuestion(
        @Path("id") questionId: Long,
        @Body payload: EvaluateQuestionPayload,
        @Tag session: SessionSnapshot? = null
    ): QuestionEvaluationResponse

    @GET("questions/{id}/answer")
    suspend fun questionAnswer(
        @Path("id") questionId: Long,
        @Tag session: SessionSnapshot? = null
    ): QuestionAnswerResponse

    @GET("questions/saved")
    suspend fun savedQuestions(
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
        @Tag session: SessionSnapshot? = null
    ): QuestionsResponse

    @GET("reviews/due")
    suspend fun dueQuestions(
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: String? = null,
        @Tag session: SessionSnapshot? = null
    ): QuestionsResponse

    @PUT("questions/{id}/saved")
    suspend fun saveQuestion(@Path("id") questionId: Long, @Tag session: SessionSnapshot? = null)

    @DELETE("questions/{id}/saved")
    suspend fun unsaveQuestion(@Path("id") questionId: Long, @Tag session: SessionSnapshot? = null)

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

    @POST("auth/logout")
    suspend fun logout(@Body payload: RefreshPayload)

    @GET("me")
    suspend fun me(@Tag session: SessionSnapshot? = null): MeResponse

    @HTTP(method = "DELETE", path = "me", hasBody = true)
    suspend fun deleteAccount(@Body payload: com.oniolla.interviewer.data.payload.DeleteAccountPayload,
        @Tag session: SessionSnapshot? = null)

    @POST("auth/email/verify")
    suspend fun verifyEmail(@Body verifyEmailPayload: VerifyEmailPayload): LoginResponse

    @POST("auth/password/reset")
    suspend fun resetPassword(@Body payload: ResetPasswordPayload)

    @POST("auth/password/forgot")
    suspend fun forgotPassword(@Body forgotPasswordPayload: ForgotPasswordPayload): ForgotPasswordResponse

    @POST("auth/login")
    suspend fun login(@Body loginPayload: LoginPayload): LoginResponse

    @POST("auth/register")
    fun signup(@Body signupPayload: SignupPayload): Single<SignupResponse>

    @POST("/auth/email/resend-code")
    suspend fun resendVerificationCode(@Body resendCodePayload: ResendCodePayload): ResendCodeResponse
}