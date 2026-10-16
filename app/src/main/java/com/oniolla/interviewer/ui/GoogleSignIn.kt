package com.oniolla.interviewer.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.oniolla.interviewer.BuildConfig
import com.oniolla.interviewer.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal sealed interface GoogleTokenResult {
    data class Success(val idToken: String) : GoogleTokenResult
    data class Failure(val message: String) : GoogleTokenResult
    data object Cancelled : GoogleTokenResult
}

/** Every invocation asks Google for a credential; the ID token is never persisted or reused. */
@Composable
internal fun rememberGoogleTokenRequest(): suspend () -> GoogleTokenResult {
    val context = LocalContext.current
    val manager = remember(context) { CredentialManager.create(context) }
    val failed = stringResource(R.string.google_sign_in_failed)
    val unavailable = stringResource(R.string.google_unavailable)
    val noCredentials = stringResource(R.string.google_no_credentials)
    val notConfigured = stringResource(R.string.google_not_configured)
    return {
        try {
            val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
            if (clientId.isBlank()) GoogleTokenResult.Failure(notConfigured)
            else {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
                val response = manager.getCredential(context, request)
                val credential = response.credential
                check(credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                check(token.isNotBlank())
                GoogleTokenResult.Success(token)
            }
        } catch (error: GetCredentialCancellationException) {
            val message = error.message.orEmpty()
            if (message.contains("Account reauth failed", ignoreCase = true) ||
                message.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true)) {
                GoogleTokenResult.Failure(failed)
            } else GoogleTokenResult.Cancelled
        } catch (_: NoCredentialException) {
            GoogleTokenResult.Failure(noCredentials)
        } catch (_: GetCredentialProviderConfigurationException) {
            GoogleTokenResult.Failure(unavailable)
        } catch (_: GetCredentialUnsupportedException) {
            GoogleTokenResult.Failure(unavailable)
        } catch (error: CancellationException) {
            throw error
        } catch (_: GetCredentialException) {
            GoogleTokenResult.Failure(failed)
        } catch (_: Exception) {
            GoogleTokenResult.Failure(failed)
        }
    }
}

/** Picker belongs to the screen scope; its Activity is never retained by the ViewModel. */
@Composable
internal fun rememberGoogleSignIn(viewModel: AuthViewModel): () -> Unit {
    val scope = rememberCoroutineScope()
    val request = rememberGoogleTokenRequest()
    return {
        if (viewModel.beginGoogleSignIn()) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var submitted = false
                try {
                    when (val result = request()) {
                        is GoogleTokenResult.Success -> {
                            viewModel.googleTokenReceived(result.idToken)
                            submitted = true
                        }
                        is GoogleTokenResult.Failure -> viewModel.googleSignInFailed(result.message)
                        GoogleTokenResult.Cancelled -> Unit
                    }
                } finally {
                    if (!submitted) viewModel.googleSignInCancelled()
                }
            }
        }
    }
}
