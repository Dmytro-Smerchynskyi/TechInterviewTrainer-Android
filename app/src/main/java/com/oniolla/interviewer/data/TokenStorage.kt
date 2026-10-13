package com.oniolla.interviewer.data

import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

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
        @Synchronized get() = field ?: getRefresh()
        @Synchronized set(value) {
            setRefresh(value)
            field = value
        }

    private fun setRefresh(token: String?) {
        if (token == null) {
            sharedPreferences.edit {
                remove(REFRESH_TOKEN_KEY)
                remove(REFRESH_TOKEN_IV_KEY)
            }
            return
        }
        token.let { token ->
            val encryptedValue = encryptor.encrypt(token)
            sharedPreferences.edit {
                putString(REFRESH_TOKEN_KEY, encryptedValue.data)
                putString(REFRESH_TOKEN_IV_KEY, encryptedValue.iv)
            }
        }
    }

    private fun getRefresh(): String? {
        return encryptor.decrypt(
            Encryptor.EncryptedValue(
                sharedPreferences.getString(REFRESH_TOKEN_KEY, null) ?: return null,
                sharedPreferences.getString(REFRESH_TOKEN_IV_KEY, null) ?: return null
            )
        )
    }

    companion object {
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val REFRESH_TOKEN_IV_KEY = "refresh_token_iv"
    }
}