package com.example.kchat.feature.chat.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lifecycle-safe audio recorder wrapping Android's MediaRecorder.
 * Records AAC audio into an MPEG-4 (.m4a) container in the app cache directory.
 */
class AudioRecorderHelper(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private var timerJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    var onMaxDurationReached: (() -> Unit)? = null

    companion object {
        const val TAG = "AudioRecorderHelper"
        const val MAX_DURATION_MS = 120_000L // 2 minutes max
        const val MIN_DURATION_MS = 500L     // Ignore accidental taps < 500ms
    }

    /**
     * Starts audio recording. Returns true if successfully started.
     */
    fun startRecording(): Boolean {
        if (_isRecording.value) return false

        return try {
            val audioDir = File(context.cacheDir, "audio_records").apply {
                if (!exists()) mkdirs()
            }
            val file = File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
            currentFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            startTimeMs = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationMs.value = 0L

            timerJob?.cancel()
            timerJob = coroutineScope.launch(Dispatchers.Main) {
                while (isActive && _isRecording.value) {
                    val elapsed = System.currentTimeMillis() - startTimeMs
                    _recordingDurationMs.value = elapsed
                    if (elapsed >= MAX_DURATION_MS) {
                        onMaxDurationReached?.invoke()
                        break
                    }
                    delay(100L)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            cleanup()
            false
        }
    }

    /**
     * Stops audio recording and returns the recorded file and duration in ms.
     * Returns null if recording failed or was shorter than [MIN_DURATION_MS].
     */
    fun stopRecording(): Pair<File, Long>? {
        if (!_isRecording.value) return null

        timerJob?.cancel()
        timerJob = null

        val durationMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
        val file = currentFile

        val success = try {
            mediaRecorder?.stop()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop MediaRecorder (recording may be too short)", e)
            false
        } finally {
            cleanupRecorder()
        }

        _isRecording.value = false
        _recordingDurationMs.value = 0L

        return if (success && file != null && file.exists() && file.length() > 0L && durationMs >= MIN_DURATION_MS) {
            Pair(file, durationMs)
        } else {
            try {
                file?.delete()
            } catch (e: Exception) {
                // Ignore
            }
            currentFile = null
            null
        }
    }

    /**
     * Cancels recording, stops MediaRecorder, and deletes any temporary file.
     */
    fun cancelRecording() {
        timerJob?.cancel()
        timerJob = null
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Ignore
        } finally {
            cleanup()
        }
    }

    private fun cleanupRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaRecorder = null
    }

    private fun cleanup() {
        cleanupRecorder()
        _isRecording.value = false
        _recordingDurationMs.value = 0L
        try {
            currentFile?.delete()
        } catch (e: Exception) {
            // Ignore
        }
        currentFile = null
    }
}
