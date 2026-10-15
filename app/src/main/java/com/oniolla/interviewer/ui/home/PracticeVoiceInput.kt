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
            voice.start(pendingBase, current.question?.requestedLanguage ?: current.question?.language)
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

/** Owns one recognition session; stale callbacks cannot change a later transcript. Main thread only. */
internal class VoiceTranscriber(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onStarted: (Long) -> Unit,
    private val onTranscribing: () -> Unit,
    private val onFinished: (String?) -> Unit,
    private val onLevel: (Float) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0

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
        val id = ++generation
        fun active() = id == generation
        fun combined(text: String) = listOf(base.trim(), text.trim()).filter { it.isNotBlank() }.joinToString(" ").take(10_000)
        try {
            val speech = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { if (active()) onLevel((rmsdB / 12f).coerceIn(0f, 1f)) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { if (active()) onTranscribing() }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (!active()) return
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }?.let { onText(combined(it)) }
                }
                override fun onResults(results: Bundle?) {
                    if (!active()) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) onText(combined(text))
                    finish(if (text.isNullOrBlank()) context.getString(R.string.practice_speech_no_match) else null)
                }
                override fun onError(error: Int) {
                    if (!active()) return
                    finish(context.getString(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.practice_mic_permission_denied
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.practice_speech_no_match
                        else -> R.string.practice_speech_error
                    }))
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            onStarted(SystemClock.elapsedRealtime())
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language ?: Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: SecurityException) { finish(context.getString(R.string.practice_mic_permission_denied)) }
        catch (_: RuntimeException) { finish(context.getString(R.string.practice_speech_unavailable)) }
    }

    fun stop() {
        try { recognizer?.stopListening(); onTranscribing() }
        catch (_: SecurityException) { finish(context.getString(R.string.practice_mic_permission_denied)) }
        catch (_: RuntimeException) { finish(context.getString(R.string.practice_speech_error)) }
    }

    private fun finish(error: String?) {
        release(cancel = false)
        onFinished(error)
    }

    private fun release(cancel: Boolean) {
        generation++
        val speech = recognizer
        recognizer = null
        try { if (cancel) speech?.cancel() } catch (_: RuntimeException) { /* Service already stopped. */ }
        finally { try { speech?.destroy() } catch (_: RuntimeException) { /* Service already detached. */ } }
    }

    fun cancel() {
        val active = recognizer != null
        release(cancel = true)
        if (active) onFinished(null)
    }
}
