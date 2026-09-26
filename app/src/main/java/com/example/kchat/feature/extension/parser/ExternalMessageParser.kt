package com.example.kchat.feature.extension.parser

import com.example.kchat.feature.extension.model.ParsedConversation
import com.example.kchat.model.Message
import java.util.Locale

/**
 * Platform-tolerant conversation parser for external messages copied from
 * WhatsApp, Telegram, Instagram, SMS, Messenger, or generic text.
 *
 * Grounding Guarantee:
 * Assigns temporary session IDs (ext_001, ext_002, ...) to every parsed message.
 * These IDs are never persisted to Firebase or Supabase.
 */
object ExternalMessageParser {

    // WhatsApp bracket style: [13/08, 19:28] Ajeeb Prani: 25
    private val WHATSAPP_BRACKET_REGEX = Regex(
        """^\[(\d{1,4}[/.-]\d{1,2}[/.-]\d{1,4}|\d{1,2}[/.-]\d{1,2})[,\s]+(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AaPp][Mm])?)\]\s*(?:-\s*)?([^:]+?):\s*(.*)$"""
    )

    // WhatsApp export style: 13/08/24, 7:28 pm - Ajeeb Prani: 25
    private val WHATSAPP_DASH_REGEX = Regex(
        """^(\d{1,4}[/.-]\d{1,2}[/.-]\d{1,4}|\d{1,2}[/.-]\d{1,2})[,\s]+(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AaPp][Mm])?)\s*-\s*([^:]+?):\s*(.*)$"""
    )

    // Telegram inline timestamp style: [19:28] Ajeeb Prani: 25
    private val TELEGRAM_INLINE_REGEX = Regex(
        """^\[(\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AaPp][Mm])?)\]\s*([^:]+?):\s*(.*)$"""
    )

    // Telegram multiline header: Ajeeb Prani, [13.08.2024 19:28]
    private val TELEGRAM_HEADER_REGEX = Regex(
        """^([^,\n]+?),\s*\[?(\d{1,2}[/.-]\d{1,2}[/.-]\d{2,4}\s+\d{1,2}:\d{2}(?::\d{2})?)\]?$"""
    )

    // Generic Sender: Message (e.g. "Ajeeb Prani: 25")
    private val GENERIC_SENDER_REGEX = Regex(
        """^([^\s:][^:\n]{0,35}):\s*(.+)$"""
    )

    fun parse(rawInput: String): ParsedConversation {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return ParsedConversation(rawText = "")
        }

        val lines = trimmed.lines()
        val parsedItems = mutableListOf<RawParsedMessage>()

        var currentSender: String? = null
        var currentTimeStr: String? = null
        val currentMessageBody = StringBuilder()

        fun flushCurrent() {
            if (currentSender != null && currentMessageBody.isNotBlank()) {
                parsedItems.add(
                    RawParsedMessage(
                        sender = currentSender!!.trim(),
                        timeStr = currentTimeStr,
                        text = currentMessageBody.toString().trim()
                    )
                )
            }
            currentSender = null
            currentTimeStr = null
            currentMessageBody.clear()
        }

