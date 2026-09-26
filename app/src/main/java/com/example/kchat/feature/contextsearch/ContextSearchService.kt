package com.example.kchat.feature.contextsearch

import com.example.kchat.feature.extension.model.ExtensionScreenshot
import com.example.kchat.model.Message

/**
 * Contract for the Context-Aware Conversation Search service.
 * Generalized to support both internal messages and external extension context (text + screenshots).
 */
interface ContextSearchService {
    suspend fun searchConversation(
        query: String,
        messages: List<Message>,
        currentUserId: String,
        screenshots: List<ExtensionScreenshot> = emptyList()
    ): Result<ContextSearchResult>
}
