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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .clickable {
                if (isInSelectionMode) {
                    onBubbleClick()
                } else if (!isLoading) {
                    coroutineScope.launch {
                        FileUtils.openFile(
                            context = context,
                            fileUrl = fileUrl,
                            fileName = fileName,
                            mimeType = fileMimeType,
                            onLoadingChange = { isLoading = it }
                        )
                    }
                }
            }
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

            Spacer(modifier = Modifier.width(6.dp))

            // Action: Loading spinner or Download/Open icon
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
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Open file",
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
