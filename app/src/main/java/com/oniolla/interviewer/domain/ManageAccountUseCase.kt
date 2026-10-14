package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.ApiException
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class PasswordChangeResult(
    val result: ContentResult<Unit>,
    val currentPasswordError: String? = null
)

class ManageAccountUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend fun profile() = loadContent { repository.getSessionProfile() }
    suspend fun refreshProfile() = loadContent { repository.getMe() }
    suspend fun name(value: String) = loadContent { repository.changeName(value) }
    suspend fun requestEmail(value: String) = loadContent { repository.requestEmailChange(value) }
    suspend fun resendEmail(value: String) = loadContent { repository.resendEmailChange(value) }
    suspend fun confirmEmail(email: String, code: String) = loadContent { repository.confirmEmailChange(email, code) }
    suspend fun changePassword(current: String, next: String): PasswordChangeResult = try {
        repository.changePassword(current, next)
        PasswordChangeResult(ContentResult.Success(Unit))
    } catch (error: ApiException.CurrentPasswordRejected) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Current password is incorrect"
        PasswordChangeResult(ContentResult.Error(message), currentPasswordError = message)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        PasswordChangeResult(loadContent { throw error })
    }
    suspend fun setPassword(value: String) = loadContent { repository.setPassword(value) }
}
