package com.example.kchat.feature.chat

import com.example.kchat.feature.chat.video.VideoUtils
import com.example.kchat.model.Message
import com.example.kchat.model.MessagePreviewCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoMessagingTest {

    // --- VideoUtils Duration Formatting Tests ---

    @Test
    fun formatDuration_zeroOrNegative_returnsZeroTime() {
        assertEquals("0:00", VideoUtils.formatDuration(0L))
        assertEquals("0:00", VideoUtils.formatDuration(-1000L))
    }

    @Test
    fun formatDuration_underOneMinute_padsSeconds() {
        assertEquals("0:05", VideoUtils.formatDuration(5_000L))
        assertEquals("0:09", VideoUtils.formatDuration(9_999L))
        assertEquals("0:10", VideoUtils.formatDuration(10_000L))
        assertEquals("0:45", VideoUtils.formatDuration(45_000L))
        assertEquals("0:59", VideoUtils.formatDuration(59_000L))
    }

    @Test
    fun formatDuration_overOneMinute_formatsMinutesAndPaddedSeconds() {
        assertEquals("1:00", VideoUtils.formatDuration(60_000L))
        assertEquals("1:05", VideoUtils.formatDuration(65_000L))
        assertEquals("1:30", VideoUtils.formatDuration(90_000L))
        assertEquals("2:00", VideoUtils.formatDuration(120_000L))
    }

    // --- VideoUtils File Size Formatting Tests ---

    @Test
    fun formatFileSize_zeroOrNegative_returnsZeroB() {
        assertEquals("0 B", VideoUtils.formatFileSize(0L))
        assertEquals("0 B", VideoUtils.formatFileSize(-100L))
    }

    @Test
    fun formatFileSize_underOneMegabyte_formatsKB() {
        val bytes500KB = 500 * 1024L
        assertEquals("500 KB", VideoUtils.formatFileSize(bytes500KB))
    }

    @Test
    fun formatFileSize_overOneMegabyte_formatsMB() {
        val bytes2MB = (2.5 * 1024 * 1024).toLong()
        assertEquals("2.5 MB", VideoUtils.formatFileSize(bytes2MB))
    }

    // --- Message Model Video Fields Tests ---

    @Test
    fun message_defaultConstructor_videoFieldsAreNull() {
        val msg = Message()
        assertNull(msg.videoUrl)
        assertNull(msg.videoDurationMs)
    }

    @Test
    fun message_withVideoFields_storesAndRetrievesCorrectly() {
        val msg = Message(
            id = "vid_1",
            senderId = "user_1",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/video1.mp4",
            videoDurationMs = 45000L
        )
        assertEquals("https://supabase.co/storage/v1/object/public/chatter_images/video1.mp4", msg.videoUrl)
        assertEquals(45000L, msg.videoDurationMs)
    }

    // --- MessagePreviewCalculator Video Integration Tests ---

    @Test
    fun messagePreviewCalculator_singleVideoMessage_showsVideoMessagePreview() {
        val videoMsg = Message(
            id = "m1",
            senderId = "other_user",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/video.mp4",
            videoDurationMs = 12000L,
            createdAt = 1000L,
            senderName = "Charlie",
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(videoMsg),
            currentUserId = "me"
        )

        assertEquals("🎥 Video message", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
        assertEquals("Charlie", summary.lastSenderName)
        assertEquals(0, summary.unreadCount)
    }

    @Test
    fun messagePreviewCalculator_unreadVideoMessage_incrementsUnreadCount() {
        val videoMsg = Message(
            id = "m1",
            senderId = "other_user",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/video.mp4",
            videoDurationMs = 15000L,
            createdAt = 1000L,
            senderName = "Dana",
            readBy = mapOf("other_user" to true) // unread by "me"
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(videoMsg),
            currentUserId = "me"
        )

        assertEquals("🎥 Video message", summary.lastMessage)
        assertEquals(1, summary.unreadCount)
    }

    @Test
    fun messagePreviewCalculator_videoFollowedByText_showsLatestTextMessage() {
        val videoMsg = Message(
            id = "m1",
            senderId = "other_user",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/video.mp4",
            videoDurationMs = 10000L,
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val textMsg = Message(
            id = "m2",
            senderId = "other_user",
            message = "Check out that clip!",
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(videoMsg, textMsg),
            currentUserId = "me"
        )

        assertEquals("Check out that clip!", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_textFollowedByVideo_showsLatestVideoMessage() {
        val textMsg = Message(
            id = "m1",
            senderId = "other_user",
            message = "Sending video:",
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val videoMsg = Message(
            id = "m2",
            senderId = "other_user",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/video.mp4",
            videoDurationMs = 20000L,
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(textMsg, videoMsg),
            currentUserId = "me"
        )

        assertEquals("🎥 Video message", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_audioFollowedByVideo_showsVideoMessage() {
        val audioMsg = Message(
            id = "m1",
            senderId = "other_user",
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/audio.m4a",
            audioDurationMs = 5000L,
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val videoMsg = Message(
            id = "m2",
            senderId = "other_user",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/video.mp4",
            videoDurationMs = 15000L,
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(audioMsg, videoMsg),
            currentUserId = "me"
        )

        assertEquals("🎥 Video message", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_videoFollowedByAudio_showsVoiceMessage() {
        val videoMsg = Message(
            id = "m1",
            senderId = "other_user",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/video.mp4",
            videoDurationMs = 15000L,
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val audioMsg = Message(
            id = "m2",
            senderId = "other_user",
            audioUrl = "https://supabase.co/storage/v1/object/public/chatter_images/audio.m4a",
            audioDurationMs = 5000L,
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(videoMsg, audioMsg),
            currentUserId = "me"
        )

        assertEquals("🎤 Voice message", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_deletedVideoMessage_skippedCorrectly() {
        val deletedVideoMsg = Message(
            id = "del_vid",
            senderId = "other_user",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/video.mp4",
            videoDurationMs = 10000L,
            createdAt = 3000L,
            readBy = mapOf("other_user" to true)
        )
        val activeTextMsg = Message(
            id = "active_text",
            senderId = "other_user",
            message = "Earlier text",
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(activeTextMsg, deletedVideoMsg),
            currentUserId = "me",
            deletedMessageIds = setOf("del_vid")
        )

        assertEquals("Earlier text", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
        assertEquals(0, summary.unreadCount)
    }
}
