package com.example.kchat.feature.chat

import com.example.kchat.feature.chat.file.FileUtils
import com.example.kchat.model.Message
import com.example.kchat.model.MessagePreviewCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileMessagingTest {

    // --- Message Model Document/File Fields Tests ---

    @Test
    fun message_defaultConstructor_fileFieldsAreNull() {
        val msg = Message()
        assertNull(msg.fileUrl)
        assertNull(msg.fileName)
        assertNull(msg.fileMimeType)
        assertNull(msg.fileSizeBytes)
    }

    @Test
    fun message_withFileFields_storesAndRetrievesCorrectly() {
        val msg = Message(
            id = "file_1",
            senderId = "user_1",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/doc1.pdf",
            fileName = "Project_Proposal.pdf",
            fileMimeType = "application/pdf",
            fileSizeBytes = 1024 * 1024L
        )
        assertEquals("https://supabase.co/storage/v1/object/public/chatter_images/doc1.pdf", msg.fileUrl)
        assertEquals("Project_Proposal.pdf", msg.fileName)
        assertEquals("application/pdf", msg.fileMimeType)
        assertEquals(1024 * 1024L, msg.fileSizeBytes)
    }

    // --- FileUtils File Size Formatting Tests ---

    @Test
    fun formatFileSize_zeroOrNegative_returnsZeroB() {
        assertEquals("0 B", FileUtils.formatFileSize(0L))
        assertEquals("0 B", FileUtils.formatFileSize(-500L))
    }

    @Test
    fun formatFileSize_underOneMegabyte_formatsKB() {
        val bytes500KB = 500 * 1024L
        assertEquals("500 KB", FileUtils.formatFileSize(bytes500KB))
    }

    @Test
    fun formatFileSize_overOneMegabyte_formatsMB() {
        val bytes2MB = (2.5 * 1024 * 1024).toLong()
        assertEquals("2.5 MB", FileUtils.formatFileSize(bytes2MB))
    }

    // --- FileUtils File Extension Label Tests ---

    @Test
    fun getFileExtensionLabel_knownExtensions_returnsUppercase() {
        assertEquals("PDF", FileUtils.getFileExtensionLabel("document.pdf", "application/pdf"))
        assertEquals("DOCX", FileUtils.getFileExtensionLabel("notes.docx", null))
        assertEquals("XLSX", FileUtils.getFileExtensionLabel("sheet.xlsx", null))
        assertEquals("ZIP", FileUtils.getFileExtensionLabel("archive.zip", "application/zip"))
        assertEquals("TXT", FileUtils.getFileExtensionLabel("readme.txt", "text/plain"))
    }

    @Test
    fun getFileExtensionLabel_noExtension_infersFromMimeType() {
        assertEquals("PDF", FileUtils.getFileExtensionLabel("download", "application/pdf"))
        assertEquals("DOC", FileUtils.getFileExtensionLabel("download", "application/msword"))
        assertEquals("ZIP", FileUtils.getFileExtensionLabel("download", "application/zip"))
    }

    // --- MessagePreviewCalculator Document/File Integration Tests ---

    @Test
    fun messagePreviewCalculator_singleFileMessage_showsFilenamePreview() {
        val fileMsg = Message(
            id = "f1",
            senderId = "other_user",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/report.pdf",
            fileName = "Quarterly_Report.pdf",
            fileSizeBytes = 2048L,
            createdAt = 1000L,
            senderName = "Alice",
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(fileMsg),
            currentUserId = "me"
        )

        assertEquals("📎 Quarterly_Report.pdf", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
        assertEquals("Alice", summary.lastSenderName)
        assertEquals(0, summary.unreadCount)
    }

    @Test
    fun messagePreviewCalculator_unreadFileMessage_incrementsUnreadCount() {
        val fileMsg = Message(
            id = "f1",
            senderId = "other_user",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/doc.pdf",
            fileName = "Doc.pdf",
            createdAt = 1000L,
            senderName = "Bob",
            readBy = mapOf("other_user" to true) // unread by "me"
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(fileMsg),
            currentUserId = "me"
        )

        assertEquals("📎 Doc.pdf", summary.lastMessage)
        assertEquals(1, summary.unreadCount)
    }

    @Test
    fun messagePreviewCalculator_longFilename_truncatesSafely() {
        val longName = "A_Very_Long_Document_Filename_That_Exceeds_Thirty_Characters_Easily.pdf"
        val fileMsg = Message(
            id = "f1",
            senderId = "other_user",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/doc.pdf",
            fileName = longName,
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(fileMsg),
            currentUserId = "me"
        )

        val expected = "📎 " + longName.take(27) + "..."
        assertEquals(expected, summary.lastMessage)
    }

    @Test
    fun messagePreviewCalculator_fileWithoutFilename_showsGenericPreview() {
        val fileMsg = Message(
            id = "f1",
            senderId = "other_user",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/unnamed",
            fileName = null,
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(fileMsg),
            currentUserId = "me"
        )

        assertEquals("📎 File", summary.lastMessage)
    }

    @Test
    fun messagePreviewCalculator_fileFollowedByText_showsLatestTextMessage() {
        val fileMsg = Message(
            id = "f1",
            senderId = "other_user",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/doc.pdf",
            fileName = "Doc.pdf",
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val textMsg = Message(
            id = "t1",
            senderId = "other_user",
            message = "Please review the attached doc.",
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(fileMsg, textMsg),
            currentUserId = "me"
        )

        assertEquals("Please review the attached doc.", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_textFollowedByFile_showsLatestFile() {
        val textMsg = Message(
            id = "t1",
            senderId = "other_user",
            message = "Here is the file:",
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val fileMsg = Message(
            id = "f1",
            senderId = "other_user",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/doc.pdf",
            fileName = "Summary.pdf",
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(textMsg, fileMsg),
            currentUserId = "me"
        )

        assertEquals("📎 Summary.pdf", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_videoFollowedByFile_showsFile() {
        val videoMsg = Message(
            id = "v1",
            senderId = "other_user",
            videoUrl = "https://supabase.co/storage/v1/object/public/chatter_images/vid.mp4",
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )
        val fileMsg = Message(
            id = "f1",
            senderId = "other_user",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/notes.txt",
            fileName = "notes.txt",
            createdAt = 2000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(videoMsg, fileMsg),
            currentUserId = "me"
        )

        assertEquals("📎 notes.txt", summary.lastMessage)
        assertEquals(2000L, summary.lastTime)
    }

    @Test
    fun messagePreviewCalculator_deletedFileMessage_skippedCorrectly() {
        val deletedFileMsg = Message(
            id = "del_file",
            senderId = "other_user",
            fileUrl = "https://supabase.co/storage/v1/object/public/chatter_images/doc.pdf",
            fileName = "Secret.pdf",
            createdAt = 3000L,
            readBy = mapOf("other_user" to true)
        )
        val activeTextMsg = Message(
            id = "active_text",
            senderId = "other_user",
            message = "Earlier conversation",
            createdAt = 1000L,
            readBy = mapOf("me" to true)
        )

        val summary = MessagePreviewCalculator.calculate(
            messages = listOf(activeTextMsg, deletedFileMsg),
            currentUserId = "me",
            deletedMessageIds = setOf("del_file")
        )

        assertEquals("Earlier conversation", summary.lastMessage)
        assertEquals(1000L, summary.lastTime)
        assertEquals(0, summary.unreadCount)
    }
}
