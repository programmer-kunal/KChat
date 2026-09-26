package com.example.kchat.feature.threadsummary

import com.example.kchat.feature.extension.model.ExtensionScreenshot
import com.example.kchat.model.Message

/**
 * Contract for the Smart Thread Summarization service.
 * Generalized to support both internal messages and external extension context (text + screenshots).
 */
interface ThreadSummaryService {
    suspend fun summarizeThread(
        messages: List<Message>,
        currentUserId: String,
        screenshots: List<ExtensionScreenshot> = emptyList()
    ): Result<ThreadSummaryResult>
}
