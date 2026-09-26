package com.example.kchat.feature.scamguard

import com.example.kchat.feature.extension.model.ExtensionScreenshot
import com.example.kchat.model.Message

/**
 * Service contract for evaluating messages for scam and phishing indicators.
 * Generalized to support both internal messages and external extension context (text + screenshots).
 */
interface ScamGuardService {
    suspend fun analyzeMessages(
        messages: List<Message>,
        currentUserId: String,
        screenshots: List<ExtensionScreenshot> = emptyList()
    ): Result<ScamGuardResult>
}
