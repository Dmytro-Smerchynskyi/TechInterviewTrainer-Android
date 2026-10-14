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
    private val localUserData: LocalUserData? = null
) {
    private data class CachedProfile(val sessionVersion: Long, val profile: MeResponse)
    private var cachedProfile: CachedProfile? = null
    private val profileMutex = Mutex()

    suspend fun refreshSession(): Boolean = sessionRefresh.restoreSession()

    fun clearSession() {
        synchronized(tokenStorage) {
            tokenStorage.clear()
            cachedProfile = null
        }
    }

    suspend fun clearUserData() {
        clearSession()
        localUserData?.clear()
    }

    suspend fun login(loginPayload: LoginPayload): LoginResponse =
        apiCall { api.login(loginPayload) }.also(::saveTokens)

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

    private fun saveTokens(response: LoginResponse) {
        synchronized(tokenStorage) {
            tokenStorage.saveSession(response.accessToken, response.refreshToken)
            cachedProfile = null
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