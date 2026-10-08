package com.example.kchat.feature.chat.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * In-memory LRU cache and persistent disk cache with background frame retriever for video thumbnails.
 */
object VideoThumbnailLoader {
    private const val TAG = "VideoThumbnailLoader"
    private val memoryCache = LruCache<String, Bitmap>(50)

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

    suspend fun loadThumbnail(videoUrl: String, context: Context? = null): Bitmap? = withContext(Dispatchers.IO) {
        if (videoUrl.isBlank()) return@withContext null

        // 1. In-memory LRU cache
        memoryCache.get(videoUrl)?.let { return@withContext it }

        // 2. Persistent disk cache
        val diskFile = if (context != null) {
            try {
                val cacheDir = File(context.cacheDir, "video_thumbnails").apply { if (!exists()) mkdirs() }
                File(cacheDir, "${hashKey(videoUrl)}.jpg")
            } catch (e: Exception) {
                null
            }
        } else null

        if (diskFile != null && diskFile.exists() && diskFile.length() > 0) {
            try {
                val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (diskBitmap != null) {
                    memoryCache.put(videoUrl, diskBitmap)
                    return@withContext diskBitmap
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading disk cache for thumbnail: ${e.message}")
            }
        }

        // 3. Fallback extraction via MediaMetadataRetriever
        try {
            val urlForExtraction = if (com.example.kchat.VaultMediaManager.isVaultUrl(videoUrl)) {
                com.example.kchat.VaultMediaManager.resolveMediaUrl(videoUrl) ?: return@withContext null
            } else {
                videoUrl
            }
            val retriever = MediaMetadataRetriever()
            if (urlForExtraction.startsWith("http://") || urlForExtraction.startsWith("https://")) {
                retriever.setDataSource(urlForExtraction, HashMap())
            } else {
                retriever.setDataSource(urlForExtraction)
            }
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            if (bitmap != null) {
                memoryCache.put(videoUrl, bitmap)
                if (diskFile != null) {
                    val tempFile = File(diskFile.parentFile, "${diskFile.name}.tmp")
                    try {
                        FileOutputStream(tempFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        if (tempFile.exists() && tempFile.length() > 0) {
                            tempFile.renameTo(diskFile)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed saving thumbnail to disk: ${e.message}")
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                    }
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load thumbnail for $videoUrl: ${e.message}")
            null
        }
    }
}
