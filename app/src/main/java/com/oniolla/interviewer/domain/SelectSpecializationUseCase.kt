package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class SelectSpecializationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val resolveAuthStateUseCase: ResolveAuthStateUseCase
) : UseCase<Specialization, AuthState> {
    override suspend fun invoke(input: Specialization): AuthState {
        return try {
            val profile = authRepository.selectSpecialization(input)
            resolveAuthStateUseCase.resolveProfile(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.toApiException()
            (error.toAuthState() as? AuthState.Error) ?: AuthState.Error(error.message)
        }
    }
}
