package com.example.kchat.feature.threadsummary

import com.example.kchat.model.Message

/**
 * Contract for the Smart Thread Summarization service.
 */
interface ThreadSummaryService {
    suspend fun summarizeThread(
        messages: List<Message>,
        currentUserId: String
    ): Result<ThreadSummaryResult>
}
