package com.example.kchat.feature.smartreply

import com.example.kchat.feature.extension.model.ExtensionScreenshot
import com.example.kchat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [SmartReplyService] powered by Firebase AI Logic
 * and the Gemini Developer API.
 * Supports both internal messages and external extension context (text + screenshots).
 */
@Singleton
class GeminiSmartReplyService @Inject constructor() : SmartReplyService {

    override suspend fun generateSmartReplies(
        selectedMessages: List<Message>,
        currentUserId: String,
        screenshots: List<ExtensionScreenshot>
    ): Result<SmartReplyResult> = withContext(Dispatchers.IO) {
        runCatching {
            val validScreenshots = screenshots.filter { it.bitmap != null }
            if (selectedMessages.isEmpty() && validScreenshots.isEmpty()) {
                return@runCatching SmartReplyResult(
                    tone = "Likely neutral",
                    sentiment = "Neutral",
                    intent = "General conversation",
                    urgency = "Low",
                    suggestions = listOf("Sounds good.", "Got it, thank you.", "Let me check.")
                )
            }

            val chronologicalMessages = selectedMessages.sortedBy { it.createdAt }

            val formattedDialogue = buildString {
                for (msg in chronologicalMessages) {
                    val senderLabel = if (msg.senderId == currentUserId || msg.senderName.equals("You", ignoreCase = true) || msg.senderName.equals("Me", ignoreCase = true)) {
                        "Me"
                    } else {
                        val name = msg.senderName.trim()
                        if (name.isNotEmpty()) name else "Other"
                    }

                    val textContent = when {
                        !msg.message.isNullOrBlank() -> msg.message.trim()
                        !msg.imageUrl.isNullOrBlank() -> "[Sent an image]"
                        else -> "[Message]"
                    }
                    appendLine("$senderLabel: $textContent")
                }
            }

            val prompt = buildString {
                appendLine("You are KChat Smart Reply, an AI assistant providing emotion, sentiment intelligence, and contextual reply suggestions for messaging conversations.")
                if (validScreenshots.isNotEmpty()) {
                    appendLine("NOTE: The user has attached ${validScreenshots.size} screenshot(s) of conversation(s) (${validScreenshots.joinToString { it.id }}). Please read the visible text in the screenshot(s) in addition to any dialogue text provided below.")
                }
                appendLine("Analyze the conversational context and output:")
                appendLine("1. 'tone': Likely tone (describe probabilistically, e.g. 'Likely friendly', 'Likely frustrated', 'Likely inquiring', 'Likely neutral'. Do not claim certainty).")
                appendLine("2. 'sentiment': Sentiment (Positive, Neutral, or Negative).")
                appendLine("3. 'intent': Inferred intent of the speaker(s) (e.g. 'Seeking clarification', 'Confirming plan', 'Sharing update').")
                appendLine("4. 'urgency': Inferred urgency level (Low, Medium, or High).")
                appendLine("5. 'suggestions': An array of 2 to 4 concise, polite, natural reply options for the user to send in response.")
                appendLine()
                appendLine("Return ONLY valid JSON matching this exact structure without markdown fences:")
                appendLine("{")
                appendLine("  \"tone\": \"Likely ...\",")
                appendLine("  \"sentiment\": \"...\",")
                appendLine("  \"intent\": \"...\",")
                appendLine("  \"urgency\": \"...\",")
                appendLine("  \"suggestions\": [")
                appendLine("    \"...\",")
                appendLine("    \"...\"")
                appendLine("  ]")
                appendLine("}")
                appendLine()
                if (formattedDialogue.isNotBlank()) {
                    appendLine("Conversation context:")
                    appendLine(formattedDialogue)
                }
            }

            val model = SmartReplyConfig.createGenerativeModel()
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
            parseModelResponse(responseText)
        }
    }

    private fun parseModelResponse(rawText: String): SmartReplyResult {
        val cleaned = rawText
            .replace("```json", "")
            .replace("```", "")
            .trim()

        val json = try {
            JSONObject(cleaned)
        } catch (e: Exception) {
            val jsonStart = cleaned.indexOf('{')
            val jsonEnd = cleaned.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                JSONObject(cleaned.substring(jsonStart, jsonEnd + 1))
            } else {
                throw e
            }
        }

        val tone = json.optString("tone", "Likely neutral").ifBlank { "Likely neutral" }
        val sentiment = json.optString("sentiment", "Neutral").ifBlank { "Neutral" }
        val intent = json.optString("intent", "Conversation").ifBlank { "Conversation" }
        val urgency = json.optString("urgency", "Normal").ifBlank { "Normal" }

        val suggestionsList = mutableListOf<String>()
        val suggestionsJson = json.optJSONArray("suggestions")
        if (suggestionsJson != null) {
            for (i in 0 until suggestionsJson.length()) {
                val item = suggestionsJson.optString(i, "").trim()
                if (item.isNotEmpty()) {
                    suggestionsList.add(item)
                }
            }
        }

        if (suggestionsList.isEmpty()) {
            suggestionsList.addAll(listOf("Got it, thank you.", "I understand. Let me check.", "Sounds good!"))
        }

        return SmartReplyResult(
            tone = tone,
            sentiment = sentiment,
            intent = intent,
            urgency = urgency,
            suggestions = suggestionsList.take(4)
        )
    }
}
