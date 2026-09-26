package com.example.kchat.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.kchat.R
import com.example.kchat.model.Message
import com.example.kchat.model.User

enum class MessageReceiptStatus {
    SENT,       // K
    DELIVERED,  // KK
    READ        // KKK
}

data class MemberReceiptInfo(
    val uid: String,
    val name: String,
    val email: String,
    val profileImage: String?,
    val isDelivered: Boolean,
    val isRead: Boolean
)

data class MessageInfoData(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val messageText: String?,
    val createdAt: Long,
    val overallStatus: MessageReceiptStatus,
    val isDirectChat: Boolean,
    val isWorldChat: Boolean,
    val isSelfChat: Boolean = false,
    val directRecipient: MemberReceiptInfo? = null,
    val groupRecipients: List<MemberReceiptInfo> = emptyList()
)

object MessageReceiptHelper {

    fun getReceiptStatus(
        message: Message,
        currentUid: String?,
        isDirectChat: Boolean = false,
        isWorldChat: Boolean = false,
        isSelfChat: Boolean = false,
        groupMemberUids: Set<String> = emptySet(),
        directOtherUid: String? = null,
        memberJoinTimestamps: Map<String, Long> = emptyMap()
    ): MessageReceiptStatus {
        if (currentUid == null) return MessageReceiptStatus.SENT

        if (isSelfChat) {
            // Self-chat messages are immediately READ (KKK) for the private self conversation
            return MessageReceiptStatus.READ
        }

        if (isDirectChat) {
            val targetUid = directOtherUid ?: run {
                message.readBy?.keys?.firstOrNull { it != currentUid && it != message.senderId }
                    ?: message.deliveredBy?.keys?.firstOrNull { it != currentUid && it != message.senderId }
            }

            val isRead = if (!targetUid.isNullOrEmpty()) {
                message.readBy?.get(targetUid) == true
            } else {
                message.readBy?.any { (uid, read) -> uid != currentUid && uid != message.senderId && read } == true
            }
            if (isRead) return MessageReceiptStatus.READ

            val isDelivered = if (!targetUid.isNullOrEmpty()) {
                message.deliveredBy?.get(targetUid) == true
            } else {
                message.deliveredBy?.any { (uid, del) -> uid != currentUid && uid != message.senderId && del } == true
            }
            if (isDelivered) return MessageReceiptStatus.DELIVERED

            return MessageReceiptStatus.SENT
        } else if (isWorldChat) {
            // Scalable global broadcast:
            // Stored in RTDB => DELIVERED (KK)
            // Empty ID (local pending) => SENT (K)
            return if (message.id.isNotEmpty()) MessageReceiptStatus.DELIVERED else MessageReceiptStatus.SENT
        } else {
            // Custom Group Chat:
            // Expected recipients = all active members in groupMemberUids MINUS the sender and currentUid
            val sender = message.senderId.ifEmpty { currentUid }
            val otherMembers = groupMemberUids
                .filter { it.isNotBlank() && it != sender }
                .filter { uid ->
                    val joinTime = memberJoinTimestamps[uid]
                    joinTime == null || message.createdAt <= 0L || joinTime <= message.createdAt
                }
                .toSet()

            if (otherMembers.isEmpty()) {
                val isRead = message.readBy?.any { (uid, read) -> uid != sender && read } == true
                if (isRead) return MessageReceiptStatus.READ
                val isDelivered = message.deliveredBy?.any { (uid, del) -> uid != sender && del } == true
                if (isDelivered) return MessageReceiptStatus.DELIVERED
                return MessageReceiptStatus.SENT
            }

            // KKK requires that ALL applicable other group members have read the message
            val allRead = otherMembers.all { uid ->
                message.readBy?.get(uid) == true
            }
            if (allRead) {
                return MessageReceiptStatus.READ
            }

            // KK requires that ALL applicable other group members have received the message (delivered or read)
            val allDelivered = otherMembers.all { uid ->
                message.deliveredBy?.get(uid) == true || message.readBy?.get(uid) == true
            }
            if (allDelivered) {
                return MessageReceiptStatus.DELIVERED
            }

            // Otherwise still SENT (waiting for all members to receive)
            return MessageReceiptStatus.SENT
        }
    }

