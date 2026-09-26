package com.example.kchat.feature.threadsummary

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
 * Implementation of [ThreadSummaryService] powered by Firebase AI Logic
 * and the Gemini Developer API (gemini-3.5-flash-lite).
 * Supports both internal messages and external extension context (text + screenshots).
 */
@Singleton
class GeminiThreadSummaryService @Inject constructor() : ThreadSummaryService {

    companion object {
        private const val MODEL_NAME = "gemini-3.5-flash-lite"
        private const val MAX_CONTEXT_MESSAGES = 60
        private const val MAX_MESSAGE_CHAR_LENGTH = 500
    }

    override suspend fun summarizeThread(
        messages: List<Message>,
        currentUserId: String,
        screenshots: List<ExtensionScreenshot>
    ): Result<ThreadSummaryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val validScreenshots = screenshots.filter { it.bitmap != null }
            if (messages.isEmpty() && validScreenshots.isEmpty()) {
                return@runCatching ThreadSummaryResult()
            }

            // Safe bounded context: last 60 messages in chronological order
            val boundedMessages = messages
                .takeLast(MAX_CONTEXT_MESSAGES)
                .sortedBy { it.createdAt }

            val formattedDialogue = buildString {
                for (msg in boundedMessages) {
                    val senderLabel = if (msg.senderId == currentUserId || msg.senderName.equals("You", ignoreCase = true) || msg.senderName.equals("Me", ignoreCase = true)) {
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

            if (formattedDialogue.isBlank() && validScreenshots.isEmpty()) {
                return@runCatching ThreadSummaryResult()
            }

            val prompt = buildString {
                appendLine("You are KChat Thread Summary, an AI conversational intelligence assistant.")
                if (validScreenshots.isNotEmpty()) {
                    appendLine("NOTE: The user has attached ${validScreenshots.size} screenshot(s) of conversation(s) (${validScreenshots.joinToString { it.id }}). Please analyze visible content in the screenshot(s) in addition to any conversation dialogue below.")
                }
                appendLine("Analyze the conversation below and summarize key information into concise structured JSON.")
                appendLine()
                appendLine("Extract items under these 5 categories:")
                appendLine("1. 'keyPoints': Essential topics discussed or core conversation context.")
                appendLine("2. 'decisions': Definite decisions or agreements made by the participants.")
                appendLine("3. 'tasks': Action items, assignments, or things someone agreed to do.")
                appendLine("4. 'deadlines': Specific dates, times, days, or deadlines explicitly mentioned.")
                appendLine("5. 'importantDetails': Key numbers, locations, or specific facts shared.")
                appendLine()
                appendLine("STRICT ACCURACY & ANTI-HALLUCINATION RULES:")
                appendLine("- Summarize ONLY facts, statements, and commitments explicitly present in the dialogue or screenshots.")
                appendLine("- Do NOT invent or assume decisions, tasks, deadlines, people, or facts that are not confirmed.")
                appendLine("- If a category has no relevant or confirmed information, return an empty array [] for that category.")
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
                if (formattedDialogue.isNotBlank()) {
                    appendLine("Conversation Dialogue:")
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
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start >= 0 && end > start) {
                JSONObject(cleaned.substring(start, end + 1))
            } else {
                throw e
            }
        }

        fun extractStringList(key: String): List<String> {
            val array = json.optJSONArray(key) ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.optString(i, "").trim()
                if (item.isNotBlank()) {
                    list.add(item)
                }
            }
            return list
        }

        return ThreadSummaryResult(
            keyPoints = extractStringList("keyPoints"),
            decisions = extractStringList("decisions"),
            tasks = extractStringList("tasks"),
            deadlines = extractStringList("deadlines"),
            importantDetails = extractStringList("importantDetails")
        )
    }
}
