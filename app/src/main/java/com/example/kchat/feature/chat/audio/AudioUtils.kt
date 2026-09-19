package com.example.kchat.feature.chat.audio

import java.util.Locale

object AudioUtils {

    /**
     * Formats duration in milliseconds to "m:ss" format (e.g. 0:05, 1:23).
     * If duration is <= 0, returns "0:00".
     */
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
