package com.example.kchat.feature.scamguard

import com.example.kchat.model.Message

/**
 * Service contract for evaluating messages for scam and phishing indicators.
 */
interface ScamGuardService {
    suspend fun analyzeMessages(
        messages: List<Message>,
        currentUserId: String
    ): Result<ScamGuardResult>
}
