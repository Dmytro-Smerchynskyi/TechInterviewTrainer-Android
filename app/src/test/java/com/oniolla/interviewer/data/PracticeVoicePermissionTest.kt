package com.oniolla.interviewer.data

import android.Manifest
import com.oniolla.interviewer.R
import com.oniolla.interviewer.ui.home.VoiceTranscriber
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PracticeVoicePermissionTest {
    @Test fun deniedMicrophoneAccessKeepsTheDraftAndDoesNotStartRecognition() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.RECORD_AUDIO)
        var text = "Existing draft"
        var started = false
        var transcribing = false
        var error: String? = null
        val voice = VoiceTranscriber(context, { text = it }, { started = true },
            { transcribing = true }, { error = it }, {})
        voice.start("", "en")
        assertFalse(started)
        assertFalse(transcribing)
        assertEquals("Existing draft", text)
        assertEquals(context.getString(R.string.practice_mic_permission_denied), error)
        voice.cancel()
        assertEquals(context.getString(R.string.practice_mic_permission_denied), error)
    }
}
