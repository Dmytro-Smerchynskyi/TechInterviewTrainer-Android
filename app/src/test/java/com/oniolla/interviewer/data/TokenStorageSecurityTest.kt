package com.oniolla.interviewer.data

import android.content.Context
import android.content.SharedPreferences
import com.oniolla.interviewer.domain.ResolveAuthStateUseCase
import com.oniolla.interviewer.domain.ResendVerificationCodeUseCase
import com.oniolla.interviewer.domain.RestoreSessionUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.security.KeyStoreException
import javax.crypto.AEADBadTagException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TokenStorageSecurityTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var tokens: TokenStorage

    @Before fun setup() {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("token-security", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("theme_mode", "DARK").commit()
        tokens = TokenStorage(preferences, Encryptor())
    }

    @Test fun cleartextPolicyMatchesTheBuildType() {
        val flags = RuntimeEnvironment.getApplication().applicationInfo.flags
        assertEquals(com.oniolla.interviewer.BuildConfig.DEBUG,
            flags and android.content.pm.ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
    }

    @Test fun emptyStorageHasNoSessionWithoutTouchingOtherPreferences() {
        assertNull(tokens.refreshToken)
        assertEquals(0L, tokens.sessionVersion)
        assertEquals("DARK", preferences.getString("theme_mode", null))
    }

    @Test fun incompleteStoredCredentialIsRemovedInsteadOfBeingRetriedForever() {
        preferences.edit().putString("refresh_token", "orphaned-ciphertext").commit()
        assertNull(tokens.refreshToken)
        assertFalse(preferences.contains("refresh_token"))
        assertFalse(preferences.contains("refresh_token_iv"))
        assertEquals("DARK", preferences.getString("theme_mode", null))
        assertEquals(1L, tokens.sessionVersion)
        assertNull(tokens.refreshToken)
        assertEquals(1L, tokens.sessionVersion)
    }

    @Test fun orphanedIvIsAlsoRemoved() {
        preferences.edit().putString("refresh_token_iv", "AAECAwQFBgcICQoL").commit()
        assertNull(tokens.refreshToken)
        assertFalse(preferences.contains("refresh_token_iv"))
    }

    @Test fun temporaryKeystoreFailurePreservesSessionAndStartupCanRetry() = runBlocking {
        preferences.edit().putString("refresh_token", "opaque-data").putString("refresh_token_iv", "opaque-iv").commit()
        val original = preferences.all.toMap()
        var unavailable = true
        var decryptions = 0
        val cipher = object : Encryptor() {
            override fun decrypt(encryptedValue: EncryptedValue): String {
                decryptions++
                if (unavailable) throw KeyStoreException("Temporarily unavailable")
                return "saved-refresh"
            }
        }
        tokens = TokenStorage(preferences, cipher)
        tokens.accessToken = "old-access"
        val api = Proxy.newProxyInstance(Api::class.java.classLoader, arrayOf(Api::class.java)) { _, _, _ ->
            throw AssertionError("Keystore failure must not start an HTTP request")
        } as Api
        val repository = AuthRepository(tokens, api, SessionRefreshCoordinator(tokens, api))
        val restore = RestoreSessionUseCase(repository,
            ResolveAuthStateUseCase(repository, ResendVerificationCodeUseCase(repository)))

        assertTrue(restore(Unit).toList().last() is AuthState.Error)
        assertEquals(original, preferences.all)
        assertEquals("old-access", tokens.accessToken)
        assertEquals(0L, tokens.sessionVersion)

        unavailable = false
        assertEquals("saved-refresh", tokens.refreshToken)
        assertEquals("saved-refresh", tokens.snapshot().refreshToken)
        assertEquals(2, decryptions)
        assertEquals(0L, tokens.sessionVersion)
    }

    @Test fun rotatedTokenIsCommittedBeforePublishingAndSurvivesNewStorageInstance() {
        var committed = false
        val recordingPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit(): Boolean {
                        committed = true
                        return editor.commit()
                    }
                    override fun apply() = throw AssertionError("Token persistence must be synchronous")
                }
            }
        }
        val cipher = object : Encryptor() {
            override fun encrypt(text: String) = EncryptedValue("opaque-" + text, "test-iv")
            override fun decrypt(encryptedValue: EncryptedValue) = encryptedValue.data.removePrefix("opaque-")
        }
        tokens = TokenStorage(recordingPreferences, cipher)
        tokens.saveSession("access", "refresh")
        committed = false
        val old = tokens.snapshot()
        tokens.updateIfCurrent(old, "new-access", "new-refresh")
        assertTrue(committed)
        assertEquals("new-access", tokens.accessToken)
        assertEquals("new-refresh", TokenStorage(preferences, cipher).refreshToken)
    }

    @Test fun failedPersistenceDoesNotPublishRotatedCredentials() {
        preferences.edit().putString("refresh_token", "old-opaque").putString("refresh_token_iv", "iv").commit()
        val failingPreferences = object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun commit() = false
                }
            }
        }
        val cipher = object : Encryptor() {
            override fun decrypt(encryptedValue: EncryptedValue) = "old-refresh"
            override fun encrypt(text: String) = EncryptedValue("new-opaque", "new-iv")
        }
        tokens = TokenStorage(failingPreferences, cipher)
        tokens.accessToken = "old-access"
        val old = tokens.snapshot()
        try {
            tokens.updateIfCurrent(old, "new-access", "new-refresh")
            fail("Persistence failure must be reported")
        } catch (_: IllegalStateException) {
            assertEquals(old, tokens.snapshot())
            assertEquals("old-opaque", preferences.getString("refresh_token", null))
        }
    }

    @Test fun corruptedTokenDropsTheWholeSessionOnce() {
        tokens = TokenStorage(preferences, object : Encryptor() {
            override fun decrypt(encryptedValue: EncryptedValue): String = throw AEADBadTagException("Corrupted data")
        })
        preferences.edit()
            .putString("refresh_token", "dG9rZW4=")
            .putString("refresh_token_iv", "AAECAwQFBgcICQoL")
            .commit()
        tokens.accessToken = "old-access"
        assertNull(tokens.refreshToken)
        assertNull(tokens.accessToken)
        assertFalse(preferences.contains("refresh_token"))
        assertFalse(preferences.contains("refresh_token_iv"))
        assertEquals("DARK", preferences.getString("theme_mode", null))
        assertEquals(1L, tokens.sessionVersion)
        assertNull(tokens.refreshToken)
        assertEquals(1L, tokens.sessionVersion)
    }
}
