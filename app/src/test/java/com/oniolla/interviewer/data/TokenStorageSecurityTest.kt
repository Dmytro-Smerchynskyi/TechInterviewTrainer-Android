package com.oniolla.interviewer.data

import android.content.Context
import android.content.SharedPreferences
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

    @Test fun unreadableRestoredTokenDropsTheWholeSessionOnce() {
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
