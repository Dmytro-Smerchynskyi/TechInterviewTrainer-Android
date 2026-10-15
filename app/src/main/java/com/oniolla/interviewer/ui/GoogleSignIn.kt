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

/** Picker belongs to the screen scope; its Activity is never retained by the ViewModel. */
@Composable
internal fun rememberGoogleSignIn(viewModel: AuthViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context) { CredentialManager.create(context) }
    val failed = stringResource(R.string.google_sign_in_failed)
    val unavailable = stringResource(R.string.google_unavailable)
    val noCredentials = stringResource(R.string.google_no_credentials)
    val notConfigured = stringResource(R.string.google_not_configured)
    return {
        if (viewModel.beginGoogleSignIn()) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var submitted = false
                try {
                    val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                    if (clientId.isBlank()) {
                        viewModel.googleSignInFailed(notConfigured)
                    } else {
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build()).build()
                        val response = manager.getCredential(context, request)
                        val credential = response.credential
                        check(credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                        val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                        check(token.isNotBlank())
                        viewModel.googleTokenReceived(token)
                        submitted = true
                    }
                } catch (error: GetCredentialCancellationException) {
                    // Google also reports failed account reauthentication as cancellation.
                    // Keep a genuine dismissal silent, but surface provider authorization failures.
                    val message = error.message.orEmpty()
                    if (message.contains("Account reauth failed", ignoreCase = true) ||
                        message.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true)) {
                        viewModel.googleSignInFailed(failed)
                    }
                } catch (_: NoCredentialException) {
                    viewModel.googleSignInFailed(noCredentials)
                } catch (_: GetCredentialProviderConfigurationException) {
                    viewModel.googleSignInFailed(unavailable)
                } catch (_: GetCredentialUnsupportedException) {
                    viewModel.googleSignInFailed(unavailable)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: GetCredentialException) {
                    viewModel.googleSignInFailed(failed)
                } catch (_: Exception) {
                    viewModel.googleSignInFailed(failed)
                } finally {
                    if (!submitted) viewModel.googleSignInCancelled()
                }
            }
        }
    }
}
