package com.example.kchat.feature.chat.video

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single-instance video playback manager for KChat.
 * Guarantees that only ONE video plays at any given time across ChatScreen.
 * Starting another video stops the previous playback cleanly.
 */
class VideoPlayerHelper(
    private val coroutineScope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var attachedSurface: Surface? = null
    private var progressJob: Job? = null

    private val _currentPlayingUrl = MutableStateFlow<String?>(null)
    val currentPlayingUrl: StateFlow<String?> = _currentPlayingUrl.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    companion object {
        const val TAG = "VideoPlayerHelper"
    }

    fun togglePlayPause(url: String, defaultDurationMs: Long = 0L) {
        if (_currentPlayingUrl.value == url) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
        } else {
            play(url, defaultDurationMs)
        }
    }

    fun play(url: String, defaultDurationMs: Long = 0L) {
        stop()

        _currentPlayingUrl.value = url
        _durationMs.value = defaultDurationMs
        _currentPositionMs.value = 0L
        _isBuffering.value = true
        _hasError.value = false

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(url)
                attachedSurface?.let { surf ->
                    if (surf.isValid) {
                        setSurface(surf)
                    }
                }
                setOnPreparedListener { mp ->
                    try {
                        _isBuffering.value = false
                        val realDuration = mp.duration.toLong()
                        if (realDuration > 0L) {
                            _durationMs.value = realDuration
                        }
                        mp.start()
                        _isPlaying.value = true
                        startProgressTicker()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error starting prepared video player", e)
                        _hasError.value = true
                        stop()
                    }
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                    stopProgressTicker()
                }
                setOnInfoListener { _, what, _ ->
                    when (what) {
                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> _isBuffering.value = true
                        MediaPlayer.MEDIA_INFO_BUFFERING_END -> _isBuffering.value = false
                    }
                    true
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    _hasError.value = true
                    stop()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer for video URL: $url", e)
            _hasError.value = true
            stop()
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing video player", e)
        }
        _isPlaying.value = false
        stopProgressTicker()
    }

    fun resume() {
        val player = mediaPlayer
        if (player != null) {
            try {
                player.start()
                _isPlaying.value = true
                startProgressTicker()
            } catch (e: Exception) {
                Log.e(TAG, "Error resuming video player", e)
                _currentPlayingUrl.value?.let { play(it, _durationMs.value) }
            }
        } else {
            _currentPlayingUrl.value?.let { play(it, _durationMs.value) }
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking video player", e)
        }
    }

    fun attachSurface(surface: Surface) {
        attachedSurface = surface
        try {
            if (surface.isValid) {
                mediaPlayer?.setSurface(surface)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching surface to player", e)
        }
    }

    fun detachSurface() {
        attachedSurface = null
        try {
            mediaPlayer?.setSurface(null)
        } catch (e: Exception) {
            // Ignore
        }
    }

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
        _isBuffering.value = false
    }

    fun release() {
        stop()
        attachedSurface = null
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
