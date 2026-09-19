package com.example.kchat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class SupabaseStorageUtils(val context: Context) {

    val supabase = createSupabaseClient(
        BuildConfig.SUPABASE_URL,
        BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Storage)
    }

    suspend fun uploadAudio(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${UUID.randomUUID()}.m4a"
            val bytes = file.readBytes()
            supabase.storage.from(BUCKET_NAME).upload(fileName, bytes)
            val publicUrl = supabase.storage.from(BUCKET_NAME).publicUrl(fileName)
            return@withContext publicUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun uploadVideo(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val fileName = "${UUID.randomUUID()}.mp4"
            val bytes = file.readBytes()
            supabase.storage.from(BUCKET_NAME).upload(fileName, bytes)
            val publicUrl = supabase.storage.from(BUCKET_NAME).publicUrl(fileName)
            return@withContext publicUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun uploadFile(file: File, originalFileName: String, mimeType: String): String? = withContext(Dispatchers.IO) {
        try {
            val ext = originalFileName.substringAfterLast('.', "").let { if (it.isNotEmpty()) ".$it" else "" }
            val storageFileName = "${UUID.randomUUID()}$ext"
            val bytes = file.readBytes()
            supabase.storage.from(BUCKET_NAME).upload(storageFileName, bytes)
            val publicUrl = supabase.storage.from(BUCKET_NAME).publicUrl(storageFileName)
            return@withContext publicUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun uploadImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val processedImage = prepareImageBytes(uri) ?: return@withContext null
            val fileName = "${UUID.randomUUID()}.${processedImage.extension}"
            supabase.storage.from(BUCKET_NAME).upload(fileName, processedImage.bytes)
            val publicUrl = supabase.storage.from(BUCKET_NAME).publicUrl(fileName)
            return@withContext publicUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private class ProcessedImage(
        val bytes: ByteArray,
        val extension: String
    )

    private fun prepareImageBytes(uri: Uri): ProcessedImage? {
        try {
            // 1. Read image dimensions and mime type without loading pixels into memory
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            val mimeType = boundsOptions.outMimeType

            // If decoding bounds failed (e.g. non-standard format), fallback to uploading raw bytes directly
            if (origWidth <= 0 || origHeight <= 0) {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                return ProcessedImage(rawBytes, getExtension(uri, mimeType))
            }

            // 2. Check raw file size: If already <= 200 KB and dimensions <= 1280, skip recompression
            val rawFileSize = try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
            } catch (e: Exception) {
                -1L
            }

            val maxDimension = 1280
            if (rawFileSize in 1..(200 * 1024) && origWidth <= maxDimension && origHeight <= maxDimension) {
                val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
                return ProcessedImage(rawBytes, getExtension(uri, mimeType))
            }

            // 3. Calculate safe inSampleSize to avoid high memory spikes during decode
            var inSampleSize = 1
            val longestSide = maxOf(origWidth, origHeight)
            while (longestSide / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // 4. Handle EXIF rotation so camera pictures aren't rotated sideways
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }

            // 5. Downscale proportionally if longest side is still larger than maxDimension (1280)
            val currentWidth = if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) sampledBitmap.height else sampledBitmap.width
            val currentHeight = if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) sampledBitmap.width else sampledBitmap.height
            val currentLongest = maxOf(currentWidth, currentHeight)

            if (currentLongest > maxDimension) {
                val scale = maxDimension.toFloat() / currentLongest.toFloat()
                matrix.postScale(scale, scale)
            }

            val finalBitmap = if (!matrix.isIdentity) {
                val transformed = Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true)
                if (transformed != sampledBitmap) {
                    sampledBitmap.recycle()
                }
                transformed
            } else {
                sampledBitmap
            }

            // 6. Compress: Preserve transparency for images with alpha; use JPEG 80% for all others
            val outputStream = ByteArrayOutputStream()
            val (format, ext) = if (finalBitmap.hasAlpha()) {
                Pair(Bitmap.CompressFormat.PNG, "png")
            } else {
                Pair(Bitmap.CompressFormat.JPEG, "jpg")
            }

            finalBitmap.compress(format, 80, outputStream)
            finalBitmap.recycle()

            val compressedBytes = outputStream.toByteArray()
            outputStream.close()

            return ProcessedImage(compressedBytes, ext)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to raw stream if processing encounters an unexpected error
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            return ProcessedImage(rawBytes, getExtension(uri))
        }
    }

    private fun getExtension(uri: Uri, mimeType: String? = null): String {
        if (!mimeType.isNullOrEmpty()) {
            when (mimeType.lowercase()) {
                "image/png" -> return "png"
                "image/jpeg", "image/jpg" -> return "jpg"
                "image/webp" -> return "webp"
                "image/gif" -> return "gif"
            }
        }
        val mimeFromResolver = context.contentResolver.getType(uri)
        if (!mimeFromResolver.isNullOrEmpty()) {
            when (mimeFromResolver.lowercase()) {
                "image/png" -> return "png"
                "image/jpeg", "image/jpg" -> return "jpg"
                "image/webp" -> return "webp"
                "image/gif" -> return "gif"
            }
        }
        val pathExt = uri.path?.substringAfterLast(".")
        if (!pathExt.isNullOrEmpty() && pathExt.length in 3..4 && !pathExt.contains("/")) {
            return pathExt.lowercase()
        }
        return "jpg"
    }

    companion object {
        const val BUCKET_NAME = "chatter_images"
    }
}