        var detectedFormat = false
        var formatLabel = "Text imported"

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) {
                if (currentMessageBody.isNotEmpty()) {
                    currentMessageBody.append("\n")
                }
                i++
                continue
            }

            // 1. Check WhatsApp Bracket style
            val waBracketMatch = WHATSAPP_BRACKET_REGEX.find(line)
            if (waBracketMatch != null) {
                flushCurrent()
                detectedFormat = true
                formatLabel = "WhatsApp-style conversation detected"
                val date = waBracketMatch.groupValues[1]
                val time = waBracketMatch.groupValues[2]
                currentSender = waBracketMatch.groupValues[3]
                currentTimeStr = "$date $time"
                currentMessageBody.append(waBracketMatch.groupValues[4])
                i++
                continue
            }

            // 2. Check WhatsApp Dash style
            val waDashMatch = WHATSAPP_DASH_REGEX.find(line)
            if (waDashMatch != null) {
                flushCurrent()
                detectedFormat = true
                formatLabel = "WhatsApp export format detected"
                val date = waDashMatch.groupValues[1]
                val time = waDashMatch.groupValues[2]
                currentSender = waDashMatch.groupValues[3]
                currentTimeStr = "$date $time"
                currentMessageBody.append(waDashMatch.groupValues[4])
                i++
                continue
            }

            // 3. Check Telegram Inline style
            val tgInlineMatch = TELEGRAM_INLINE_REGEX.find(line)
            if (tgInlineMatch != null) {
                flushCurrent()
                detectedFormat = true
                formatLabel = "Telegram-style conversation detected"
                currentTimeStr = tgInlineMatch.groupValues[1]
                currentSender = tgInlineMatch.groupValues[2]
                currentMessageBody.append(tgInlineMatch.groupValues[3])
                i++
                continue
            }

            // 4. Check Telegram Multiline Header style
            val tgHeaderMatch = TELEGRAM_HEADER_REGEX.find(line)
            if (tgHeaderMatch != null) {
                flushCurrent()
                detectedFormat = true
                formatLabel = "Telegram conversation detected"
                currentSender = tgHeaderMatch.groupValues[1]
                currentTimeStr = tgHeaderMatch.groupValues[2]
                // The actual message follows on subsequent line(s)
                i++
                continue
            }

            // 5. Check Generic "Sender: Message"
            val genericMatch = GENERIC_SENDER_REGEX.find(line)
            if (genericMatch != null && isValidSenderName(genericMatch.groupValues[1])) {
                flushCurrent()
                detectedFormat = true
                if (formatLabel == "Text imported") {
                    formatLabel = "Conversation format detected"
                }
                currentSender = genericMatch.groupValues[1]
                currentMessageBody.append(genericMatch.groupValues[2])
                i++
                continue
            }

            // Multi-line continuation: append to active message if one is accumulating
            if (currentSender != null) {
                if (currentMessageBody.isNotEmpty()) {
                    currentMessageBody.append("\n")
                }
                currentMessageBody.append(line)
            } else {
                // Unstructured line before any sender header
                if (currentMessageBody.isNotEmpty()) {
                    currentMessageBody.append("\n")
                }
                currentMessageBody.append(line)
            }
            i++
        }
        flushCurrent()

        // Fallback for unstructured text where no headers were recognized
        if (parsedItems.isEmpty() && trimmed.isNotBlank()) {
            // Split by distinct non-empty paragraphs or preserve as single block
            val paragraphs = trimmed.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
            if (paragraphs.size > 1) {
                paragraphs.forEachIndexed { idx, p ->
                    parsedItems.add(
                        RawParsedMessage(
                            sender = "Text Block ${idx + 1}",
                            timeStr = null,
                            text = p
                        )
                    )
                }
            } else {
                parsedItems.add(
                    RawParsedMessage(
                        sender = "Imported Text",
                        timeStr = null,
                        text = trimmed
                    )
                )
            }
            formatLabel = "Text imported"
            detectedFormat = false
        }

        val baseTime = System.currentTimeMillis() - (parsedItems.size * 60_000L)
        val finalMessages = parsedItems.mapIndexed { index, item ->
            val tempId = String.format(Locale.US, "ext_%03d", index + 1)
            Message(
                id = tempId,
                senderId = item.sender,
                senderName = item.sender,
                message = item.text,
                createdAt = baseTime + (index * 60_000L)
            )
        }

        val participants = finalMessages
            .map { it.senderName.trim() }
            .filter { it.isNotBlank() && !it.startsWith("Text Block") && it != "Imported Text" }
            .distinct()

        return ParsedConversation(
            messages = finalMessages,
            participants = participants,
            formatDescription = formatLabel,
            isFormatDetected = detectedFormat,
            rawText = rawInput
        )
    }

    private fun isValidSenderName(candidate: String): Boolean {
        val trimmed = candidate.trim()
        if (trimmed.length < 2 || trimmed.length > 35) return false
        if (trimmed.startsWith("http", ignoreCase = true) || trimmed.startsWith("ftp", ignoreCase = true)) return false
        if (trimmed.all { it.isDigit() || it == ':' || it == '.' || it == '/' || it == '-' }) return false
        return true
    }

    private data class RawParsedMessage(
        val sender: String,
        val timeStr: String?,
        val text: String
    )
}
