package com.example.kchat.feature.chat.video

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory LRU cache and background frame retriever for video thumbnails.
 */
object VideoThumbnailLoader {
    private const val TAG = "VideoThumbnailLoader"
    private val memoryCache = LruCache<String, Bitmap>(50)

    suspend fun loadThumbnail(videoUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        if (videoUrl.isBlank()) return@withContext null
        memoryCache.get(videoUrl)?.let { return@withContext it }
        try {
            val retriever = MediaMetadataRetriever()
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                retriever.setDataSource(videoUrl, HashMap())
            } else {
                retriever.setDataSource(videoUrl)
            }
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            if (bitmap != null) {
                memoryCache.put(videoUrl, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load thumbnail for $videoUrl: ${e.message}")
            null
        }
    }
}
