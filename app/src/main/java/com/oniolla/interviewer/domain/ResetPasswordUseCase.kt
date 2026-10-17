package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.ApiException
import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.payload.ResetPasswordPayload
import com.oniolla.interviewer.data.toApiException
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

sealed interface PasswordResetResult {
    data object Success : PasswordResetResult
    data object InvalidToken : PasswordResetResult
    data class Error(val message: String) : PasswordResetResult
}

class ResetPasswordUseCase @Inject constructor(private val repository: AuthRepository) {
    fun hasSession(): Boolean = repository.hasSession()

    suspend operator fun invoke(token: String, password: String): PasswordResetResult = try {
        repository.resetPassword(ResetPasswordPayload(token, password))
        PasswordResetResult.Success
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val apiError = error.toApiException()
        if (apiError is ApiException.BadRequest &&
            apiError.customError.code == "VALIDATION_ERROR" &&
            apiError.customError.message == "Reset token is invalid or expired.") {
            PasswordResetResult.InvalidToken
        } else {
            PasswordResetResult.Error(apiError.message?.takeIf { it.isNotBlank() }
                ?: "Unable to reset your password. Please try again.")
        }
    }
}
