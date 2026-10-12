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
    override var accessToken: String? = null
    override var refreshToken: String? = null
        get() = field ?: getRefresh()
        set(value) {
            field = value
            setRefresh(value)
        }

    override fun clear() {
        accessToken = null
        refreshToken = null
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