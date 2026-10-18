package com.oniolla.interviewer.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

open class Encryptor @Inject constructor() {

    private fun getOrCreateKey(): SecretKey {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
            getKey(KEY_ALIAS, null)?.let {
                return it as SecretKey
            }
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    open fun encrypt(text: String): EncryptedValue {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val data = doFinal(text.toByteArray(Charsets.UTF_8))
            return EncryptedValue(
                Base64.encodeToString(data, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        }
    }

    open fun decrypt(encryptedValue: EncryptedValue): String {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE, getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(encryptedValue.iv, Base64.NO_WRAP))
            )
            return doFinal(
                Base64.decode(
                    encryptedValue.data,
                    Base64.NO_WRAP
                )
            ).toString(Charsets.UTF_8)
        }
    }

    data class EncryptedValue(
        val data: String,
        val iv: String
    )

    companion object {
        private const val KEY_ALIAS = "auth_token_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}