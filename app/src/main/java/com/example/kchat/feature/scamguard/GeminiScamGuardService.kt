package com.example.kchat.feature.scamguard

import com.example.kchat.feature.extension.model.ExtensionScreenshot
import com.example.kchat.model.Message
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [ScamGuardService] powered by Firebase AI Logic
 * and the Gemini Developer API using the gemini-3.5-flash-lite model.
 *
 * Operates strictly on-demand on user-selected or imported messages/screenshots
 * with zero persistent storage and zero background scanning.
 */
@Singleton
class GeminiScamGuardService @Inject constructor() : ScamGuardService {

    companion object {
        const val MODEL_NAME = "gemini-3.5-flash-lite"
        private const val MAX_MESSAGE_CHAR_LENGTH = 500
    }

    override suspend fun analyzeMessages(
        messages: List<Message>,
        currentUserId: String,
        screenshots: List<ExtensionScreenshot>
    ): Result<ScamGuardResult> = withContext(Dispatchers.IO) {
        runCatching {
            val validScreenshots = screenshots.filter { it.bitmap != null }
            if (messages.isEmpty() && validScreenshots.isEmpty()) {
                return@runCatching ScamGuardResult(
                    riskLevel = ScamRiskLevel.LOW,
                    isSuspicious = false,
                    summary = "No messages or screenshots provided for analysis.",
                    indicators = emptyList(),
                    recommendedActions = listOf("Provide one or more messages or screenshots to evaluate potential scam or phishing risks.")
                )
            }

            // Always preserve chronological order of messages
            val chronologicalMessages = messages.sortedBy { it.createdAt }

            val formattedDialogue = buildString {
                for (msg in chronologicalMessages) {
                    val senderLabel = if (msg.senderId == currentUserId || msg.senderName.equals("You", ignoreCase = true) || msg.senderName.equals("Me", ignoreCase = true)) {
                        "Me"
                    } else {
                        val name = msg.senderName.trim()
                        if (name.isNotEmpty()) "Other ($name)" else "Other"
                    }

                    // Preserve URLs exactly as present in original text; do not visit or resolve them.
                    val textContent = when {
                        !msg.message.isNullOrBlank() -> msg.message.trim().take(MAX_MESSAGE_CHAR_LENGTH)
                        !msg.imageUrl.isNullOrBlank() -> "[Sent an image]"
                        else -> "[Message]"
                    }
                    appendLine("$senderLabel: $textContent")
                }
            }

            val prompt = buildString {
                appendLine("You are KChat Scam Guard, an AI conversational security assistant.")
                if (validScreenshots.isNotEmpty()) {
                    appendLine("NOTE: The user has attached ${validScreenshots.size} screenshot(s) (${validScreenshots.joinToString { it.id }}). Carefully inspect any visible messages, URLs, phone numbers, payment requests, or text in the screenshot(s) in addition to any text below.")
                }
                appendLine("Carefully evaluate the conversation context below for potential scam, phishing, or social engineering risks.")
                appendLine()
                appendLine("EVALUATION CATEGORIES TO CHECK:")
                appendLine("1. Phishing / suspicious or disguised links")
                appendLine("2. Credential theft (asking for passwords, login details, PINs)")
                appendLine("3. OTP / verification-code / 2FA code requests")
                appendLine("4. Financial, payment, wire transfer, cryptocurrency, or gift-card requests")
                appendLine("5. Fake rewards, prizes, lottery winnings, giveaways, or unearned offers")
                appendLine("6. Account suspension, security alert, or urgent verification pretexts")
                appendLine("7. Impersonation of banks, support agents, executives, or trusted figures")
                appendLine("8. Artificial urgency, fear tactics, or coercive manipulation")
                appendLine("9. Suspicious requests to download or install software/APK/apps")
                appendLine("10. Requests for sensitive personal information (Govt IDs, SSN, bank accounts)")
                appendLine()
                appendLine("STRICT ANTI-FALSE-POSITIVE & SAFETY RULES:")
                appendLine("- Evaluate the complete selected message context together rather than isolated words.")
                appendLine("- NEVER state certainty (never say 'Definitely a scam'). Use objective language such as 'Potentially suspicious' or 'Shows patterns commonly associated with phishing'.")
                appendLine("- Distinguish between LOW, MEDIUM, and HIGH risk:")
                appendLine("  * LOW: Normal conversation, benign requests, or insufficient evidence of deceptive intent.")
                appendLine("  * MEDIUM: Contains suspicious patterns (e.g. unsolicited links, unusual urgency, vague offers) but lacks definitive malicious confirmation.")
                appendLine("  * HIGH: Strong, unmistakable indicators of fraud (e.g. asking for OTP/passwords, bank transfer fraud, fake security suspension threats, known scam templates).")
                appendLine("- Base your assessment ONLY on observable evidence in the supplied text and screenshots.")
                appendLine("- Treat URLs only as static text; do not assume a domain is malicious unless there is observable deception or phishing structure.")
                appendLine("- Do NOT invent facts, identities, or external claims.")
                appendLine("- If there is insufficient evidence, default to riskLevel 'LOW' and explain that no significant scam indicators were detected.")
                appendLine("- Avoid unnecessary panic or sensationalist wording.")
                appendLine()
                appendLine("OUTPUT FORMAT:")
                appendLine("Return ONLY valid JSON matching this exact structure without markdown fences:")
                appendLine("{")
                appendLine("  \"riskLevel\": \"LOW | MEDIUM | HIGH\",")
                appendLine("  \"isSuspicious\": true | false,")
                appendLine("  \"summary\": \"Concise 1-2 sentence overview of the risk assessment.\",")
                appendLine("  \"indicators\": [\"Observable indicator 1\", \"Observable indicator 2\"],")
                appendLine("  \"recommendedActions\": [\"Actionable safety recommendation 1\", \"Actionable safety recommendation 2\"]")
                appendLine("}")
                appendLine()
                if (formattedDialogue.isNotBlank()) {
                    appendLine("Selected Messages:")
                    appendLine(formattedDialogue)
                }
            }

            val config = generationConfig {
                responseMimeType = "application/json"
            }
            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = MODEL_NAME,
                    generationConfig = config
                )

            val response = if (validScreenshots.isNotEmpty()) {
                val parts = mutableListOf<com.google.firebase.ai.type.Part>()
                for (sc in validScreenshots) {
                    parts.add(com.google.firebase.ai.type.ImagePart(sc.bitmap!!))
                }
                parts.add(com.google.firebase.ai.type.TextPart(prompt))
                val multiModalContent = com.google.firebase.ai.type.Content(role = "user", parts = parts)
                model.generateContent(multiModalContent)
            } else {
                model.generateContent(prompt)
            }

            val responseText = response.text ?: throw IllegalStateException("Empty response from AI model")
            parseScamResponse(responseText)
        }
    }

    fun parseScamGuardResponse(rawText: String): ScamGuardResult = parseScamResponse(rawText)

    internal fun parseScamResponse(rawText: String): ScamGuardResult {
        val cleaned = rawText
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val json = try {
            JSONObject(cleaned)
        } catch (e: Exception) {
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start >= 0 && end > start) {
                JSONObject(cleaned.substring(start, end + 1))
            } else {
                throw e
            }
        }

        val riskLevelStr = json.optString("riskLevel", "LOW")
        val riskLevel = ScamRiskLevel.fromString(riskLevelStr)
        val isSuspicious = json.optBoolean("isSuspicious", riskLevel != ScamRiskLevel.LOW)
        val summary = json.optString("summary", "").trim()

        val indicatorsList = mutableListOf<String>()
        val indicatorsArray = json.optJSONArray("indicators")
        if (indicatorsArray != null) {
            for (i in 0 until indicatorsArray.length()) {
                val item = indicatorsArray.optString(i, "").trim()
                if (item.isNotBlank()) indicatorsList.add(item)
            }
        }

        val actionsList = mutableListOf<String>()
        val actionsArray = json.optJSONArray("recommendedActions")
        if (actionsArray != null) {
            for (i in 0 until actionsArray.length()) {
                val item = actionsArray.optString(i, "").trim()
                if (item.isNotBlank()) actionsList.add(item)
            }
        }

        return ScamGuardResult(
            riskLevel = riskLevel,
            isSuspicious = isSuspicious,
            summary = summary,
            indicators = indicatorsList.take(4),
            recommendedActions = actionsList.take(4)
        )
    }
}
