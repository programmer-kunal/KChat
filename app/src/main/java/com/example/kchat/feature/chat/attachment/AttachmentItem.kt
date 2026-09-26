package com.example.kchat.feature.chat.attachment

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.util.UUID

enum class AttachmentType {
    IMAGE,
    VIDEO,
    DOCUMENT
}

data class AttachmentItem(
    val id: String = UUID.randomUUID().toString(),
    val type: AttachmentType,
    val uri: Uri? = null,
    val file: File? = null,
    val fileName: String = "",
    val mimeType: String = "",
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val thumbnail: Bitmap? = null,
    val isFromCamera: Boolean = false
)
