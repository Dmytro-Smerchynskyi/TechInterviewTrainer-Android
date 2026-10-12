package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.model.SignupResponse
import com.oniolla.interviewer.data.payload.SignupPayload
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class SignupUseCase @Inject constructor(
    private val authRepository: AuthRepository
) : UseCase<SignupPayload, Single<SignupResponse>> {
    override suspend operator fun invoke(input: SignupPayload): Single<SignupResponse> {
        return authRepository.signup(input)
    }
}