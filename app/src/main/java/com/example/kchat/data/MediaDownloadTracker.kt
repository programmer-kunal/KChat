package com.example.kchat.data

import android.content.Context
import java.io.File

object MediaDownloadTracker {

    private fun getPrefs(context: Context, uid: String) =
        context.getSharedPreferences("kchat_media_downloads_$uid", Context.MODE_PRIVATE)

    fun isDownloaded(context: Context, uid: String, messageId: String, type: String): Boolean {
        if (uid.isBlank() || messageId.isBlank()) return false
        val prefs = getPrefs(context, uid)
        if (prefs.getBoolean("${type}_${messageId}", false)) {
            return true
        }

        // Also check if local decrypted file exists on disk
        return when (type) {
            "audio" -> {
                val f = File(context.noBackupFilesDir, "temp_dec_audio_${messageId}.m4a")
                f.exists() && f.length() > 0L
            }
            "doc" -> {
                val dir = context.noBackupFilesDir
                val files = dir.listFiles { _, name -> name.startsWith("temp_dec_doc_${messageId}_") }
                files != null && files.isNotEmpty() && files.any { it.length() > 0L }
            }
            "video" -> {
                val f = File(context.noBackupFilesDir, "temp_dec_video_${messageId}.mp4")
                f.exists() && f.length() > 0L
            }
            else -> false
        }
    }

    fun setDownloaded(context: Context, uid: String, messageId: String, type: String, downloaded: Boolean = true) {
        if (uid.isBlank() || messageId.isBlank()) return
        getPrefs(context, uid).edit().putBoolean("${type}_${messageId}", downloaded).apply()
    }
}
