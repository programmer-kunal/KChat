package com.example.kchat.feature.smartreply

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig

/**
 * Configuration constants for KChat Smart Reply (Emotion & Sentiment Intelligence).
 * Uses the primary Flash-class Gemini model supported by Firebase AI Logic
 * for low-latency, lightweight Smart Reply inference.
 */
object SmartReplyConfig {
    /**
     * Primary Flash-class Gemini model for Smart Reply inference.
     */
    const val MODEL_NAME = "gemini-3.5-flash-lite"

    /**
     * Factory function to create the GenerativeModel configured with the
     * Gemini Developer API backend (Google AI).
     */
    fun createGenerativeModel(): GenerativeModel {
        val config = generationConfig {
            responseMimeType = "application/json"
        }
        return Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(
                modelName = MODEL_NAME,
                generationConfig = config
            )
    }
}
