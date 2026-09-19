package com.example.kchat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagePreviewCalculatorTest {

    private val currentUserId = "user_me"
    private val otherUserId = "user_other"
    private val thirdUserId = "user_third"

    // 1. Normal text message
    @Test
    fun calculate_normalTextMessage_returnsTextAndTimestamp() {
        val message = Message(
            id = "m1",
            senderId = otherUserId,
            message = "Hello world",
            createdAt = 1000L,
            senderName = "Alice",
            readBy = mapOf(currentUserId to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(message),
            currentUserId = currentUserId,
            photoLabel = "Photo"
        )

        assertEquals("Hello world", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
        assertEquals("Alice", summary.lastSenderName)
        assertEquals(0, summary.unreadCount)
    }

    // 2. Photo/image message
    @Test
    fun calculate_photoMessage_usesProvidedPhotoLabel() {
        val message = Message(
            id = "m1",
            senderId = otherUserId,
            message = null,
            imageUrl = "https://example.com/photo.jpg",
            createdAt = 1000L,
            senderName = "Bob",
            readBy = mapOf(currentUserId to true)
        )

        val directChatSummary = MessagePreviewCalculator.calculate(
            messages = listOf(message),
            currentUserId = currentUserId,
            photoLabel = "Photo"
        )
        assertEquals("Photo", directChatSummary.lastMessage)

        val groupChatSummary = MessagePreviewCalculator.calculate(
            messages = listOf(message),
            currentUserId = currentUserId,
            photoLabel = "📷 Photo"
        )
        assertEquals("📷 Photo", groupChatSummary.lastMessage)
    }

    // 3. Unread message
    @Test
    fun calculate_unreadMessage_incrementsUnreadCount() {
        val message = Message(
            id = "m1",
            senderId = otherUserId,
            message = "Unread text",
            createdAt = 1000L,
            readBy = emptyMap()
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(message),
            currentUserId = currentUserId
        )

        assertEquals(1, summary.unreadCount)
    }

    // 4. Read message
    @Test
    fun calculate_readMessage_doesNotIncrementUnreadCount() {
        val message = Message(
            id = "m1",
            senderId = otherUserId,
            message = "Read text",
            createdAt = 1000L,
            readBy = mapOf(currentUserId to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(message),
            currentUserId = currentUserId
        )

        assertEquals(0, summary.unreadCount)
    }

    // 5. Sender's own message
    @Test
    fun calculate_senderOwnMessage_neverCountsAsUnread() {
        val message = Message(
            id = "m1",
            senderId = currentUserId,
            message = "My own message",
            createdAt = 1000L,
            readBy = emptyMap()
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(message),
            currentUserId = currentUserId
        )

        assertEquals(0, summary.unreadCount)
        assertEquals("My own message", summary.lastMessage)
    }

    // 6. Deleted message
    @Test
    fun calculate_deletedMessage_excludedFromPreviewAndUnread() {
        val msg1 = Message(
            id = "m1",
            senderId = otherUserId,
            message = "First message",
            createdAt = 1000L,
            readBy = emptyMap()
        )
        val msg2 = Message(
            id = "m2",
            senderId = otherUserId,
            message = "Deleted message",
            createdAt = 2000L,
            readBy = emptyMap()
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(msg1, msg2),
            currentUserId = currentUserId,
            deletedMessageIds = setOf("m2")
        )

        assertEquals("First message", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
        assertEquals(1, summary.unreadCount)
    }

    // 7. Multiple messages
    @Test
    fun calculate_multipleMessages_selectsLatestMessage() {
        val msg1 = Message(id = "m1", senderId = otherUserId, message = "First", createdAt = 1000L)
        val msg2 = Message(id = "m2", senderId = otherUserId, message = "Second", createdAt = 2000L)
        val msg3 = Message(id = "m3", senderId = otherUserId, message = "Third", createdAt = 3000L)

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(msg1, msg2, msg3),
            currentUserId = currentUserId
        )

        assertEquals("Third", summary.lastMessage)
        assertEquals(3000L, summary.lastTime)
    }

    // 8. Chronological ordering
    @Test
    fun calculate_outOfOrderMessages_resolvesToHighestTimestamp() {
        val msg1 = Message(id = "m1", senderId = otherUserId, message = "Newest", createdAt = 5000L)
        val msg2 = Message(id = "m2", senderId = otherUserId, message = "Oldest", createdAt = 1000L)
        val msg3 = Message(id = "m3", senderId = otherUserId, message = "Middle", createdAt = 3000L)

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(msg1, msg2, msg3),
            currentUserId = currentUserId
        )

        assertEquals("Newest", summary.lastMessage)
        assertEquals(5000L, summary.lastTime)
    }

    // 9. Empty message list
    @Test
    fun calculate_emptyMessageList_returnsSafeDefaults() {
        val summary = MessagePreviewCalculator.calculate(
            messages = emptyList(),
            currentUserId = currentUserId
        )

        assertNull(summary.lastMessage)
        assertNull(summary.lastTime)
        assertNull(summary.lastSenderName)
        assertEquals(0, summary.unreadCount)
    }

    // 10. Multiple unread messages
    @Test
    fun calculate_multipleUnreadMessages_accumulatesCorrectly() {
        val messages = listOf(
            Message(id = "m1", senderId = otherUserId, message = "A", createdAt = 1000L, readBy = emptyMap()),
            Message(id = "m2", senderId = thirdUserId, message = "B", createdAt = 2000L, readBy = emptyMap()),
            Message(id = "m3", senderId = otherUserId, message = "C", createdAt = 3000L, readBy = emptyMap())
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = messages,
            currentUserId = currentUserId
        )

        assertEquals(3, summary.unreadCount)
        assertEquals("C", summary.lastMessage)
    }

    // 11. Mixed read and unread messages
    @Test
    fun calculate_mixedReadAndUnreadMessages_onlyCountsUnreadFromOthers() {
        val messages = listOf(
            // Read by me -> 0
            Message(id = "m1", senderId = otherUserId, message = "A", createdAt = 1000L, readBy = mapOf(currentUserId to true)),
            // Unread by me -> 1
            Message(id = "m2", senderId = otherUserId, message = "B", createdAt = 2000L, readBy = emptyMap()),
            // Sent by me (unread by others) -> 0 for me
            Message(id = "m3", senderId = currentUserId, message = "C", createdAt = 3000L, readBy = emptyMap()),
            // Unread by me -> 1
            Message(id = "m4", senderId = thirdUserId, message = "D", createdAt = 4000L, readBy = emptyMap()),
            // Read by me -> 0
            Message(id = "m5", senderId = thirdUserId, message = "E", createdAt = 5000L, readBy = mapOf(currentUserId to true))
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = messages,
            currentUserId = currentUserId
        )

        assertEquals(2, summary.unreadCount)
        assertEquals("E", summary.lastMessage)
        assertEquals(5000L, summary.lastTime)
    }

    // 12. Photo message with text combination
    @Test
    fun calculate_photoWithText_photoLabelTakesPrecedence() {
        val message = Message(
            id = "m1",
            senderId = otherUserId,
            message = "Check out this image!",
            imageUrl = "https://example.com/image.png",
            createdAt = 1000L,
            senderName = "Charlie"
        )

        val directChatSummary = MessagePreviewCalculator.calculate(
            messages = listOf(message),
            currentUserId = currentUserId,
            photoLabel = "Photo"
        )
        assertEquals("Photo", directChatSummary.lastMessage)

        val groupSummary = MessagePreviewCalculator.calculate(
            messages = listOf(message),
            currentUserId = currentUserId,
            photoLabel = "📷 Photo"
        )
        assertEquals("📷 Photo", groupSummary.lastMessage)
    }

    // 13. Corrupted / empty messages ignored when valid message exists
    @Test
    fun calculate_corruptedEmptyMessages_ignoredWhenValidMessageExists() {
        val validMsg = Message(
            id = "m1",
            senderId = otherUserId,
            message = "Valid message text",
            createdAt = 1000L,
            senderName = "Alice"
        )
        // Corrupted legacy message with empty strings and default/zero values
        val corruptedMsg = Message(
            id = "m2",
            senderId = "",
            message = "",
            createdAt = 0L,
            senderName = ""
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(validMsg, corruptedMsg),
            currentUserId = currentUserId
        )

        assertEquals("Valid message text", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
        assertEquals("Alice", summary.lastSenderName)
    }

    // 14. Only corrupted / empty messages return null
    @Test
    fun calculate_onlyCorruptedMessages_returnsNullDefaults() {
        val corruptedMsg1 = Message(id = "m1", message = "", imageUrl = null)
        val corruptedMsg2 = Message(id = "m2", message = null, imageUrl = null)

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(corruptedMsg1, corruptedMsg2),
            currentUserId = currentUserId
        )

        assertNull(summary.lastMessage)
        assertNull(summary.lastTime)
        assertEquals(0, summary.unreadCount)
    }

    // 15. Blank whitespace message is ignored
    @Test
    fun calculate_blankWhitespaceMessage_ignored() {
        val validMsg = Message(
            id = "m1",
            senderId = otherUserId,
            message = "Legitimate text",
            createdAt = 1000L
        )
        val blankMsg = Message(
            id = "m2",
            senderId = otherUserId,
            message = "   ",
            createdAt = 2000L
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(validMsg, blankMsg),
            currentUserId = currentUserId
        )

        assertEquals("Legitimate text", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
    }
}
