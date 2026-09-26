package com.example.kchat.feature.chat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lightweight, persistent conversation mute manager backed by Android SharedPreferences.
 * Scopes mute state per user and per conversation/group to survive app restarts and navigation.
 * API parameter order: (context, uid, channelId).
 */
object ConversationMuteManager {

    private const val PREFS_NAME = "kchat_mute_preferences"

    private val _muteStateVersion = MutableStateFlow(0)
    val muteStateVersion: StateFlow<Int> = _muteStateVersion

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun buildKey(uid: String, channelId: String): String {
        val safeUid = if (uid.isBlank()) "global" else uid
        return "mute_${safeUid}_$channelId"
    }

    fun isMuted(context: Context, uid: String?, channelId: String?): Boolean {
        if (channelId.isNullOrBlank() || uid.isNullOrBlank()) return false
        val key = buildKey(uid, channelId)
        return getPrefs(context).getBoolean(key, false)
    }

    fun setMuted(context: Context, uid: String?, channelId: String?, isMuted: Boolean) {
        if (channelId.isNullOrBlank() || uid.isNullOrBlank()) return
        val key = buildKey(uid, channelId)
        getPrefs(context).edit().putBoolean(key, isMuted).apply()
        _muteStateVersion.value += 1
    }

    fun toggleMute(context: Context, uid: String?, channelId: String?): Boolean {
        val current = isMuted(context, uid, channelId)
        val next = !current
        setMuted(context, uid, channelId, next)
        return next
    }
}
