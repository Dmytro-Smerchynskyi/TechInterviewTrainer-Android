package com.oniolla.interviewer.ui.home

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.math.abs

/** Audio source is separate from the recognizer so Stop closes the stream, not the session. */
internal interface SpeechAudioInput : Closeable {
    fun attach(intent: Intent)
    fun start(onLevel: (Float) -> Unit, onError: (Exception) -> Unit)
    fun stop()
}

@RequiresApi(33)
internal class MicrophoneSpeechAudio(private val context: android.content.Context) : SpeechAudioInput {
    private val pipe = ParcelFileDescriptor.createPipe()
    @Volatile private var running = false
    private var started = false
    private var closed = false

    init {
        try {
            // Never let a service that ignores the source block the capture thread indefinitely.
            Os.fcntlInt(pipe[1].fileDescriptor, OsConstants.F_SETFL, OsConstants.O_NONBLOCK)
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    override fun attach(intent: Intent) {
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SAMPLE_RATE)
        intent.putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
    }

    override fun start(onLevel: (Float) -> Unit, onError: (Exception) -> Unit) {
        check(!started && !closed)
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) throw SecurityException("Microphone permission denied")
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Microphone format is not supported" }
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum * 2, 6_400)).build()
        try {
            check(record.state == AudioRecord.STATE_INITIALIZED)
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        } catch (error: Exception) {
            record.release()
            throw error
        }
        started = true
        running = true
        thread(name = "practice-speech-audio", isDaemon = true) {
            try {
                val buffer = ByteArray(3_200)
                var lastLevelAt = 0L
                var blockedAt = 0L
                while (running) {
                    val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                    if (count < 0) throw IOException("Microphone read failed: $count")
                    if (count == 0) { Thread.sleep(10); continue }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastLevelAt >= 100) {
                        var peak = 0
                        for (i in 0 until count - 1 step 2) {
                            val sample = (buffer[i].toInt() and 255) or (buffer[i + 1].toInt() shl 8)
                            peak = maxOf(peak, abs(sample))
                        }
                        onLevel((peak / 12_000f).coerceIn(0f, 1f))
                        lastLevelAt = now
                    }
                    var offset = 0
                    while (running && offset < count) {
                        try {
                            offset += Os.write(pipe[1].fileDescriptor, buffer, offset, count - offset)
                            blockedAt = 0L
                        } catch (error: ErrnoException) {
                            if (error.errno != OsConstants.EAGAIN && error.errno != OsConstants.EINTR) throw error
                            if (blockedAt == 0L) blockedAt = SystemClock.elapsedRealtime()
                            if (SystemClock.elapsedRealtime() - blockedAt > 3_000) {
                                throw IOException("Recognition service is not reading the audio source")
                            }
                            Thread.sleep(10)
                        }
                    }
                }
            } catch (error: Exception) {
                if (running) onError(error)
            } finally {
                running = false
                try { record.stop() } catch (_: RuntimeException) { }
                record.release()
                closeDescriptor(pipe[1])
            }
        }
    }

    override fun stop() {
        running = false
        if (!started) closeDescriptor(pipe[1])
        // The capture thread releases the mic and closes the writer, delivering EOF to the service.
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
        closeDescriptor(pipe[0])
    }

    private fun closeDescriptor(descriptor: ParcelFileDescriptor) {
        try { descriptor.close() } catch (_: IOException) { }
    }

    private companion object { const val SAMPLE_RATE = 16_000 }
}
