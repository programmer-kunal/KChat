package com.example.kchat.feature.chat

import com.example.kchat.model.Message
import java.util.Collections
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe, bounded in-memory cache for parsed messages across active conversations.
 * Preserves parsed message lists when ChatViewModel is destroyed on back navigation,
 * enabling instantaneous (0ms) chat opening upon re-entry.
 */
@Singleton
class ChatRepository @Inject constructor() {

    companion object {
        private const val MAX_CACHED_CHANNELS = 25
        val instance: ChatRepository by lazy { ChatRepository() }
    }

    // Thread-safe bounded LRU cache retaining up to MAX_CACHED_CHANNELS active conversations
    private val channelMessageCache: MutableMap<String, List<Message>> = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<Message>>(MAX_CACHED_CHANNELS, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Message>>?): Boolean {
                return size > MAX_CACHED_CHANNELS
            }
        }
    )

    fun getCachedMessages(channelId: String): List<Message>? {
        return channelMessageCache[channelId]
    }

    fun putCachedMessages(channelId: String, messages: List<Message>) {
        channelMessageCache[channelId] = messages
    }

    fun clearCache() {
        channelMessageCache.clear()
    }
}
