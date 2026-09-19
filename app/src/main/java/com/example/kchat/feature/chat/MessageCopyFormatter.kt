package com.example.kchat.feature.chat

import com.example.kchat.model.Message

/**
 * Pure helper for formatting selected messages for clipboard copy.
 */
object MessageCopyFormatter {

    /**
     * Formats a list of selected messages for clipboard copying.
     * - Filters out messages that do not have text (e.g. image-only).
     * - If single text message: returns ONLY the message body.
     * - If multiple text messages: sorts chronologically by createdAt and formats as:
     *   "Sender Name: Message text"
     *   separated by line breaks.
     * - Image-only messages are excluded and their URLs are never copied.
     */
    fun format(selectedMessages: List<Message>): String {
        val textMessages = selectedMessages
            .filter { !it.message.isNullOrBlank() }
            .sortedBy { it.createdAt }

        if (textMessages.isEmpty()) return ""

        if (textMessages.size == 1) {
            return textMessages.first().message?.trim() ?: ""
        }

        return textMessages.joinToString("\n\n") { msg ->
            val sender = msg.senderName.ifBlank { "Someone" }
            val text = msg.message?.trim() ?: ""
            "$sender: $text"
        }
    }
}
