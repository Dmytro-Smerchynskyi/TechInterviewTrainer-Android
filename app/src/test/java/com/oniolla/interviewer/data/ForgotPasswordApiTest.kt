package com.oniolla.interviewer.data

import com.google.gson.JsonParser
import com.oniolla.interviewer.data.payload.ForgotPasswordPayload
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import com.oniolla.interviewer.ui.toUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class ForgotPasswordApiTest {
    private fun api(status: Int = 200, body: String, checkRequest: (okhttp3.Request) -> Unit = {}): Api {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                checkRequest(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(status)
                    .message("Test response")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://example.test/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(Api::class.java)
    }

    @Test
    fun sendsPostWithEmailAndReadsSuccessMessage() = runBlocking {
        val api = api(body = """{"message":"Reset email sent"}""") { request ->
            assertEquals("POST", request.method)
            assertEquals("/auth/password/forgot", request.url.encodedPath)
            val buffer = Buffer()
            request.body!!.writeTo(buffer)
            val payload = JsonParser.parseString(buffer.readUtf8()).asJsonObject
            assertEquals(setOf("email"), payload.keySet())
            assertEquals("developer@example.com", payload["email"].asString)
        }
        val response = apiCall { api.forgotPassword(ForgotPasswordPayload("developer@example.com")) }
        assertEquals("Reset email sent", response.message)
    }

    @Test
    fun httpErrorsKeepServerMessageAndCategory() = runBlocking {
        listOf(400, 401, 403, 404, 429, 500).forEach { status ->
            val api = api(status, """{"error":{"code":"REQUEST_FAILED","message":"Try again later"}}""")
            try {
                apiCall { api.forgotPassword(ForgotPasswordPayload("developer@example.com")) }
                fail("Expected HTTP error for $status")
            } catch (error: ApiException) {
                when (status) {
                    400, 429 -> assertTrue(error is ApiException.BadRequest)
                    401 -> assertTrue(error is ApiException.Unauthorized)
                    403 -> assertTrue(error is ApiException.Forbidden)
                    404 -> assertTrue(error is ApiException.NotFound)
                    500 -> assertTrue(error is ApiException.ServerError)
                }
                assertSame(error, error.toApiException())
                val state = error.toAuthState() as AuthState.Error
                assertEquals("Try again later", state.message)
                assertEquals(AuthViewState.Error("Try again later"), state.toUi())
            }
        }
    }

    @Test
    fun networkErrorUsesExistingUserFacingMessage() = runBlocking {
        try {
            apiCall<Unit> { throw IOException("Connection failed") }
            fail("Expected network error")
        } catch (error: ApiException.NetworkError) {
            assertEquals(AuthState.Error("Something unexpected happened"), error.toAuthState())
        }
    }

    @Test
    fun cancellationIsNotConvertedToNetworkError() = runBlocking {
        val cancellation = CancellationException("Cancelled")
        try {
            apiCall<Unit> { throw cancellation }
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertSame(cancellation, error)
        }
    }

    @Test
    fun successfulResetMapsToRecoveryScreenWithSentEmail() {
        val state = AuthState.PasswordResetLinkSent("Reset email sent", "developer@example.com")
            .toUi() as AuthViewState.Unauthorized.ForgotPassword
        assertTrue(state.resetLinkSent)
        assertFalse(state.isLoading)
        assertNull(state.emailError)
        assertEquals("developer@example.com", state.sentEmail)
    }
}
