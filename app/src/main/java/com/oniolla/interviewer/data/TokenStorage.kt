package com.oniolla.interviewer.data

import android.content.SharedPreferences
import java.security.GeneralSecurityException
import android.security.keystore.KeyPermanentlyInvalidatedException
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorage @Inject constructor(
    private val sharedPreferences: SharedPreferences,
    private val encryptor: Encryptor
) : SessionTokenStorage {
    @get:Synchronized
    @set:Synchronized
    override var sessionVersion: Long = 0

    @get:Synchronized
    @set:Synchronized
    override var accessToken: String? = null

    override var refreshToken: String? = null
        @Synchronized get() = field ?: getRefresh().also { field = it }
        @Synchronized set(value) {
            setRefresh(value)
            field = value
        }

    private fun setRefresh(token: String?) {
        val editor = sharedPreferences.edit()
        if (token == null) {
            editor.remove(REFRESH_TOKEN_KEY).remove(REFRESH_TOKEN_IV_KEY)
        } else {
            val encryptedValue = encryptor.encrypt(token)
            editor.putString(REFRESH_TOKEN_KEY, encryptedValue.data)
                .putString(REFRESH_TOKEN_IV_KEY, encryptedValue.iv)
        }
        // A rotated refresh token must reach disk before we publish the new session.
        check(editor.commit()) { "Unable to save your session. Please try again." }
    }

    private fun getRefresh(): String? {
        val data = sharedPreferences.getString(REFRESH_TOKEN_KEY, null)
        val iv = sharedPreferences.getString(REFRESH_TOKEN_IV_KEY, null)
        if (data == null && iv == null) return null
        if (data == null || iv == null) {
            clear()
            return null
        }
        return try {
            encryptor.decrypt(Encryptor.EncryptedValue(data, iv))
        } catch (_: AEADBadTagException) {
            clear()
            null
        } catch (_: KeyPermanentlyInvalidatedException) {
            clear()
            null
        } catch (error: GeneralSecurityException) {
            // Temporary Keystore failures must remain retryable, not erase valid credentials.
            throw IllegalStateException("Unable to access your saved session. Unlock your device and try again.", error)
        } catch (_: IllegalArgumentException) {
            clear()
            null
        }
    }

    companion object {
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val REFRESH_TOKEN_IV_KEY = "refresh_token_iv"
    }
}