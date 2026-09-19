package com.example.kchat.feature.contextsearch

import com.example.kchat.model.Message

/**
 * Contract for the Context-Aware Conversation Search service.
 */
interface ContextSearchService {
    suspend fun searchConversation(
        query: String,
        messages: List<Message>,
        currentUserId: String
    ): Result<ContextSearchResult>
}
