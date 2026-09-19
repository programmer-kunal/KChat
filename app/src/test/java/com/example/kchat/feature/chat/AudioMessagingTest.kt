package com.example.kchat.feature.chat

import com.example.kchat.feature.chat.audio.AudioUtils
import com.example.kchat.model.Message
import com.example.kchat.model.MessagePreviewCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMessagingTest {

    // --- AudioUtils Duration Formatting Tests ---

    @Test
    fun formatDuration_zeroOrNegative_returnsZeroTime() {
        assertEquals("0:00", AudioUtils.formatDuration(0L))
        assertEquals("0:00", AudioUtils.formatDuration(-500L))
    }

    @Test
    fun formatDuration_underOneMinute_padsSeconds() {
        assertEquals("0:05", AudioUtils.formatDuration(5_000L))
        assertEquals("0:09", AudioUtils.formatDuration(9_999L))
        assertEquals("0:10", AudioUtils.formatDuration(10_000L))
        assertEquals("0:45", AudioUtils.formatDuration(45_000L))
        assertEquals("0:59", AudioUtils.formatDuration(59_000L))
    }

    @Test
    fun formatDuration_overOneMinute_formatsMinutesAndPaddedSeconds() {
        assertEquals("1:00", AudioUtils.formatDuration(60_000L))
        assertEquals("1:05", AudioUtils.formatDuration(65_000L))
        assertEquals("1:30", AudioUtils.formatDuration(90_000L))
        assertEquals("2:00", AudioUtils.formatDuration(120_000L))
        assertEquals("2:15", AudioUtils.formatDuration(135_000L))
    }

    // --- Message Model Audio Fields Tests ---

    @Test
    fun message_defaultConstructor_audioFieldsAreNull() {
        val msg = Message()
        assertNull(msg.audioUrl)
        assertNull(msg.audioDurationMs)
    }

    @Test
    fun message_withAudioFields_storesAndRetrievesCorrectly() {
        val msg = Message(
            id = "audio_1",
            senderId = "user_1",
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/voice1.m4a",
            audioDurationMs = 15000L
        )
        assertEquals("https://supabase.co/storage/v1/object/public/chatter_images/voice1.m4a", msg.audioUrl)
        assertEquals(15000L, msg.audioDurationMs)
    }

    // --- MessagePreviewCalculator Audio Integration Tests ---

    @Test
    fun messagePreviewCalculator_singleAudioMessage_showsVoiceMessagePreview() {
        val audioMsg = Message(
            id = "m1",
            senderId = "other_user",
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/voice.m4a",
            audioDurationMs = 8000L,
            createdAt = 1000L,
            senderName = "Bob",
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(audioMsg),
            currentUserId = "me"
        )

        assertEquals("🎤 Voice message", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
        assertEquals("Bob", summary.lastSenderName)
        assertEquals(0, summary.unreadCount)
    }

    @Test
    fun messagePreviewCalculator_unreadAudioMessage_incrementsUnreadCount() {
        val audioMsg = Message(
            id = "m1",
            senderId = "other_user",
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/voice.m4a",
            audioDurationMs = 12000L,
            createdAt = 1000L,
            senderName = "Alice",
            readBy = mapOf("other_user" to true) // unread by "me"
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(audioMsg),
            currentUserId = "me"
        )

        assertEquals("🎤 Voice message", summary.lastMessage)
        assertEquals(1, summary.unreadCount)
    }

    @Test
    fun messagePreviewCalculator_audioFollowedByText_showsLatestTextMessage() {
        val audioMsg = Message(
            id = "m1",
            senderId = "other_user",
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/voice.m4a",
            audioDurationMs = 8000L,
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val textMsg = Message(
            id = "m2",
            senderId = "other_user",
            message = "Did you hear that voice note?",
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(audioMsg, textMsg),
            currentUserId = "me"
        )

        assertEquals("Did you hear that voice note?", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_textFollowedByAudio_showsLatestVoiceMessage() {
        val textMsg = Message(
            id = "m1",
            senderId = "other_user",
            message = "Listen to this:",
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val audioMsg = Message(
            id = "m2",
            senderId = "other_user",
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/voice.m4a",
            audioDurationMs = 5000L,
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(textMsg, audioMsg),
            currentUserId = "me"
        )

        assertEquals("🎤 Voice message", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_deletedAudioMessage_isSkipped() {
        val audioMsg = Message(
            id = "m1",
            senderId = "other_user",
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/voice.m4a",
            audioDurationMs = 5000L,
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )
        val olderTextMsg = Message(
            id = "m0",
            senderId = "other_user",
            message = "Older text",
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(olderTextMsg, audioMsg),
            currentUserId = "me",
            deletedMessageIds = setOf("m1")
        )

        assertEquals("Older text", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
    }

    // --- Audio Reply Snippet Logic Tests ---

    @Test
    fun replySnippet_forAudioMessage_returnsVoiceMessageLabel() {
        val audioMsg = Message(
            id = "m1",
            senderId = "other_user",
            message = null,
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/voice.m4a",
            audioDurationMs = 10000L
        )

        val replySnippet = if (!audioMsg.message.isNullOrBlank()) {
            audioMsg.message.trim()
        } else if (!audioMsg.imageUrl.isNullOrBlank()) {
            "[Photo]"
        } else if (!audioMsg.audioUrl.isNullOrBlank()) {
            "🎤 Voice message"
        } else {
            ""
        }

        assertEquals("🎤 Voice message", replySnippet)
    }
}
