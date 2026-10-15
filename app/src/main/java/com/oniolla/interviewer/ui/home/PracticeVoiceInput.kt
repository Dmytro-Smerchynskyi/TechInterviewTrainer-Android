package com.oniolla.interviewer.ui.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oniolla.interviewer.R
import java.util.Locale

internal data class VoiceInputActions(val record: () -> Unit, val rerecord: () -> Unit, val cancel: () -> Unit)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

@Composable
internal fun rememberPracticeVoiceInput(
    state: PracticeViewState,
    onText: (String) -> Unit,
    onStarted: (Long) -> Unit,
    onTranscribing: () -> Unit,
    onFinished: (String?) -> Unit,
    onLevel: (Float) -> Unit
): VoiceInputActions {
    val context = LocalContext.current
    val permissionDenied by rememberUpdatedState(stringResource(R.string.practice_mic_permission_denied))
    val activity = context.activity()
    val owner = LocalLifecycleOwner.current
    val current by rememberUpdatedState(state)
    val textCallback by rememberUpdatedState(onText)
    val startedCallback by rememberUpdatedState(onStarted)
    val transcribingCallback by rememberUpdatedState(onTranscribing)
    val finishedCallback by rememberUpdatedState(onFinished)
    val levelCallback by rememberUpdatedState(onLevel)
    val voice = remember(context) { VoiceTranscriber(context,
        { textCallback(it) }, { startedCallback(it) }, { transcribingCallback() },
        { finishedCallback(it) }, { levelCallback(it) }) }
    var rationale by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var pendingBase by remember { mutableStateOf("") }
    var pendingQuestion by remember { mutableStateOf<Long?>(null) }

    fun startIfStillAnswering() {
        if (current.page == PracticePage.ANSWERING && current.mode == AnswerMode.VOICE &&
            current.question?.id == pendingQuestion) {
            voice.start(pendingBase, current.question?.language)
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startIfStillAnswering()
        else {
            finishedCallback(permissionDenied)
            if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)) {
                settings = true
            }
        }
    }
    fun requestStart(base: String) {
        pendingBase = base
        pendingQuestion = current.question?.id
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                startIfStillAnswering()
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO) ->
                rationale = true
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    LaunchedEffect(state.page, state.mode) {
        if (state.page != PracticePage.ANSWERING || state.mode != AnswerMode.VOICE) voice.cancel()
    }
    DisposableEffect(voice, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) voice.cancel()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); voice.cancel() }
    }
    if (rationale) AlertDialog(onDismissRequest = { rationale = false },
        title = { Text(stringResource(R.string.practice_mic_permission_title)) },
        text = { Text(stringResource(R.string.practice_mic_permission_reason)) },
        confirmButton = { TextButton(onClick = { rationale = false; permission.launch(Manifest.permission.RECORD_AUDIO) }) {
            Text(stringResource(R.string.practice_allow))
        } },
        dismissButton = { TextButton(onClick = { rationale = false }) { Text(stringResource(R.string.practice_cancel)) } })
    if (settings) AlertDialog(onDismissRequest = { settings = false },
        title = { Text(stringResource(R.string.practice_mic_permission_title)) },
        text = { Text(stringResource(R.string.practice_mic_settings_reason)) },
        confirmButton = { TextButton(onClick = {
            settings = false
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)))
        }) { Text(stringResource(R.string.practice_open_settings)) } },
        dismissButton = { TextButton(onClick = { settings = false }) { Text(stringResource(R.string.practice_cancel)) } })
    return VoiceInputActions(
        record = { if (current.isRecording) voice.stop() else if (!current.isTranscribing) requestStart(current.answer) },
        rerecord = { voice.cancel(); requestStart("") },
        cancel = voice::cancel
    )
}

