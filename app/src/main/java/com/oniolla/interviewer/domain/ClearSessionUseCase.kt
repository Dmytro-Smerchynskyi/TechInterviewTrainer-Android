package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import javax.inject.Inject

class ClearSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository
) : UseCase<Unit, Unit> {
    override suspend fun invoke(input: Unit) {
        authRepository.clearSession()
    }
}
