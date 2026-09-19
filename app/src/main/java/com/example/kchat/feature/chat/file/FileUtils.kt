package com.example.kchat.feature.chat.file

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.Locale

data class FileMetadata(
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String
)

data class PendingFile(
    val file: File,
    val fileName: String,
    val mimeType: String,
    val fileSizeBytes: Long
)

object FileUtils {

    const val TAG = "FileUtils"
    const val MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024L // 25 MB

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

    fun getFileExtensionLabel(fileName: String?, mimeType: String?): String {
        val ext = fileName?.substringAfterLast('.', "")?.uppercase(Locale.ROOT)?.trim() ?: ""
        if (ext.isNotEmpty() && ext.length <= 5) {
            return ext
        }
        return when {
            mimeType?.contains("pdf", ignoreCase = true) == true -> "PDF"
            mimeType?.contains("word", ignoreCase = true) == true || mimeType?.contains("doc", ignoreCase = true) == true -> "DOC"
            mimeType?.contains("sheet", ignoreCase = true) == true || mimeType?.contains("excel", ignoreCase = true) == true -> "XLS"
            mimeType?.contains("presentation", ignoreCase = true) == true || mimeType?.contains("powerpoint", ignoreCase = true) == true -> "PPT"
            mimeType?.contains("zip", ignoreCase = true) == true || mimeType?.contains("compressed", ignoreCase = true) == true -> "ZIP"
            mimeType?.contains("text", ignoreCase = true) == true -> "TXT"
            else -> "FILE"
        }
    }

    fun extractFileMetadata(context: Context, uri: Uri): FileMetadata {
        var name = "Document"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: "Document"
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying file metadata", e)
        }

        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return FileMetadata(
            fileName = name,
            fileSizeBytes = size,
            mimeType = mimeType
        )
    }

    suspend fun copyUriToTempFile(context: Context, uri: Uri, originalName: String): File? = withContext(Dispatchers.IO) {
        try {
            val docDir = File(context.cacheDir, "documents").apply {
                if (!exists()) mkdirs()
            }
            val safeName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val tempFile = File(docDir, "doc_${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (tempFile.exists() && tempFile.length() > 0L) {
                if (tempFile.length() > MAX_FILE_SIZE_BYTES) {
                    tempFile.delete()
                    null
                } else {
                    tempFile
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy uri to temp file", e)
            null
        }
    }

    suspend fun openFile(
        context: Context,
        fileUrl: String,
        fileName: String,
        mimeType: String?,
        onLoadingChange: (Boolean) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) { onLoadingChange(true) }
        try {
            val downloadDir = File(context.cacheDir, "downloaded_docs").apply {
                if (!exists()) mkdirs()
            }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "document" }
            val localFile = File(downloadDir, safeName)

            if (!localFile.exists() || localFile.length() <= 0L) {
                URL(fileUrl).openStream().use { input ->
                    localFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            withContext(Dispatchers.Main) { onLoadingChange(false) }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                localFile
            )

            val effectiveMime = if (!mimeType.isNullOrBlank() && mimeType != "application/octet-stream") {
                mimeType
            } else {
                val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                when (ext) {
                    "pdf" -> "application/pdf"
                    "doc" -> "application/msword"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "xls" -> "application/vnd.ms-excel"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "ppt" -> "application/vnd.ms-powerpoint"
                    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    "txt" -> "text/plain"
                    "zip" -> "application/zip"
                    "csv" -> "text/csv"
                    else -> "application/octet-stream"
                }
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, effectiveMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(viewIntent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading or opening file", e)
            withContext(Dispatchers.Main) {
                onLoadingChange(false)
                Toast.makeText(context, "Failed to download file", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