/** Owns one session; ignores callbacks from a canceled session. Main thread only. */
internal class VoiceTranscriber(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onStarted: (Long) -> Unit,
    private val onTranscribing: () -> Unit,
    private val onFinished: (String?) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val audioFactory: () -> SpeechAudioInput = {
        check(android.os.Build.VERSION.SDK_INT >= 33)
        MicrophoneSpeechAudio(context)
    }
) {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var audio: SpeechAudioInput? = null
    private var generation = 0
    private var sessionActive = false
    private var checking = false
    private var segmented = false
    private var stopping = false
    private var committed = ""
    private var partial = ""
    private var languageTag = ""
    private var startedAt = 0L

    fun start(base: String, language: String?) {
        cancel()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onFinished(context.getString(R.string.practice_mic_permission_denied))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFinished(context.getString(R.string.practice_speech_unavailable))
            return
        }
        committed = base.trim()
        partial = ""
        languageTag = language ?: Locale.getDefault().toLanguageTag()
        startedAt = SystemClock.elapsedRealtime()
        stopping = false
        sessionActive = true
        onStarted(startedAt)
        begin(trySegmented = android.os.Build.VERSION.SDK_INT >= 33)
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5_000L)
    }

    private fun combined(text: String) =
        listOf(committed, text.trim()).filter { it.isNotBlank() }.joinToString(" ").take(10_000)

    private fun commit(text: String = partial) {
        if (text.isNotBlank()) {
            committed = combined(text)
            onText(committed)
        }
        partial = ""
    }

    private fun begin(trySegmented: Boolean) {
        val id = ++generation
        fun active() = id == generation && recognizer != null
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    if (active() && !stopping && !segmented) onLevel((rmsdB / 12f).coerceIn(0f, 1f))
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (active() && !segmented) onTranscribing()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (!active()) return
                    text(partialResults)?.takeIf { it.isNotBlank() }?.let {
                        partial = it
                        onText(combined(it))
                    }
                }
                override fun onSegmentResults(segmentResults: Bundle) {
                    if (!active() || !segmented) return
                    commit(text(segmentResults) ?: partial)
                }
                override fun onEndOfSegmentedSession() {
                    if (!active()) return
                    commit()
                    // A provider ending before Stop does not support the requested stream lifetime.
                    if (segmented && !stopping) fallback("service ended segmented session before Stop") else finish(null)
                }
                override fun onResults(results: Bundle?) {
                    if (!active()) return
                    commit(text(results) ?: partial)
                    if (segmented && !stopping) fallback("service ended segmented session before Stop") else finish(null)
                }
                override fun onError(error: Int) {
                    if (!active()) return
                    debug("Recognition error=$error segmented=$segmented stopping=$stopping")
                    commit()
                    if (segmented && !stopping && error in setOf(SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_SERVER)) {
                        fallback("recognition error=$error")
                    } else if (stopping && error in setOf(SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) finish(null)
                    else finish(context.getString(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.practice_mic_permission_denied
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.practice_speech_no_match
                        else -> R.string.practice_speech_error
                    }))
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val request = intent()
            if (trySegmented && android.os.Build.VERSION.SDK_INT >= 33) {
                val source = audioFactory()
                audio = source
                source.attach(request)
                checking = true
                fun startStreaming() {
                    if (!active() || !checking) return
                    checking = false
                    handler.removeCallbacksAndMessages(null)
                    segmented = true
                    debug("Starting segmented audio stream")
                    try {
                        speech.startListening(request)
                        if (active() && segmented && !stopping) source.start(
                            { level -> handler.post { if (active() && !stopping) onLevel(level) } },
                            { error -> handler.post {
                                if (active()) {
                                    if (error is SecurityException) finish(context.getString(R.string.practice_mic_permission_denied))
                                    else fallback("audio source: ${error.javaClass.simpleName}")
                                }
                            } })
                    } catch (_: SecurityException) { finish(context.getString(R.string.practice_mic_permission_denied)) }
                    catch (error: Exception) { if (active()) fallback("stream start: ${error.javaClass.simpleName}") }
                }
                // Unable to check support does not mean the requested mode is unsupported.
                handler.postDelayed({
                    if (active() && checking) {
                        debug("Support check timed out; trying segmented mode")
                        startStreaming()
                    }
                }, 3_000)
                speech.checkRecognitionSupport(request, java.util.concurrent.Executor { handler.post(it) },
                    object : android.speech.RecognitionSupportCallback {
                        override fun onSupportResult(support: android.speech.RecognitionSupport) {
                            startStreaming()
                        }
                        override fun onError(error: Int) {
                            if (!active() || !checking) return
                            debug("Support check error=$error")
                            if (error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT) startStreaming()
                            else fallback("support check error=$error")
                        }
                    })
            } else {
                segmented = false
                debug("Starting legacy recognition")
                speech.startListening(request)
            }
        } catch (_: SecurityException) { finish(context.getString(R.string.practice_mic_permission_denied)) }
        catch (_: Exception) {
            if (trySegmented && recognizer != null) fallback("segmented setup exception")
            else finish(context.getString(R.string.practice_speech_unavailable))
        }
    }

    private fun text(results: Bundle?) = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun debug(message: String) {
        if (com.oniolla.interviewer.BuildConfig.DEBUG) android.util.Log.d("PracticeVoice", message)
    }

    private fun fallback(reason: String) {
        debug("Switching to legacy: $reason")
        if (stopping) { finish(null); return }
        commit()
        release(cancel = true)
        checking = false
        segmented = false
        val id = generation
        // Allow the capture thread to release the mic before the legacy service opens it.
        handler.postDelayed({ if (generation == id && !stopping) begin(trySegmented = false) }, 250)
    }

    fun stop() {
        if (stopping) return
        stopping = true
        if (checking || recognizer == null) { commit(); finish(null); return }
        onTranscribing()
        try {
            if (segmented) audio?.stop() else recognizer?.stopListening()
            val id = generation
            if (sessionActive) handler.postDelayed({
                if (sessionActive && generation == id) { commit(); finish(null) }
            }, 5_000)
        } catch (_: SecurityException) { finish(context.getString(R.string.practice_mic_permission_denied)) }
        catch (_: Exception) { commit(); finish(context.getString(R.string.practice_speech_error)) }
    }

    private fun finish(error: String?) {
        if (!sessionActive) return
        sessionActive = false
        handler.removeCallbacksAndMessages(null)
        release(cancel = true)
        checking = false
        segmented = false
        stopping = false
        onFinished(error)
    }

    private fun release(cancel: Boolean) {
        generation++
        audio?.close()
        audio = null
        val speech = recognizer
        recognizer = null
        try { if (cancel) speech?.cancel() } catch (_: RuntimeException) { }
        finally { try { speech?.destroy() } catch (_: RuntimeException) { } }
    }

    fun cancel() {
        val active = sessionActive
        sessionActive = false
        handler.removeCallbacksAndMessages(null)
        release(cancel = true)
        checking = false
        segmented = false
        stopping = false
        if (active) onFinished(null)
    }
}
