package com.oniolla.interviewer.data

import android.Manifest
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.oniolla.interviewer.ui.home.SpeechAudioInput
import com.oniolla.interviewer.ui.home.VoiceTranscriber
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.LooperMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSpeechRecognizer
import java.time.Duration

@Implements(SpeechRecognizer::class)
class ListenerSpeechShadow : ShadowSpeechRecognizer() {
    lateinit var listener: RecognitionListener
    @Implementation
    public override fun handleChangeListener(value: RecognitionListener) {
        listener = value
        super.handleChangeListener(value)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], shadows = [ListenerSpeechShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class SegmentedVoiceTest {
    private class FakeAudio : SpeechAudioInput {
        var started = false
        var stopped = false
        var closed = false
        override fun attach(intent: Intent) {
            intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }
        override fun start(onLevel: (Float) -> Unit, onError: (Exception) -> Unit) { started = true }
        override fun stop() { stopped = true }
        override fun close() { closed = true }
    }

    private val audio = FakeAudio()
    private lateinit var voice: VoiceTranscriber
    private var text = "Draft"
    private var starts = 0
    private var finishes = 0
    private var transcribing = false
    private var error: String? = null

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(RecognitionService.SERVICE_INTERFACE), ResolveInfo().apply {
                serviceInfo = ServiceInfo().apply {
                    packageName = context.packageName
                    name = "TestRecognitionService"
                }
            })
        voice = VoiceTranscriber(context, { text = it }, { starts++ },
            { transcribing = true }, { finishes++; error = it }, {}, { audio })
        voice.start("Draft", "ru")
        idle()
        assertEquals(1, starts)
        assertEquals(0, finishes)
    }

    @After fun cleanup() { voice.cancel() }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun current(): ListenerSpeechShadow = Shadow.extract(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
    private fun result(value: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(value))
    }
    private fun supported(): ListenerSpeechShadow {
        val engine = current()
        engine.triggerSupportResult(RecognitionSupport.Builder().build())
        idle()
        assertTrue(audio.started)
        return engine
    }
    private fun fallbackDelay() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))

    @Test fun continuousSegmentsKeepRecordingUntilStopAndAccumulateWithoutDuplicatingPartials() {
        val engine = supported()
        assertEquals("ru", engine.lastRecognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertEquals(RecognizerIntent.EXTRA_AUDIO_SOURCE,
            engine.lastRecognizerIntent.getStringExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        engine.listener.onPartialResults(result("Первый"))
        engine.listener.onSegmentResults(result("Первый ответ"))
        engine.listener.onEndOfSpeech()
        assertFalse(transcribing)
        assertEquals(0, finishes)
        engine.listener.onSegmentResults(result("Продолжение"))
        assertEquals("Draft Первый ответ Продолжение", text)
        voice.stop()
        assertTrue(audio.stopped)
        assertTrue(transcribing)
        assertEquals(0, finishes)
        engine.listener.onEndOfSegmentedSession()
        assertEquals(1, finishes)
        assertTrue(audio.closed)
        assertNull(error)
    }

    @Test fun unsupportedServiceFallsBackOnceToLegacyRequest() {
        val old = current()
        old.triggerSupportError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED)
        idle()
        fallbackDelay()
        val legacy = current()
        assertNotSame(old, legacy)
        assertTrue(audio.closed)
        assertFalse(audio.started)
        assertFalse(legacy.lastRecognizerIntent.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        assertEquals(5_000L, legacy.lastRecognizerIntent.getLongExtra(
            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0))
        legacy.listener.onResults(result("Обычный ответ"))
        assertEquals("Draft Обычный ответ", text)
        assertEquals(1, finishes)
        fallbackDelay()
        assertSame(legacy, current())
    }

    @Test fun serviceIgnoringSegmentedModeFallsBackAndKeepsRecognizedText() {
        val engine = supported()
        engine.listener.onResults(result("Первый ответ"))
        fallbackDelay()
        val legacy = current()
        assertNotSame(engine, legacy)
        assertTrue(audio.closed)
        legacy.listener.onResults(result("Второй ответ"))
        assertEquals("Draft Первый ответ Второй ответ", text)
        assertEquals(1, finishes)
    }

    @Test fun stoppingWhileCheckingSupportDoesNotStartMicrophoneLater() {
        val engine = current()
        voice.stop()
        engine.triggerSupportResult(RecognitionSupport.Builder().build())
        idle()
        fallbackDelay()
        assertFalse(audio.started)
        assertTrue(audio.closed)
        assertEquals(1, finishes)
    }

    @Test fun cancelReleasesAudioAndRejectsLateResults() {
        val engine = supported()
        engine.listener.onSegmentResults(result("Ответ"))
        voice.cancel()
        engine.listener.onSegmentResults(result("Поздний"))
        engine.listener.onEndOfSegmentedSession()
        assertEquals("Draft Ответ", text)
        assertTrue(audio.closed)
        assertEquals(1, finishes)
    }

    @Test fun stopTimeoutKeepsLastPartialAndFinishesOnce() {
        val engine = supported()
        engine.listener.onPartialResults(result("Последние слова"))
        voice.stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        engine.listener.onEndOfSegmentedSession()
        assertEquals("Draft Последние слова", text)
        assertEquals(1, finishes)
    }

    @Test fun missingSupportCallbackTriesStreamingAndIgnoresLateCallback() {
        val engine = current()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3_250))
        assertSame(engine, current())
        assertTrue(audio.started)
        assertFalse(audio.closed)
        engine.triggerSupportError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
        idle()
        assertSame(engine, current())
        assertFalse(audio.closed)
    }

    @Test fun unableToCheckSupportStillTriesSegmentedMode() {
        val engine = current()
        engine.triggerSupportError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
        idle()
        assertTrue(audio.started)
        assertSame(engine, current())
        engine.listener.onSegmentResults(result("Непрерывный ответ"))
        engine.listener.onEndOfSpeech()
        assertEquals(0, finishes)
        assertEquals("Draft Непрерывный ответ", text)
        voice.stop()
        engine.listener.onEndOfSegmentedSession()
        assertEquals(1, finishes)
    }

    @Test @Config(sdk = [28], shadows = [ListenerSpeechShadow::class])
    fun oldAndroidUsesLegacyWithoutOpeningAudioSource() {
        val engine = current()
        assertFalse(audio.started)
        assertFalse(engine.lastRecognizerIntent.hasExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
        engine.listener.onResults(result("Ответ"))
        assertEquals("Draft Ответ", text)
        assertEquals(1, finishes)
    }
}
