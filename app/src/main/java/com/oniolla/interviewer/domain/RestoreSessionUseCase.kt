package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RestoreSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository
): UseCase<Unit, Flow<AuthState>> {
    override suspend fun invoke(input: Unit): Flow<AuthState> {
        return authRepository.refreshSession()
    }
}