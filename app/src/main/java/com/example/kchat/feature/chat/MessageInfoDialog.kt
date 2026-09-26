package com.example.kchat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.kchat.R
import com.example.kchat.model.Message
import com.example.kchat.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@Composable
fun MessageInfoDialog(
    message: Message,
    channelID: String,
    channelName: String,
    isDirectChat: Boolean,
    isWorldChat: Boolean,
    isSelfChat: Boolean = false,
    groupMemberUids: Set<String>,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val currentUid = Firebase.auth.currentUser?.uid ?: ""
    val effectiveIsSelfChat = isSelfChat || (channelID.startsWith("self_chat_") && (!message.isSavedMessage || message.originalChatId == null || message.originalChatId.startsWith("self_chat_")))
    var currentUserProfile by remember { mutableStateOf<User?>(null) }
    var groupMembers by remember { mutableStateOf<List<User>>(emptyList()) }
    var directRecipientUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(message.id, channelID) {
        isLoading = true
        if (effectiveIsSelfChat) {
            viewModel.getUserProfile(currentUid) { user ->
                currentUserProfile = user
                isLoading = false
            }
        } else if (isDirectChat) {
            val parts = channelID.split("_")
            val otherUid = parts.firstOrNull { it != currentUid }
            if (!otherUid.isNullOrEmpty()) {
                viewModel.getUserProfile(otherUid) { user ->
                    directRecipientUser = user
                    isLoading = false
                }
            } else {
                isLoading = false
            }
        } else if (!isWorldChat) {
            viewModel.getGroupMembers(channelID) { members ->
                groupMembers = members
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    val infoData = remember(message, groupMembers, directRecipientUser, groupMemberUids, effectiveIsSelfChat) {
        MessageReceiptHelper.buildMessageInfoData(
            message = message,
            currentUid = currentUid,
            isDirectChat = if (effectiveIsSelfChat) false else isDirectChat,
            isWorldChat = if (effectiveIsSelfChat) false else isWorldChat,
            isSelfChat = effectiveIsSelfChat,
            groupMemberUsers = groupMembers,
            directRecipientUser = directRecipientUser,
            groupMemberUids = groupMemberUids
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Message Info",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                    }
                } else {
                    // Unified scrollable container for responsiveness across all screen heights
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. TOP MESSAGE PREVIEW BUBBLE
                        item {
                            MessagePreviewBubble(
                                message = message,
                                overallStatus = infoData.overallStatus
                            )
                        }

                        // SECTIONS BASED ON CHAT TYPE
                        if (effectiveIsSelfChat) {
                            // SELF-CHAT: Immediately Read (KKK) - No group/external recipients
                            // Delivered Section
                            item {
                                SectionHeader(
                                    title = "Delivered",
                                    status = MessageReceiptStatus.READ,
                                    tint = MessageReceiptColors.Delivered
                                )
                            }
                            item {
                                MemberReceiptItem(
                                    name = "You",
                                    profileImage = currentUserProfile?.profileImage ?: Firebase.auth.currentUser?.photoUrl?.toString(),
                                    status = MessageReceiptStatus.READ,
                                    statusLabel = "Delivered",
                                    statusColor = MessageReceiptColors.Delivered
                                )
                            }

                            // Seen Section
                            item {
                                SectionHeader(
                                    title = "Seen",
                                    status = MessageReceiptStatus.READ,
                                    tint = MessageReceiptColors.Read
                                )
                            }
                            item {
                                MemberReceiptItem(
                                    name = "You",
                                    profileImage = currentUserProfile?.profileImage ?: Firebase.auth.currentUser?.photoUrl?.toString(),
                                    status = MessageReceiptStatus.READ,
                                    statusLabel = "Read",
                                    statusColor = MessageReceiptColors.Read
                                )
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "Self message • Delivered and read immediately",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else if (isDirectChat) {
                            val recipient = infoData.directRecipient

                            // Delivered Section
                            item {
                                SectionHeader(
                                    title = "Delivered",
                                    status = MessageReceiptStatus.DELIVERED,
                                    tint = MessageReceiptColors.Delivered
                                )
                            }
                            item {
                                val isDelivered = recipient?.isDelivered == true
                                MemberReceiptItem(
                                    name = recipient?.name ?: "Recipient",
                                    profileImage = recipient?.profileImage,
                                    status = if (isDelivered) MessageReceiptStatus.DELIVERED else MessageReceiptStatus.SENT,
                                    statusLabel = if (isDelivered) "Delivered" else "Not delivered",
                                    statusColor = if (isDelivered) MessageReceiptColors.Delivered else Color.Gray
                                )
                            }

                            // Seen Section
                            item {
                                SectionHeader(
                                    title = "Seen",
                                    status = MessageReceiptStatus.READ,
                                    tint = MessageReceiptColors.Read
                                )
                            }
                            item {
                                val isRead = recipient?.isRead == true
                                val isDelivered = recipient?.isDelivered == true
                                val seenStatus = if (isRead) MessageReceiptStatus.READ
                                else if (isDelivered) MessageReceiptStatus.DELIVERED
                                else MessageReceiptStatus.SENT

                                MemberReceiptItem(
                                    name = recipient?.name ?: "Recipient",
                                    profileImage = recipient?.profileImage,
                                    status = seenStatus,
                                    statusLabel = if (isRead) "Read" else "Not read",
                                    statusColor = if (isRead) MessageReceiptColors.Read else Color.Gray
                                )
                            }
                        } else if (isWorldChat) {
                            // WORLD CHAT
                            item {
                                SectionHeader(
                                    title = "Delivered",
                                    status = MessageReceiptStatus.DELIVERED,
                                    tint = MessageReceiptColors.Delivered
                                )
                            }
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "World Chat Room",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "Delivered",
                                            color = MessageReceiptColors.Delivered,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        KChatReceipt(
                                            status = MessageReceiptStatus.DELIVERED,
                                            tint = MessageReceiptColors.Delivered
                                        )
                                    }
                                }
                            }
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "World Chat is a public room broadcast to all KChat users. Individual read receipts are not tracked for public global chats.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else {
                            // CUSTOM GROUP CHAT
                            val recipients = infoData.groupRecipients
                            val deliveredCount = recipients.count { it.isDelivered }
                            val readCount = recipients.count { it.isRead }

                            // DELIVERED SECTION
                            item {
                                SectionHeader(
                                    title = "Delivered",
                                    subtitle = "($deliveredCount/${recipients.size})",
                                    status = MessageReceiptStatus.DELIVERED,
                                    tint = MessageReceiptColors.Delivered
                                )
                            }

                            if (recipients.isEmpty()) {
                                item {
                                    Text(
                                        text = "No other members in this group.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(recipients, key = { "del_${it.uid}" }) { member ->
                                    MemberReceiptItem(
                                        name = member.name,
                                        profileImage = member.profileImage,
                                        status = if (member.isDelivered) MessageReceiptStatus.DELIVERED else MessageReceiptStatus.SENT,
                                        statusLabel = if (member.isDelivered) "Delivered" else "Not delivered",
                                        statusColor = if (member.isDelivered) MessageReceiptColors.Delivered else Color.Gray
                                    )
                                }
                            }

                            // SEEN SECTION
                            item {
                                SectionHeader(
                                    title = "Seen",
                                    subtitle = "($readCount/${recipients.size})",
                                    status = MessageReceiptStatus.READ,
                                    tint = MessageReceiptColors.Read
                                )
                            }

                            if (recipients.isEmpty()) {
                                item {
                                    Text(
                                        text = "No other members in this group.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                items(recipients, key = { "seen_${it.uid}" }) { member ->
                                    val seenStatus = if (member.isRead) MessageReceiptStatus.READ
                                    else if (member.isDelivered) MessageReceiptStatus.DELIVERED
                                    else MessageReceiptStatus.SENT

                                    MemberReceiptItem(
                                        name = member.name,
                                        profileImage = member.profileImage,
                                        status = seenStatus,
                                        statusLabel = if (member.isRead) "Read" else "Not read",
                                        statusColor = if (member.isRead) MessageReceiptColors.Read else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top message preview styled like a KChat chat bubble with timestamp and overall receipt.
 */
@Composable
private fun MessagePreviewBubble(
    message: Message,
    overallStatus: MessageReceiptStatus
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val previewText = when {
                !message.message.isNullOrBlank() -> message.message.trim()
                !message.imageUrl.isNullOrBlank() -> "📷 Photo"
                !message.audioUrl.isNullOrBlank() -> "🎤 Voice message"
                !message.videoUrl.isNullOrBlank() -> "🎥 Video message"
                !message.fileUrl.isNullOrBlank() -> "📎 ${message.fileName ?: "File"}"
                else -> "[Message]"
            }
            Text(
                text = previewText,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val formattedTime = if (message.createdAt > 0L) {
                    DateTimeUtils.formatMessageTime(message.createdAt)
                } else ""
                if (formattedTime.isNotEmpty()) {
                    Text(
                        text = formattedTime,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                KChatReceipt(
                    status = overallStatus,
                    tint = MessageReceiptColors.getColor(overallStatus)
                )
            }
        }
    }
}

/**
 * Section header with label, optional count, and K / KK / KKK glyph.
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    status: MessageReceiptStatus,
    tint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
        KChatReceipt(
            status = status,
            tint = tint
        )
    }
}

/**
 * Row displaying a recipient member with their avatar, name, and KChat receipt status.
 * Replaces WhatsApp checkmarks with K / KK / KKK glyphs.
 */
@Composable
private fun MemberReceiptItem(
    name: String,
    profileImage: String?,
    status: MessageReceiptStatus,
    statusLabel: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(modifier = Modifier.size(34.dp)) {
            if (!profileImage.isNullOrEmpty()) {
                AsyncImage(
                    model = profileImage,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.firstOrNull()?.uppercase() ?: "U",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Name
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Receipt status: Text label + K / KK / KKK glyph (NO checkmarks!)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = statusLabel,
                color = if (statusColor != Color.Gray) statusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (statusColor != Color.Gray) FontWeight.SemiBold else FontWeight.Normal
            )
            KChatReceipt(
                status = status,
                tint = if (statusColor != Color.Gray) statusColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
