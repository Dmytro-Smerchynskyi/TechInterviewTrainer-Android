package com.oniolla.interviewer.domain

import com.oniolla.interviewer.data.AuthRepository
import com.oniolla.interviewer.data.AuthState
import com.oniolla.interviewer.data.payload.GoogleSignInPayload
import com.oniolla.interviewer.data.toApiException
import com.oniolla.interviewer.data.toAuthState
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class GoogleSignInRequest(val idToken: String, val fromSignUp: Boolean)

class GoogleSignInUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val resolveAuthState: ResolveAuthStateUseCase
) : UseCase<GoogleSignInRequest, AuthState> {
    override suspend fun invoke(input: GoogleSignInRequest): AuthState {
        if (input.idToken.isBlank()) return AuthState.Error("Unable to sign in with Google. Please try again.")
        try {
            repository.googleSignIn(GoogleSignInPayload(input.idToken), input.fromSignUp)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { return error.toApiException().toAuthState() }
        return resolveAuthState(Unit)
    }
}
