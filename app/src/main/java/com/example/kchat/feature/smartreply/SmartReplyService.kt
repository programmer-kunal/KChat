package com.example.kchat.feature.smartreply

import com.example.kchat.feature.extension.model.ExtensionScreenshot
import com.example.kchat.model.Message

/**
 * Interface contract for Smart Reply generation.
 * Generalized to support both internal messages and external extension context (including screenshots).
 */
interface SmartReplyService {
    suspend fun generateSmartReplies(
        selectedMessages: List<Message>,
        currentUserId: String,
        screenshots: List<ExtensionScreenshot> = emptyList()
    ): Result<SmartReplyResult>
}