    fun buildMessageInfoData(
        message: Message,
        currentUid: String?,
        isDirectChat: Boolean,
        isWorldChat: Boolean,
        isSelfChat: Boolean = false,
        groupMemberUsers: List<User> = emptyList(),
        directRecipientUser: User? = null,
        groupMemberUids: Set<String> = emptySet(),
        memberJoinTimestamps: Map<String, Long> = emptyMap()
    ): MessageInfoData {
        val overallStatus = getReceiptStatus(
            message = message,
            currentUid = currentUid,
            isDirectChat = isDirectChat,
            isWorldChat = isWorldChat,
            isSelfChat = isSelfChat,
            groupMemberUids = groupMemberUids,
            directOtherUid = directRecipientUser?.uid,
            memberJoinTimestamps = memberJoinTimestamps
        )

        val sender = message.senderId.ifEmpty { currentUid ?: "" }

        if (isSelfChat) {
            return MessageInfoData(
                messageId = message.id,
                senderId = sender,
                senderName = message.senderName,
                messageText = message.message,
                createdAt = message.createdAt,
                overallStatus = MessageReceiptStatus.READ,
                isDirectChat = false,
                isWorldChat = false,
                isSelfChat = true
            )
        } else if (isDirectChat) {
            val rUid = directRecipientUser?.uid
                ?: message.readBy?.keys?.firstOrNull { it != currentUid && it != sender }
                ?: message.deliveredBy?.keys?.firstOrNull { it != currentUid && it != sender }
                ?: ""
            val isRead = if (rUid.isNotEmpty()) message.readBy?.get(rUid) == true else message.readBy?.any { (uid, read) -> uid != currentUid && uid != sender && read } == true
            val isDelivered = isRead || (if (rUid.isNotEmpty()) message.deliveredBy?.get(rUid) == true else message.deliveredBy?.any { (uid, del) -> uid != currentUid && uid != sender && del } == true)
            val recipientInfo = MemberReceiptInfo(
                uid = rUid,
                name = directRecipientUser?.name ?: "Recipient",
                email = directRecipientUser?.email ?: "",
                profileImage = directRecipientUser?.profileImage,
                isDelivered = isDelivered,
                isRead = isRead
            )
            return MessageInfoData(
                messageId = message.id,
                senderId = sender,
                senderName = message.senderName,
                messageText = message.message,
                createdAt = message.createdAt,
                overallStatus = overallStatus,
                isDirectChat = true,
                isWorldChat = false,
                directRecipient = recipientInfo
            )
        } else if (isWorldChat) {
            return MessageInfoData(
                messageId = message.id,
                senderId = sender,
                senderName = message.senderName,
                messageText = message.message,
                createdAt = message.createdAt,
                overallStatus = overallStatus,
                isDirectChat = false,
                isWorldChat = true
            )
        } else {
            // Group Chat
            val applicableUsers = groupMemberUsers.filter { it.uid != sender && it.uid.isNotBlank() }
            val memberInfos = applicableUsers.map { user ->
                val isRead = message.readBy?.get(user.uid) == true
                val isDelivered = isRead || message.deliveredBy?.get(user.uid) == true
                MemberReceiptInfo(
                    uid = user.uid,
                    name = user.name.ifBlank { user.email.ifBlank { "Member" } },
                    email = user.email,
                    profileImage = user.profileImage,
                    isDelivered = isDelivered,
                    isRead = isRead
                )
            }
            return MessageInfoData(
                messageId = message.id,
                senderId = sender,
                senderName = message.senderName,
                messageText = message.message,
                createdAt = message.createdAt,
                overallStatus = overallStatus,
                isDirectChat = false,
                isWorldChat = false,
                groupRecipients = memberInfos
            )
        }
    }
}

object MessageReceiptColors {
    val Sent: Color = Color.White.copy(alpha = 0.9f)
    val Delivered: Color = Color(0xFF22C55E) // Green
    val Read: Color = Color(0xFF38BDF8) // Blue

    fun getColor(status: MessageReceiptStatus): Color = when (status) {
        MessageReceiptStatus.SENT -> Sent
        MessageReceiptStatus.DELIVERED -> Delivered
        MessageReceiptStatus.READ -> Read
    }
}

@Composable
fun KChatReceipt(
    status: MessageReceiptStatus,
    modifier: Modifier = Modifier,
    tint: Color = MessageReceiptColors.getColor(status)
) {
    val count = when (status) {
        MessageReceiptStatus.SENT -> 1
        MessageReceiptStatus.DELIVERED -> 2
        MessageReceiptStatus.READ -> 3
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        repeat(count) {
            Icon(
                painter = painterResource(id = R.drawable.ic_kchat_receipt_k),
                contentDescription = when (status) {
                    MessageReceiptStatus.SENT -> "Sent"
                    MessageReceiptStatus.DELIVERED -> "Delivered"
                    MessageReceiptStatus.READ -> "Read"
                },
                tint = tint,
                modifier = Modifier.size(width = 10.dp, height = 12.dp)
            )
        }
    }
}
