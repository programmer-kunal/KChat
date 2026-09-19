package com.example.kchat.model

data class MessageSummary(
    val lastMessage: String? = null,
    val lastTime: Long? = null,
    val lastSenderName: String? = null,
    val unreadCount: Int = 0
)

object MessagePreviewCalculator {

    /**
     * Calculates the message preview summary for a given collection of messages.
     *
     * @param messages The collection of messages in the chat/channel.
     * @param currentUserId The UID of the current authenticated user.
     * @param deletedMessageIds IDs of messages that have been deleted for the current user.
     * @param photoLabel The preview label to use when the message contains an image.
     *                   Defaults to "Photo" (DirectChat), or "📷 Photo" (Home/Groups).
     * @return [MessageSummary] containing the latest message text, timestamp, sender name, and unread count.
     */
    fun calculate(
        messages: Iterable<Message>,
        currentUserId: String,
        deletedMessageIds: Set<String> = emptySet(),
        photoLabel: String = "Photo"
    ): MessageSummary {
        var bestMsg: String? = null
        var bestTime: Long? = null
        var bestSenderName: String? = null
        var unreadCount = 0

        for (message in messages) {
            val messageId = message.id
            if (messageId.isNotEmpty() && messageId in deletedMessageIds) {
                continue
            }

            val hasText = !message.message.isNullOrBlank()
            val hasImage = !message.imageUrl.isNullOrBlank()
            val hasAudio = !message.audioUrl.isNullOrBlank()
            val hasVideo = !message.videoUrl.isNullOrBlank()
            val hasFile = !message.fileUrl.isNullOrBlank()
            if (!hasText && !hasImage && !hasAudio && !hasVideo && !hasFile) {
                continue
            }

            val isRead = message.readBy?.get(currentUserId) == true
            if (message.senderId.isNotEmpty() && message.senderId != currentUserId && !isRead) {
                unreadCount++
            }

            val createdAt = message.createdAt
            if (bestTime == null || createdAt >= bestTime) {
                bestTime = createdAt
                bestSenderName = message.senderName
                bestMsg = if (hasImage) {
                    photoLabel
                } else if (hasAudio) {
                    "🎤 Voice message"
                } else if (hasVideo) {
                    "🎥 Video message"
                } else if (hasFile) {
                    val displayFileName = message.fileName?.trim()
                    if (!displayFileName.isNullOrBlank()) {
                        val truncatedName = if (displayFileName.length > 30) {
                            displayFileName.take(27) + "..."
                        } else {
                            displayFileName
                        }
                        "📎 $truncatedName"
                    } else {
                        "📎 File"
                    }
                } else {
                    message.message
                }
            }
        }

        return MessageSummary(
            lastMessage = bestMsg,
            lastTime = bestTime,
            lastSenderName = bestSenderName,
            unreadCount = unreadCount
        )
    }
}
