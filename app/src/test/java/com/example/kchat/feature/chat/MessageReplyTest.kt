package com.example.kchat.feature.chat

import com.example.kchat.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageReplyTest {

    @Test
    fun message_defaultConstructor_replyFieldsAreNull() {
        val message = Message()
        assertNull(message.replyToMessageId)
        assertNull(message.replyToSenderName)
        assertNull(message.replyToMessageText)
    }

    @Test
    fun message_withReplyFields_storesAndRetrievesCorrectly() {
        val message = Message(
            id = "msg_123",
            senderId = "user_abc",
            senderName = "Bob",
            message = "I agree with you",
            createdAt = 1000L,
            replyToMessageId = "orig_001",
            replyToSenderName = "Alice",
            replyToMessageText = "Are we meeting today?"
        )

        assertEquals("orig_001", message.replyToMessageId)
        assertEquals("Alice", message.replyToSenderName)
        assertEquals("Are we meeting today?", message.replyToMessageText)
    }

    @Test
    fun messageCopyFormatter_singleTextMessage_returnsBodyOnly() {
        val message = Message(
            id = "m1",
            senderId = "u1",
            senderName = "Alice",
            message = "Hello World!",
            createdAt = 1000L
        )

        val result = MessageCopyFormatter.format(listOf(message))
        assertEquals("Hello World!", result)
    }

    @Test
    fun messageCopyFormatter_multipleTextMessages_formatsWithSenderAndChronologicalOrder() {
        val msg1 = Message(
            id = "m1",
            senderId = "u1",
            senderName = "Alice",
            message = "First message",
            createdAt = 1000L
        )
        val msg2 = Message(
            id = "m2",
            senderId = "u2",
            senderName = "Bob",
            message = "Second message",
            createdAt = 2000L
        )

        // Pass in reverse order to verify chronological sorting
        val result = MessageCopyFormatter.format(listOf(msg2, msg1))
        val expected = "Alice: First message\nBob: Second message"
        assertEquals(expected, result)
    }

    @Test
    fun messageCopyFormatter_imageOnlyMessage_returnsEmptyString() {
        val imgMsg = Message(
            id = "m1",
            senderId = "u1",
            senderName = "Alice",
            message = null,
            imageUrl = "https://example.com/img.jpg",
            createdAt = 1000L
        )

        val result = MessageCopyFormatter.format(listOf(imgMsg))
        assertEquals("", result)
    }

    @Test
    fun messageCopyFormatter_mixedTextAndImageMessages_copiesOnlyTextMessages() {
        val msg1 = Message(
            id = "m1",
            senderId = "u1",
            senderName = "Alice",
            message = "Look at this photo",
            createdAt = 1000L
        )
        val imgMsg = Message(
            id = "m2",
            senderId = "u1",
            senderName = "Alice",
            message = null,
            imageUrl = "https://example.com/pic.jpg",
            createdAt = 2000L
        )
        val msg2 = Message(
            id = "m3",
            senderId = "u2",
            senderName = "Bob",
            message = "Looks amazing!",
            createdAt = 3000L
        )

        val result = MessageCopyFormatter.format(listOf(msg1, imgMsg, msg2))
        val expected = "Alice: Look at this photo\nBob: Looks amazing!"
        assertEquals(expected, result)
    }

    @Test
    fun messageCopyFormatter_blankSenderName_defaultsToUser() {
        val msg1 = Message(
            id = "m1",
            senderId = "u1",
            senderName = "",
            message = "Hello",
            createdAt = 1000L
        )
        val msg2 = Message(
            id = "m2",
            senderId = "u2",
            senderName = "   ",
            message = "Hi",
            createdAt = 2000L
        )

        val result = MessageCopyFormatter.format(listOf(msg1, msg2))
        val expected = "User: Hello\nUser: Hi"
        assertEquals(expected, result)
    }

    @Test
    fun messageCopyFormatter_emptyList_returnsEmptyString() {
        val result = MessageCopyFormatter.format(emptyList())
        assertEquals("", result)
    }
}
