package com.example.kchat.feature.chat.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import com.example.kchat.feature.chat.audio.AudioUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class VideoMetadata(
    val durationMs: Long,
    val fileSizeBytes: Long,
    val thumbnail: Bitmap?
)

data class PendingVideo(
    val file: File,
    val durationMs: Long,
    val thumbnail: Bitmap?
)

class CaptureVideoContract(
    private val maxDurationSeconds: Int = 120
) : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent {
        return Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxDurationSeconds)
            .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK
    }
}

object VideoUtils {

    const val TAG = "VideoUtils"
    const val MAX_VIDEO_DURATION_MS = 120_000L // 2 minutes
    const val MIN_VIDEO_DURATION_MS = 500L     // Ignore accidental taps < 500ms

    fun formatDuration(durationMs: Long): String {
        return AudioUtils.formatDuration(durationMs)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    suspend fun extractMetadata(file: File): VideoMetadata? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() <= 0L) return@withContext null
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val thumbnail = retriever.frameAtTime
            retriever.release()
            VideoMetadata(
                durationMs = durationMs,
                fileSizeBytes = file.length(),
                thumbnail = thumbnail
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata from file: ${file.absolutePath}", e)
            null
        }
    }

    suspend fun copyUriToTempFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val videoDir = File(context.cacheDir, "video_messages").apply {
                if (!exists()) mkdirs()
            }
            val tempFile = File(videoDir, "video_${System.currentTimeMillis()}.mp4")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0L) {
                tempFile
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy uri to temp file", e)
            null
        }
    }
}
