package com.example.kchat.feature.contextsearch

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Encapsulates an individual message result returned by Context-Aware Search.
 * Grounded directly in an original conversation message.
 */
data class ContextSearchResultItem(
    val messageId: String,
    val senderId: String,
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val isCurrentUser: Boolean
) {
    val formattedTime: String
        get() {
            if (timestamp <= 0L) return ""
            return try {
                val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                sdf.format(Date(timestamp))
            } catch (e: Exception) {
                ""
            }
        }
}

/**
 * Result container for Context-Aware Search containing the query,
 * the detected intent (if any), grounded message items, and grounded screenshot IDs.
 */
data class ContextSearchResult(
    val query: String,
    val intent: String = "",
    val items: List<ContextSearchResultItem> = emptyList(),
    val matchingImageIds: List<String> = emptyList(),
    val answer: String = "",
    val isFallbackMatch: Boolean = false
) {
    val isEmpty: Boolean
        get() = items.isEmpty() && matchingImageIds.isEmpty() && answer.isBlank()

    fun toFormattedText(): String = buildString {
        appendLine("🔎 Context Search: \"$query\"")
        if (intent.isNotBlank()) {
            appendLine("Intent: $intent")
        }
        if (answer.isNotBlank()) {
            appendLine()
            appendLine("Answer:")
            appendLine(answer)
        }
        if (items.isNotEmpty()) {
            appendLine()
            appendLine("Referenced Messages:")
            items.forEach { item ->
                appendLine("[${item.messageId}] ${item.senderName}: ${item.messageText}")
            }
        }
        if (matchingImageIds.isNotEmpty()) {
            appendLine()
            appendLine("Referenced Screenshots: ${matchingImageIds.joinToString()}")
        }
    }.trim()
}
