package com.example.kchat.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kchat.R

private enum class AttachmentView {
    MAIN,
    IMAGES,
    VIDEOS
}

/**
 * Material 3 ModalBottomSheet displaying the KChat Attachment menu.
 * Uses the exact same dark blue theme, typography, cards, and interaction
 * design system as [com.example.kchat.feature.smartreply.KChatAiBottomSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onAudioClick: () -> Unit,
    onVideoClick: () -> Unit = {},
    onVideoCameraClick: () -> Unit = onVideoClick,
    onVideoGalleryClick: () -> Unit = onVideoClick,
    onDocumentClick: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    var currentView by remember { mutableStateOf(AttachmentView.MAIN) }

    if (currentView != AttachmentView.MAIN) {
        BackHandler(enabled = true) {
            currentView = AttachmentView.MAIN
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorResource(id = R.color.dark_blue),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray.copy(alpha = 0.5f))
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            AnimatedContent(
                targetState = currentView,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "AttachmentViewTransition"
            ) { view ->
                when (view) {
                    AttachmentView.MAIN -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Header: Attach Icon + Title + Subtitle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colorResource(id = R.color.light_blue).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.attach),
                                        contentDescription = "Attach",
                                        tint = colorResource(id = R.color.light_blue),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Attach",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Share media and files",
                                        color = colorResource(id = R.color.light_blue).copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // 2x2 Feature Grid - Row 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AttachmentOptionCard(
                                    title = "Images",
                                    subtitle = "Send photos and images",
                                    icon = Icons.Default.Image,
                                    statusBadge = "Ready",
                                    isActive = true,
                                    onClick = { currentView = AttachmentView.IMAGES },
                                    modifier = Modifier.weight(1f)
                                )
                                AttachmentOptionCard(
                                    title = "Audio",
                                    subtitle = "Send audio and voice messages",
                                    icon = Icons.Default.Mic,
                                    statusBadge = "Ready",
                                    isActive = true,
                                    onClick = onAudioClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2x2 Feature Grid - Row 2
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AttachmentOptionCard(
                                    title = "Video",
                                    subtitle = "Send videos",
                                    icon = Icons.Default.Videocam,
                                    statusBadge = "Ready",
                                    isActive = true,
                                    onClick = { currentView = AttachmentView.VIDEOS },
                                    modifier = Modifier.weight(1f)
                                )
                                AttachmentOptionCard(
                                    title = "Documents",
                                    subtitle = "Send files and documents",
                                    icon = Icons.Default.Description,
                                    statusBadge = "Ready",
                                    isActive = true,
                                    onClick = onDocumentClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    AttachmentView.IMAGES -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Sub-Header with Back Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { currentView = AttachmentView.MAIN },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colorResource(id = R.color.light_blue),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colorResource(id = R.color.light_blue).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Images",
                                        tint = colorResource(id = R.color.light_blue),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Send Images",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Choose camera or gallery",
                                        color = colorResource(id = R.color.light_blue).copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Camera & Gallery options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AttachmentOptionCard(
                                    title = "Camera",
                                    subtitle = "Take a new photo",
                                    icon = Icons.Default.CameraAlt,
                                    statusBadge = "Capture",
                                    isActive = true,
                                    onClick = onCameraClick,
                                    modifier = Modifier.weight(1f)
                                )
                                AttachmentOptionCard(
                                    title = "Gallery",
                                    subtitle = "Select from device",
                                    icon = Icons.Default.PhotoLibrary,
                                    statusBadge = "Browse",
                                    isActive = true,
                                    onClick = onGalleryClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    AttachmentView.VIDEOS -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Sub-Header with Back Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { currentView = AttachmentView.MAIN },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colorResource(id = R.color.light_blue),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colorResource(id = R.color.light_blue).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Video",
                                        tint = colorResource(id = R.color.light_blue),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Send Videos",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Record video or choose from gallery",
                                        color = colorResource(id = R.color.light_blue).copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Record Video & Gallery options
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AttachmentOptionCard(
                                    title = "Record Video",
                                    subtitle = "Record a new video",
                                    icon = Icons.Default.Videocam,
                                    statusBadge = "Record",
                                    isActive = true,
                                    onClick = onVideoCameraClick,
                                    modifier = Modifier.weight(1f)
                                )
                                AttachmentOptionCard(
                                    title = "Gallery",
                                    subtitle = "Select from device",
                                    icon = Icons.Default.PhotoLibrary,
                                    statusBadge = "Browse",
                                    isActive = true,
                                    onClick = onVideoGalleryClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable option card for attachment categories matching the exact visual
 * hierarchy, border radius, padding, and elevation of KChat AI capability cards.
 */
@Composable
private fun AttachmentOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusBadge: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) colorResource(id = R.color.light_blue).copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.05f)
            )
            .border(
                width = 1.dp,
                color = if (isActive) colorResource(id = R.color.light_blue).copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Row: Icon + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) colorResource(id = R.color.light_blue).copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isActive) colorResource(id = R.color.light_blue) else Color.LightGray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Status Badge ("Ready", "Soon", "Capture", "Browse")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isActive) colorResource(id = R.color.light_blue).copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.1f)
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusBadge,
                        color = if (isActive) colorResource(id = R.color.light_blue) else Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = subtitle,
                color = Color.LightGray.copy(alpha = 0.85f),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2
            )
        }
    }
}
