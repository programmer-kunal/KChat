package com.example.kchat.feature.smartreply

import com.example.kchat.model.Message

/**
 * Interface contract for Smart Reply generation.
 */
interface SmartReplyService {
    suspend fun generateSmartReplies(
        selectedMessages: List<Message>,
        currentUserId: String
    ): Result<SmartReplyResult>
}
