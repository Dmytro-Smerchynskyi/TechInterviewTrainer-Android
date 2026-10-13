package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class RestoreSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val resolveAuthStateUseCase: ResolveAuthStateUseCase
) : UseCase<Unit, Flow<AuthState>> {
    override suspend fun invoke(input: Unit): Flow<AuthState> = flow {
        emit(AuthState.Loading(0f))

        val refreshResult = try {
            if (authRepository.refreshSession()) null else AuthState.Unauthorized
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            refreshFailure(e)
        }
        if (refreshResult != null) {
            emit(refreshResult)
            return@flow
        }

        emit(AuthState.Loading(0.5f))
        val result = resolveAuthStateUseCase(Unit)
        emit(AuthState.Loading(1f))
        emit(result)
    }

    private fun refreshFailure(exception: Exception): AuthState {
        val error = exception.toApiException()
        if (error is ApiException.Unauthorized) {
            return AuthState.Unauthorized
        }
        return when (val state = error.toAuthState()) {
            AuthState.EmailVerificationNeeded -> {
                authRepository.clearSession()
                AuthState.Unauthorized
            }
            else -> state
        }
    }
}
