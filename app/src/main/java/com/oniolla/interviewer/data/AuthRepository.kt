package com.oniolla.interviewer.data

import com.oniolla.interviewer.data.payload.ChangeNamePayload
import com.oniolla.interviewer.data.payload.ChangeEmailPayload
import com.oniolla.interviewer.data.payload.ConfirmEmailChangePayload
import com.oniolla.interviewer.data.payload.ChangePasswordPayload
import com.oniolla.interviewer.data.payload.SetPasswordPayload
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
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class AuthRepository @Inject constructor(
    private val tokenStorage: SessionTokenStorage,
    private val api: Api,
    private val sessionRefresh: SessionRefreshCoordinator,
    private val localUserData: LocalUserData? = null,
    private val usageRepository: SessionUsageRepository = SessionUsageRepository(api, sessionRefresh, tokenStorage)
) {
    private data class CachedProfile(val sessionVersion: Long, val profile: MeResponse)
    private var cachedProfile: CachedProfile? = null
    private val profileMutex = Mutex()

    suspend fun refreshSession(): Boolean = sessionRefresh.restoreSession()

    fun clearSession() {
        synchronized(tokenStorage) {
            tokenStorage.clear()
            cachedProfile = null
            usageRepository.clear()
        }
    }

    suspend fun clearUserData() {
        clearSession()
        localUserData?.clear()
    }

    suspend fun login(loginPayload: LoginPayload): LoginResponse =
        apiCall { api.login(loginPayload) }.also(::saveTokens)

    suspend fun googleSignIn(
        payload: com.oniolla.interviewer.data.payload.GoogleSignInPayload,
        fromSignUp: Boolean
    ): LoginResponse {
        val response = apiCall { api.googleSignIn(payload) }
        currentCoroutineContext().ensureActive()
        check(response.accessToken.isNotBlank() && response.refreshToken.isNotBlank()) { "Invalid token response" }
        if (fromSignUp) clearUserData()
        saveTokens(response)
        return response
    }

    suspend fun verifyEmail(payload: VerifyEmailPayload): LoginResponse =
        apiCall { api.verifyEmail(payload) }.also(::saveTokens)

    suspend fun getMe(): MeResponse = updateProfile { api.me(it) }

    suspend fun getSessionProfile(): MeResponse = profileMutex.withLock {
        val cached = synchronized(tokenStorage) {
            cachedProfile?.takeIf { it.sessionVersion == tokenStorage.snapshot().version }
                .also { cachedProfile = it }
        }
        cached?.profile ?: getMe()
    }

    private suspend fun updateProfile(call: suspend (SessionSnapshot) -> MeResponse): MeResponse =
        sessionRefresh.authorizedApiCall { session ->
            val profile = call(session)
            currentCoroutineContext().ensureActive()
            synchronized(tokenStorage) {
                if (tokenStorage.snapshot().version != session.version) {
                    throw CancellationException("Session changed while loading profile")
                }
                cachedProfile = CachedProfile(session.version, profile)
            }
            profile
        }

    suspend fun selectSpecialization(specialization: Specialization): MeResponse = updateProfile {
        api.selectSpecialization(SpecializationPayload(specialization.code), it)
    }

    suspend fun getPreparationLanguages(): PreparationLanguagesResponse = sessionRefresh.authorizedApiCall { api.preparationLanguages(it) }

    suspend fun selectPreparationLanguage(language: String): MeResponse = updateProfile {
        api.selectPreparationLanguage(PreparationLanguagePayload(language), it)
    }

    fun cachedUsage() = usageRepository.observe()
    fun currentUsage() = usageRepository.current()
    suspend fun accountUsage(forceRefresh: Boolean = false) =
        if (forceRefresh) usageRepository.refresh() else usageRepository.get()

    suspend fun changeName(name: String): MeResponse = updateProfile {
        api.changeName(ChangeNamePayload(name), it)
    }

    suspend fun requestEmailChange(email: String, currentPassword: String? = null, idToken: String? = null) =
        sessionRefresh.authorizedApiCall { session ->
            withPasswordValidation { api.requestEmailChange(ChangeEmailPayload(email, currentPassword, idToken), session) }
        }

    suspend fun resendEmailChange(email: String, currentPassword: String? = null, idToken: String? = null) =
        sessionRefresh.authorizedApiCall { session ->
            withPasswordValidation { api.resendEmailChange(ChangeEmailPayload(email, currentPassword, idToken), session) }
        }

    suspend fun confirmEmailChange(email: String, code: String) = rotateAccountTokens {
        api.confirmEmailChange(ConfirmEmailChangePayload(email, code), it)
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) = rotateAccountTokens { session ->
        withPasswordValidation { api.changePassword(ChangePasswordPayload(currentPassword, newPassword), session) }
    }

    private suspend fun <T> withPasswordValidation(call: suspend () -> T): T =
        try {
            apiCall { call() }
        } catch (error: ApiException) {
            val customError = when (error) {
                is ApiException.Unauthorized -> error.customError
                is ApiException.BadRequest -> error.customError
                else -> null
            }
            // WRONG_PASSWORD is field validation, even when the HTTP status is 401.
            // All other errors keep their existing session/refresh handling.
            if (customError?.code != "WRONG_PASSWORD") throw error
            throw ApiException.CurrentPasswordRejected(customError)
        }

    suspend fun setPassword(password: String, idToken: String) = rotateAccountTokens {
        api.setPassword(SetPasswordPayload(password, idToken), it)
    }

    /** Rotate credentials within the same user session so caches and in-flight calls keep their owner. */
    private suspend fun rotateAccountTokens(call: suspend (SessionSnapshot) -> LoginResponse) {
        val (session, response) = sessionRefresh.authorizedApiCall { it to call(it) }
        currentCoroutineContext().ensureActive()
        check(response.accessToken.isNotBlank() && response.refreshToken.isNotBlank()) { "Invalid token response" }
        synchronized(tokenStorage) {
            val current = tokenStorage.snapshot()
            if (current.version != session.version) throw CancellationException("Session changed while updating credentials")
            tokenStorage.updateIfCurrent(current, response.accessToken, response.refreshToken)
                ?: throw CancellationException("Session credentials changed")
            cachedProfile = null
        }
    }

    private fun saveTokens(response: LoginResponse) {
        synchronized(tokenStorage) {
            tokenStorage.saveSession(response.accessToken, response.refreshToken)
            cachedProfile = null
            usageRepository.clear()
        }
    }

    suspend fun forgotPassword(email: String): ForgotPasswordResponse = apiCall {
        api.forgotPassword(ForgotPasswordPayload(email))
    }

    suspend fun sendVerificationEmail(email: String): ResendCodeResponse = apiCall {
        api.resendVerificationCode(ResendCodePayload(email))
    }

    suspend fun signup(signupPayload: SignupPayload): Single<SignupResponse> = try {
        clearUserData()
        apiCall { api.signup(signupPayload) }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Single.error(error)
    }
}