package com.example.kchat.feature.chat.attachment

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.kchat.R
import com.example.kchat.feature.chat.file.FileUtils
import com.example.kchat.feature.chat.video.VideoUtils

@Composable
fun AttachmentPreviewDialog(
    attachments: List<AttachmentItem>,
    onRemoveItem: (AttachmentItem) -> Unit,
    onAddMore: () -> Unit,
    onDismiss: () -> Unit,
    onSend: (List<AttachmentItem>) -> Unit
) {
    if (attachments.isEmpty()) {
        onDismiss()
        return
    }

    val allImages = attachments.all { it.type == AttachmentType.IMAGE }
    val allVideos = attachments.all { it.type == AttachmentType.VIDEO }
    val allDocuments = attachments.all { it.type == AttachmentType.DOCUMENT }

    val titleText = when {
        allImages -> if (attachments.size == 1) "Photo Preview" else "${attachments.size} Photos Selected"
        allVideos -> if (attachments.size == 1) "Video Preview" else "${attachments.size} Videos Selected"
        allDocuments -> if (attachments.size == 1) "Document Preview" else "${attachments.size} Documents Selected"
        else -> "${attachments.size} Attachments Selected"
    }

    val headerIcon = when {
        allImages -> Icons.Default.PhotoLibrary
        allVideos -> Icons.Default.Videocam
        allDocuments -> Icons.Default.Description
        else -> Icons.Default.AttachFile
    }

    var selectedImageId by remember(attachments) {
        mutableStateOf(attachments.firstOrNull()?.id ?: "")
    }

    val activeImageItem = attachments.find { it.id == selectedImageId } ?: attachments.firstOrNull()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val isDark = MaterialTheme.colorScheme.background == Color(0xFF162542)
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = headerIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = titleText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Add More button in header
                        IconButton(
                            onClick = onAddMore,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add More",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Area based on Type
                when {
                    allImages -> {
                        // Large Preview of Active Image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (activeImageItem != null) {
                                AsyncImage(
                                    model = activeImageItem.uri ?: activeImageItem.file,
                                    contentDescription = "Active Image Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )

                                // Remove button on top-right of active image
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .clickable { onRemoveItem(activeImageItem) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Horizontal thumbnail carousel
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(attachments, key = { it.id }) { item ->
                                val isSelected = item.id == selectedImageId
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else (if (isDark) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { selectedImageId = item.id }
                                ) {
                                    AsyncImage(
                                        model = item.uri ?: item.file,
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            // Add More Tile in Carousel
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onAddMore() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add More",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Add",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    allVideos -> {
                        if (attachments.size == 1) {
                            val video = attachments.first()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (video.thumbnail != null) {
                                    Image(
                                        bitmap = video.thumbnail.asImageBitmap(),
                                        contentDescription = "Video Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }

                                // Remove button
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .clickable { onRemoveItem(video) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Metadata banner at bottom
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Duration: ${VideoUtils.formatDuration(video.durationMs)}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = VideoUtils.formatFileSize(video.fileSizeBytes),
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            // Multiple videos list
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(attachments, key = { it.id }) { video ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(130.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (video.thumbnail != null) {
                                            Image(
                                                bitmap = video.thumbnail.asImageBitmap(),
                                                contentDescription = "Video Thumbnail",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }

                                        // Remove button
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f))
                                                .clickable { onRemoveItem(video) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        // Bottom info banner
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(Color.Black.copy(alpha = 0.7f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = VideoUtils.formatDuration(video.durationMs),
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = VideoUtils.formatFileSize(video.fileSizeBytes),
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    allDocuments -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(attachments, key = { it.id }) { doc ->
                                val extLabel = FileUtils.getFileExtensionLabel(doc.fileName, doc.mimeType)
                                val formattedSize = FileUtils.formatFileSize(doc.fileSizeBytes)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isDark) Color(0xFF1E2D4A) else MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = doc.fileName,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "$extLabel • $formattedSize",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveItem(doc) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Mixed attachments
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(attachments, key = { it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isDark) Color(0xFF1E2D4A) else MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when (item.type) {
                                        AttachmentType.IMAGE -> Icons.Default.PhotoLibrary
                                        AttachmentType.VIDEO -> Icons.Default.Videocam
                                        AttachmentType.DOCUMENT -> Icons.Default.Description
                                    }
                                    val title = when (item.type) {
                                        AttachmentType.IMAGE -> item.fileName.ifBlank { "Photo" }
                                        AttachmentType.VIDEO -> "Video (${VideoUtils.formatDuration(item.durationMs)})"
                                        AttachmentType.DOCUMENT -> item.fileName.ifBlank { "Document" }
                                    }
                                    val subtitle = when (item.type) {
                                        AttachmentType.IMAGE -> "Photo"
                                        AttachmentType.VIDEO -> VideoUtils.formatFileSize(item.fileSizeBytes)
                                        AttachmentType.DOCUMENT -> "${FileUtils.getFileExtensionLabel(item.fileName, item.mimeType)} • ${FileUtils.formatFileSize(item.fileSizeBytes)}"
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = subtitle,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    IconButton(
                                        onClick = { onRemoveItem(item) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons: Cancel and Send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = { onSend(attachments) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.send),
                            contentDescription = "Send",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (attachments.size > 1) "Send (${attachments.size})" else "Send",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
