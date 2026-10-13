package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SelectPreparationLanguageUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val resolveAuthStateUseCase: ResolveAuthStateUseCase
) : UseCase<String, AuthState> {
    override suspend fun invoke(input: String): AuthState = try {
        val profile = authRepository.selectPreparationLanguage(input)
        if (profile.preparationLanguage != input) {
            AuthState.Error("Preparation language was not saved")
        } else resolveAuthStateUseCase.resolveProfile(profile)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val apiError = error.toApiException()
        (apiError.toAuthState() as? AuthState.Error) ?: AuthState.Error(apiError.message)
    }
}
