package com.example.kchat.feature.contextsearch

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
 * Implementation of [ContextSearchService] powered by Firebase AI Logic
 * and the Gemini Developer API (gemini-3.5-flash-lite).
 *
 * Employs a hybrid strategy:
 * 1. Primary: Gemini semantic intent analysis and candidate message ID ranking.
 * 2. Fallback: Local keyword/token matching if the network/AI call is unavailable.
 *
 * Source-of-truth rule: Returned search results are ALWAYS grounded in the original
 * local Message objects. Gemini never writes or rewrites message content.
 */
@Singleton
class GeminiContextSearchService @Inject constructor() : ContextSearchService {

    companion object {
        private const val MODEL_NAME = "gemini-3.5-flash-lite"
        private const val MAX_CONTEXT_MESSAGES = 60
        private const val MAX_MESSAGE_CHAR_LENGTH = 300
        private const val MAX_RESULTS = 8

        private val STOP_WORDS = setOf(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and",
            "any", "are", "aren't", "as", "at", "be", "because", "been", "before", "being",
            "below", "between", "both", "but", "by", "can't", "cannot", "could", "couldn't",
            "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", "during",
            "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
            "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here",
            "here's", "hers", "herself", "him", "himself", "his", "how", "how's", "i",
            "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't", "it", "it's",
            "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself",
            "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought",
            "our", "ours", "ourselves", "out", "over", "own", "same", "shan't", "she",
            "she'd", "she'll", "she's", "should", "shouldn't", "so", "some", "such",
            "than", "that", "that's", "the", "their", "theirs", "them", "themselves",
            "then", "there", "there's", "these", "they", "they'd", "they'll", "they're",
            "they've", "this", "those", "through", "to", "too", "under", "until", "up",
            "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
            "weren't", "what", "what's", "when", "when's", "where", "where's", "which",
            "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would",
            "wouldn't", "you", "you'd", "you'll", "you're", "you've", "your", "yours",
            "yourself", "yourselves"
        )
    }

    override suspend fun searchConversation(
        query: String,
        messages: List<Message>,
        currentUserId: String
    ): Result<ContextSearchResult> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank() || messages.isEmpty()) {
            return@withContext Result.success(ContextSearchResult(query = trimmedQuery))
        }

        // Map messages by genuine ID for strict local validation
        val messageMap = messages.filter { it.id.isNotBlank() }.associateBy { it.id }
        if (messageMap.isEmpty()) {
            return@withContext Result.success(ContextSearchResult(query = trimmedQuery))
        }

        // Bounded candidate context: up to 60 messages in chronological order
        val candidateMessages = messages
            .takeLast(MAX_CONTEXT_MESSAGES)
            .sortedBy { it.createdAt }

        // Attempt primary semantic search using Gemini
        try {
            val formattedCandidates = buildString {
                for (msg in candidateMessages) {
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
                    appendLine("[ID: ${msg.id}] $senderLabel: $textContent")
                }
            }

            val prompt = buildString {
                appendLine("You are KChat Context Search, an AI conversational search engine.")
                appendLine("Your task is to identify which messages in the dialogue below are relevant to answering or addressing the user's natural-language query.")
                appendLine()
                appendLine("User Search Query: \"$trimmedQuery\"")
                appendLine()
                appendLine("STRICT RULES:")
                appendLine("1. Analyze the semantic intent of the query (e.g. dates/times, decisions, assigned tasks, locations, confirmations, or specific topics).")
                appendLine("2. Identify candidate messages that directly answer, discuss, or provide relevant context for the query.")
                appendLine("3. Select between 1 and 6 of the most relevant message IDs from the provided dialogue. Order them from most relevant to least relevant.")
                appendLine("4. Use ONLY IDs that appear verbatim in the candidate list. NEVER invent or hallucinate message IDs.")
                appendLine("5. If no messages in the dialogue are relevant to the query, return an empty array [] for 'relevantMessageIds'. Do NOT guess.")
                appendLine("6. Return valid JSON only matching this exact schema without markdown fences:")
                appendLine("{")
                appendLine("  \"intent\": \"Brief description of detected query intent\",")
                appendLine("  \"relevantMessageIds\": [\"id_1\", \"id_2\"]")
                appendLine("}")
                appendLine()
                appendLine("Candidate Dialogue:")
                appendLine(formattedCandidates)
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

            val parsedResult = parseGeminiResponse(responseText, messageMap, trimmedQuery, currentUserId)
            Result.success(parsedResult)
        } catch (e: Exception) {
            // Fallback: local keyword/token matching when AI call is unavailable
            val fallbackResult = performLocalFallbackSearch(trimmedQuery, candidateMessages, currentUserId)
            Result.success(fallbackResult)
        }
    }

    private fun parseGeminiResponse(
        rawText: String,
        messageMap: Map<String, Message>,
        query: String,
        currentUserId: String
    ): ContextSearchResult {
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

        val intent = json.optString("intent", "").trim()
        val idsArray = json.optJSONArray("relevantMessageIds")
        val resultItems = mutableListOf<ContextSearchResultItem>()

        if (idsArray != null) {
            for (i in 0 until idsArray.length()) {
                val rawId = idsArray.optString(i, "").trim()
                // Strict validation: ID must exist in actual local message map
                val message = messageMap[rawId]
                if (message != null && resultItems.none { it.messageId == message.id }) {
                    resultItems.add(
                        ContextSearchResultItem(
                            messageId = message.id,
                            senderId = message.senderId,
                            senderName = if (message.senderId == currentUserId) "You" else message.senderName.trim().ifEmpty { "Other" },
                            messageText = message.message?.trim()?.ifEmpty { "[Sent an image]" } ?: "[Sent an image]",
                            timestamp = message.createdAt,
                            isCurrentUser = message.senderId == currentUserId
                        )
                    )
                }
            }
        }

        return ContextSearchResult(
            query = query,
            intent = intent,
            items = resultItems.take(MAX_RESULTS),
            isFallbackMatch = false
        )
    }

    /**
     * Safe local fallback search when Gemini is unavailable.
     * Matches significant keywords from the query without claiming semantic understanding.
     */
    private fun performLocalFallbackSearch(
        query: String,
        candidateMessages: List<Message>,
        currentUserId: String
    ): ContextSearchResult {
        val tokens = query.lowercase()
            .split("\\s+".toRegex())
            .map { it.filter { ch -> ch.isLetterOrDigit() } }
            .filter { it.isNotBlank() && it !in STOP_WORDS }

        val matches = candidateMessages.filter { msg ->
            val text = msg.message?.lowercase() ?: ""
            if (text.isBlank()) return@filter false

            // Exact phrase match or token matches
            text.contains(query.lowercase()) || (tokens.isNotEmpty() && tokens.any { text.contains(it) })
        }

        val items = matches
            .takeLast(MAX_RESULTS)
            .map { msg ->
                ContextSearchResultItem(
                    messageId = msg.id,
                    senderId = msg.senderId,
                    senderName = if (msg.senderId == currentUserId) "You" else msg.senderName.trim().ifEmpty { "Other" },
                    messageText = msg.message?.trim() ?: "[Sent an image]",
                    timestamp = msg.createdAt,
                    isCurrentUser = msg.senderId == currentUserId
                )
            }

        return ContextSearchResult(
            query = query,
            intent = "",
            items = items,
            isFallbackMatch = true
        )
    }
}
