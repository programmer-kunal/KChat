package com.example.kchat.feature.chat.file

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kchat.R
import com.example.kchat.VaultMediaManager
import kotlinx.coroutines.launch

@Composable
fun FileMessageBubble(
    fileUrl: String,
    fileName: String,
    fileMimeType: String?,
    fileSizeBytes: Long,
    isCurrentUser: Boolean,
    isInSelectionMode: Boolean,
    onBubbleClick: () -> Unit = {},
    message: com.example.kchat.model.Message? = null,
    channelId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val messageKey = message?.id?.ifBlank { fileUrl } ?: fileUrl
    var isDownloaded by remember(messageKey, currentUid) {
        mutableStateOf(isCurrentUser || com.example.kchat.data.MediaDownloadTracker.isDownloaded(context, currentUid, messageKey, "doc"))
    }
    var isLoading by remember { mutableStateOf(false) }

    val extLabel = FileUtils.getFileExtensionLabel(fileName, fileMimeType)
    val formattedSize = FileUtils.formatFileSize(fileSizeBytes)

    val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)

    val iconBgColor = if (isCurrentUser) {
        Color.White.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }

    val iconTint = if (isCurrentUser) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }

    val cardBg = if (isCurrentUser) {
        Color.Black.copy(alpha = 0.15f)
    } else {
        if (isDark) Color.Black.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }

    val openOrDownloadAction = {
        if (isInSelectionMode) {
            onBubbleClick()
        } else if (!isLoading) {
            coroutineScope.launch {
                isLoading = true
                try {
                    val targetFile: java.io.File? = if (message?.mediaEncrypted == true && channelId != null) {
                        com.example.kchat.crypto.EncryptedMediaManager.getOrDecryptDocumentFile(context, channelId, message)
                    } else {
                        // For unencrypted, check cache or download
                        val resolved = VaultMediaManager.resolveMediaUrl(fileUrl) ?: fileUrl
                        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                        val localFile = java.io.File(context.noBackupFilesDir, "temp_dec_doc_${messageKey}_$safeName")
                        if (localFile.exists() && localFile.length() > 0L) {
                            localFile
                        } else {
                            try {
                                val conn = java.net.URL(resolved).openConnection()
                                conn.connectTimeout = 15000
                                conn.readTimeout = 30000
                                conn.getInputStream().use { input ->
                                    java.io.FileOutputStream(localFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                localFile
                            } catch (e: Exception) {
                                localFile.delete()
                                null
                            }
                        }
                    }

                    if (targetFile != null && targetFile.exists() && targetFile.length() > 0L) {
                        com.example.kchat.data.MediaDownloadTracker.setDownloaded(context, currentUid, messageKey, "doc", true)
                        isDownloaded = true

                        val effectiveMime = if (!fileMimeType.isNullOrBlank() && fileMimeType != "application/octet-stream") {
                            fileMimeType
                        } else {
                            val ext = (fileName.ifBlank { targetFile.name }).substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
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
                                "jpg", "jpeg" -> "image/jpeg"
                                "png" -> "image/png"
                                else -> android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                            }
                        }

                        val contentUri = try {
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", targetFile)
                        } catch (e: Exception) {
                            val cacheDocDir = java.io.File(context.cacheDir, "documents").apply { if (!exists()) mkdirs() }
                            val cacheFile = java.io.File(cacheDocDir, targetFile.name)
                            targetFile.copyTo(cacheFile, overwrite = true)
                            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", cacheFile)
                        }

                        val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, effectiveMime)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        // Explicitly grant read URI permission to all packages matching this intent
                        val pm = context.packageManager
                        val resolveInfoList = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            pm.queryIntentActivities(
                                android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(contentUri, effectiveMime) },
                                android.content.pm.PackageManager.ResolveInfoFlags.of(android.content.pm.PackageManager.MATCH_DEFAULT_ONLY.toLong())
                            )
                        } else {
                            pm.queryIntentActivities(
                                android.content.Intent(android.content.Intent.ACTION_VIEW).apply { setDataAndType(contentUri, effectiveMime) },
                                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
                            )
                        }

                        for (resolveInfo in resolveInfoList) {
                            val pkg = resolveInfo.activityInfo.packageName
                            try {
                                context.grantUriPermission(pkg, contentUri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (_: Exception) {}
                        }

                        try {
                            context.startActivity(viewIntent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(contentUri, "*/*")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val chooser = android.content.Intent.createChooser(fallbackIntent, "Open document with").apply {
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(chooser)
                            } catch (_: android.content.ActivityNotFoundException) {
                                android.widget.Toast.makeText(context, "No app found to open this document", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Download failed, tap to retry", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FileMessageBubble", "Failed to open document: ${e.message}")
                    android.widget.Toast.makeText(context, "Failed to open document: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                } finally {
                    isLoading = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .clickable { openOrDownloadAction() }
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File Icon Container
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Document",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Filename & Meta
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = fileName.ifBlank { "Document" },
                        color = if (isCurrentUser) Color.White else (if (isDark) Color.White else MaterialTheme.colorScheme.onSurface),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$extLabel • $formattedSize",
                        color = if (isCurrentUser) Color.White.copy(alpha = 0.75f) else (if (isDark) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant),
                        fontSize = 10.5.sp
                    )
                }
            }

            // Receiver-only download option: shown only when not yet downloaded
            if (!isCurrentUser && !isDownloaded) {
                Spacer(modifier = Modifier.width(6.dp))
                if (isLoading) {
                    CircularProgressIndicator(
                        color = iconTint,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(iconBgColor)
                            .clickable { openOrDownloadAction() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download file",
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else if (isLoading) {
                Spacer(modifier = Modifier.width(6.dp))
                CircularProgressIndicator(
                    color = iconTint,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
