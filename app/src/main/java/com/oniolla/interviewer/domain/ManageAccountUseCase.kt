package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.ApiException
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class AccountMutationResult(
    val result: ContentResult<Unit>,
    val currentPasswordError: String? = null
)

class ManageAccountUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend fun profile() = loadContent { repository.getSessionProfile() }
    suspend fun refreshProfile() = loadContent { repository.getMe() }
    suspend fun name(value: String) = loadContent { repository.changeName(value) }
    suspend fun requestEmail(value: String, currentPassword: String? = null, idToken: String? = null) =
        mutation { repository.requestEmailChange(value, currentPassword, idToken) }
    suspend fun resendEmail(value: String, currentPassword: String? = null, idToken: String? = null) =
        mutation { repository.resendEmailChange(value, currentPassword, idToken) }
    suspend fun confirmEmail(email: String, code: String) = loadContent { repository.confirmEmailChange(email, code) }
    suspend fun changePassword(current: String, next: String) =
        mutation { repository.changePassword(current, next) }
    suspend fun setPassword(value: String, idToken: String) = loadContent { repository.setPassword(value, idToken) }

    suspend fun deleteAccount(currentPassword: String? = null, idToken: String? = null) =
        mutation { repository.deleteAccount(currentPassword, idToken) }

    private suspend fun mutation(call: suspend () -> Unit): AccountMutationResult = try {
        call()
        AccountMutationResult(ContentResult.Success(Unit))
    } catch (error: ApiException.CurrentPasswordRejected) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Current password is incorrect"
        AccountMutationResult(ContentResult.Error(message), currentPasswordError = message)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AccountMutationResult(loadContent { throw error })
    }
}
