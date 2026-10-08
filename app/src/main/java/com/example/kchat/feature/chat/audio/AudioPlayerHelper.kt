package com.example.kchat.feature.chat.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Single-playback manager for audio messages in KChat.
 * Ensures that only ONE audio message can play at any given time across the screen.
 * Starting playback on another message immediately stops the previous one.
 * Includes local disk caching for instant subsequent playback and offline capability.
 */
class AudioPlayerHelper(
    private val coroutineScope: CoroutineScope,
    private val context: Context? = null
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _currentPlayingUrl = MutableStateFlow<String?>(null)
    val currentPlayingUrl: StateFlow<String?> = _currentPlayingUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    companion object {
        const val TAG = "AudioPlayerHelper"
    }

    private fun hashKey(url: String): String {
        val cleanUrl = url.substringBefore('?')
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(cleanUrl.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            cleanUrl.hashCode().toString()
        }
    }

    private fun getCachedAudioFile(url: String, cacheKey: String? = null): File? {
        val ctx = context ?: return null
        return try {
            val key = cacheKey ?: url.substringBefore('?')
            val cacheDir = File(ctx.cacheDir, "audio_cache").apply { if (!exists()) mkdirs() }
            File(cacheDir, "${hashKey(key)}.m4a")
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadAudioToCache(remoteUrl: String, targetFile: File) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
                val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 15000
                    instanceFollowRedirects = true
                }
                if (connection.responseCode in 200..299) {
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        tempFile.renameTo(targetFile)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background caching of audio failed for $remoteUrl: ${e.message}")
            }
        }
    }

    /**
     * Toggles playback for the specified audio [url].
     * If the specified audio is currently playing, it will pause.
     * If paused on the same audio, it will resume.
     * If a different audio or no audio is playing, it will stop the old audio and start the new one.
     */
    fun togglePlayPause(url: String, defaultDurationMs: Long = 0L, cacheKey: String? = null) {
        if (_currentPlayingUrl.value == url) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
        } else {
            play(url, defaultDurationMs, cacheKey)
        }
    }

    /**
     * Starts playing audio from the specified [url] or local cached file.
     * Any previously playing audio is immediately stopped and released.
     */
    fun play(url: String, defaultDurationMs: Long = 0L, cacheKey: String? = null) {
        stop()

        _currentPlayingUrl.value = url
        _durationMs.value = defaultDurationMs
        _currentPositionMs.value = 0L

        try {
            val cachedFile = getCachedAudioFile(url, cacheKey)
            val dataSourceToUse = if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0) {
                cachedFile.absolutePath
            } else {
                if ((url.startsWith("http://") || url.startsWith("https://")) && cachedFile != null) {
                    downloadAudioToCache(url, cachedFile)
                }
                url
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(dataSourceToUse)
                setOnPreparedListener { mp ->
                    try {
                        val realDuration = mp.duration.toLong()
                        if (realDuration > 0L) {
                            _durationMs.value = realDuration
                        }
                        mp.start()
                        _isPlaying.value = true
                        startProgressTicker()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting prepared player", e)
                        stop()
                    }
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                    stopProgressTicker()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer for URL: $url", e)
            stop()
        }
    }

    /**
     * Pauses the current audio playback.
     */
    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing player", e)
        }
        _isPlaying.value = false
        stopProgressTicker()
    }

    /**
     * Resumes playback of the currently loaded audio.
     */
    fun resume() {
        val player = mediaPlayer
        if (player != null) {
            try {
                player.start()
                _isPlaying.value = true
                startProgressTicker()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming player", e)
                _currentPlayingUrl.value?.let { play(it, _durationMs.value) }
            }
        } else {
            _currentPlayingUrl.value?.let { play(it, _durationMs.value) }
        }
    }

    /**
     * Seeks playback to the requested position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking player", e)
        }
    }

    /**
     * Stops the current playback, resets state, and frees the MediaPlayer.
     */
    fun stop() {
        stopProgressTicker()
        try {
            mediaPlayer?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPlayingUrl.value = null
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
    }

    /**
     * Fully releases all resources when the chat screen leaves composition.
     */
    fun release() {
        stop()
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = coroutineScope.launch(Dispatchers.Main) {
            while (isActive && _isPlaying.value) {
                try {
                    val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                    _currentPositionMs.value = pos
                } catch (e: Exception) {
                    // Ignore
                }
                delay(100L)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }
}
