package com.example.kchat.feature.threadsummary

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
 * Implementation of [ThreadSummaryService] powered by Firebase AI Logic
 * and the Gemini Developer API (gemini-3.5-flash-lite).
 */
@Singleton
class GeminiThreadSummaryService @Inject constructor() : ThreadSummaryService {

    companion object {
        private const val MODEL_NAME = "gemini-3.5-flash-lite"
        private const val MAX_CONTEXT_MESSAGES = 50
        private const val MAX_MESSAGE_CHAR_LENGTH = 500
    }

    override suspend fun summarizeThread(
        messages: List<Message>,
        currentUserId: String
    ): Result<ThreadSummaryResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (messages.isEmpty()) {
                return@runCatching ThreadSummaryResult()
            }

            // Safe bounded context: last 50 messages in chronological order
            val boundedMessages = messages
                .takeLast(MAX_CONTEXT_MESSAGES)
                .sortedBy { it.createdAt }

            val formattedDialogue = buildString {
                for (msg in boundedMessages) {
                    val senderLabel = if (msg.senderId == currentUserId) {
                        "Me"
                    } else {
                        val name = msg.senderName.trim()
                        if (name.isNotEmpty()) "Other ($name)" else "Other"
                    }

                    val textContent = when {
                        !msg.message.isNullOrBlank() -> msg.message.trim().take(MAX_MESSAGE_CHAR_LENGTH)
                        !msg.imageUrl.isNullOrBlank() -> "[Sent an image]"
                        else -> "[Message]"
                    }
                    appendLine("$senderLabel: $textContent")
                }
            }

            if (formattedDialogue.isBlank()) {
                return@runCatching ThreadSummaryResult()
            }

            val prompt = buildString {
                appendLine("You are KChat Thread Summary, an AI conversational intelligence assistant.")
                appendLine("Analyze the 1-to-1 conversation dialogue below and summarize key information into concise structured JSON.")
                appendLine()
                appendLine("Extract items under these 5 categories:")
                appendLine("1. 'keyPoints': Essential topics discussed or core conversation context.")
                appendLine("2. 'decisions': Definite decisions or agreements made by the participants.")
                appendLine("3. 'tasks': Action items, assignments, or things someone agreed to do.")
                appendLine("4. 'deadlines': Specific dates, times, days, or deadlines explicitly mentioned.")
                appendLine("5. 'importantDetails': Key numbers, locations, or specific facts shared.")
                appendLine()
                appendLine("STRICT ACCURACY & ANTI-HALLUCINATION RULES:")
                appendLine("- Summarize ONLY facts, statements, and commitments explicitly present in the dialogue.")
                appendLine("- Do NOT invent or assume decisions, tasks, deadlines, people, or facts that are not confirmed in the dialogue.")
                appendLine("- If a category has no relevant or confirmed information in the dialogue, return an empty array [] for that category.")
                appendLine("- Do NOT report suggestions or uncertain possibilities as confirmed decisions.")
                appendLine("- Preserve exact names, numbers, dates, and times when mentioned.")
                appendLine("- Keep each item concise (1-2 sentences).")
                appendLine("- Return ONLY valid JSON matching this exact structure without markdown formatting:")
                appendLine("{")
                appendLine("  \"keyPoints\": [\"...\"],")
                appendLine("  \"decisions\": [\"...\"],")
                appendLine("  \"tasks\": [\"...\"],")
                appendLine("  \"deadlines\": [\"...\"],")
                appendLine("  \"importantDetails\": [\"...\"]")
                appendLine("}")
                appendLine()
                appendLine("Conversation Dialogue:")
                appendLine(formattedDialogue)
            }

            val config = generationConfig {
                responseMimeType = "application/json"
            }
            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(
                    modelName = MODEL_NAME,
                    generationConfig = config
                )

            val response = model.generateContent(prompt)
            val responseText = response.text ?: throw IllegalStateException("Empty response from AI model")

            parseSummaryResponse(responseText)
        }
    }

    private fun parseSummaryResponse(rawText: String): ThreadSummaryResult {
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

        fun extractList(key: String): List<String> {
            val array = json.optJSONArray(key) ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i, "").trim()
                if (item.isNotEmpty()) {
                    list.add(item)
                }
            }
            return list
        }

        return ThreadSummaryResult(
            keyPoints = extractList("keyPoints"),
            decisions = extractList("decisions"),
            tasks = extractList("tasks"),
            deadlines = extractList("deadlines"),
            importantDetails = extractList("importantDetails")
        )
    }
}
