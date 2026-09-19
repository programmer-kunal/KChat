package com.example.kchat.feature.smartreply

import com.example.kchat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [SmartReplyService] powered by Firebase AI Logic
 * and the Gemini Developer API.
 */
@Singleton
class GeminiSmartReplyService @Inject constructor() : SmartReplyService {

    override suspend fun generateSmartReplies(
        selectedMessages: List<Message>,
        currentUserId: String
    ): Result<SmartReplyResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (selectedMessages.isEmpty()) {
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
                    val senderLabel = if (msg.senderId == currentUserId) {
                        "Me"
                    } else {
                        val name = msg.senderName.trim()
                        if (name.isNotEmpty()) "Other ($name)" else "Other"
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
                appendLine("You are KChat Smart Reply, an AI assistant providing emotion and sentiment intelligence to support direct 1-to-1 messaging.")
                appendLine("Analyze the conversational context below and output:")
                appendLine("1. 'tone': Likely tone (describe probabilistically, e.g. 'Likely friendly', 'Likely frustrated', 'Likely inquiring', 'Likely neutral'. Do not claim certainty).")
                appendLine("2. 'sentiment': Sentiment (Positive, Neutral, or Negative).")
                appendLine("3. 'intent': Inferred intent of the other participant (e.g. 'Seeking clarification', 'Confirming plan', 'Sharing update').")
                appendLine("4. 'urgency': Inferred urgency level (Low, Medium, or High).")
                appendLine("5. 'suggestions': An array of 2 to 4 concise, polite, natural reply options for 'Me' to send.")
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
                appendLine("Conversation context:")
                appendLine(formattedDialogue)
            }

            val model = SmartReplyConfig.createGenerativeModel()
            val response = model.generateContent(prompt)
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
