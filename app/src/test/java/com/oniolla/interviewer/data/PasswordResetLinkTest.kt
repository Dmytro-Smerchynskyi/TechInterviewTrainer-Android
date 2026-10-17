package com.oniolla.interviewer.data

import android.content.Intent
import android.net.Uri
import com.oniolla.interviewer.ui.passwordResetToken
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PasswordResetLinkTest {
    private fun token(url: String) = passwordResetToken(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    @Test fun acceptsOurHttpsLinkAndDecodesOpaqueToken() {
        assertEquals("opaque+token/value=", token("https://interviewtrainer.app/resetpassword?token=opaque%2Btoken%2Fvalue%3D"))
        assertEquals("value", token("https://interviewtrainer.app:443/resetpassword?token=value"))
    }

    @Test fun rejectsUntrustedOrMalformedLinks() {
        listOf(
            "http://interviewtrainer.app/resetpassword?token=value",
            "https://interviewtrainer.app.attacker.test/resetpassword?token=value",
            "https://attacker.test/resetpassword?token=value",
            "https://attacker.test@interviewtrainer.app/resetpassword?token=value",
            "https://interviewtrainer.app:8443/resetpassword?token=value",
            "https://interviewtrainer.app/other?token=value",
            "https://interviewtrainer.app/resetpassword/?token=value",
            "https://interviewtrainer.app/resetpassword?token=value#fragment",
            "https://interviewtrainer.app/resetpassword",
            "https://interviewtrainer.app/resetpassword?token=",
            "https://interviewtrainer.app/resetpassword?token=%20",
            "https://interviewtrainer.app/resetpassword?token=one&token=two",
            "https://interviewtrainer.app/resetpassword?token=" + "x".repeat(8193)
        ).forEach { assertNull(token(it)) }
        assertNull(passwordResetToken(Intent(Intent.ACTION_MAIN,
            Uri.parse("https://interviewtrainer.app/resetpassword?token=value"))))
    }
}
