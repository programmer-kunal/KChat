package com.example.kchat.data.cache

import android.content.Context
import android.util.Log
import com.example.kchat.feature.chat.DirectChatItem
import com.example.kchat.feature.home.HomeChannel
import com.example.kchat.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object LocalDataCache {
    private const val TAG = "LocalDataCache"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun getUserDir(uid: String): File? {
        val ctx = appContext ?: return null
        if (uid.isBlank()) return null
        val safeUid = uid.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dir = File(ctx.filesDir, "user_cache_$safeUid")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun safeChannelFileName(channelId: String): String {
        return "messages_" + channelId.replace(Regex("[^a-zA-Z0-9_-]"), "_") + ".json"
    }

    // --- Direct Chats Cache ---

    suspend fun saveDirectChats(uid: String, items: List<DirectChatItem>) = withContext(Dispatchers.IO) {
        val dir = getUserDir(uid) ?: return@withContext
        try {
            val jsonArray = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("uid", item.uid)
                    put("name", item.name)
                    put("imageUrl", item.imageUrl ?: JSONObject.NULL)
                    put("lastMessage", item.lastMessage ?: JSONObject.NULL)
                    put("lastTime", item.lastTime)
                    put("unreadCount", item.unreadCount)
                }
                jsonArray.put(obj)
            }
            writeStringAtomically(File(dir, "direct_chats.json"), jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving direct chats cache: ${e.message}")
        }
    }

    suspend fun loadDirectChats(uid: String): List<DirectChatItem> = withContext(Dispatchers.IO) {
        val dir = getUserDir(uid) ?: return@withContext emptyList()
        val file = File(dir, "direct_chats.json")
        if (!file.exists()) return@withContext emptyList()
        try {
            val content = file.readText(StandardCharsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<DirectChatItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    DirectChatItem(
                        uid = obj.optString("uid"),
                        name = obj.optString("name"),
                        imageUrl = if (obj.isNull("imageUrl")) null else obj.optString("imageUrl"),
                        lastMessage = if (obj.isNull("lastMessage")) null else obj.optString("lastMessage"),
                        lastTime = obj.optLong("lastTime", 0L),
                        unreadCount = obj.optInt("unreadCount", 0)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading direct chats cache: ${e.message}")
            emptyList()
        }
    }

    // --- Channels Cache ---

    suspend fun saveHomeChannels(uid: String, channels: List<HomeChannel>) = withContext(Dispatchers.IO) {
        val dir = getUserDir(uid) ?: return@withContext
        try {
            val jsonArray = JSONArray()
            for (ch in channels) {
                if (com.example.kchat.feature.home.HomeViewModel.isWorldChat(ch.id, ch.name)) continue
                val obj = JSONObject().apply {
                    put("id", ch.id)
                    put("name", ch.name)
                    put("lastMessage", ch.lastMessage ?: JSONObject.NULL)
                    put("lastTime", ch.lastTime ?: JSONObject.NULL)
                    put("lastSenderName", ch.lastSenderName ?: JSONObject.NULL)
                    put("unreadCount", ch.unreadCount)
                    put("creatorUid", ch.creatorUid ?: JSONObject.NULL)
                    put("imageUrl", ch.imageUrl ?: JSONObject.NULL)
                }
                jsonArray.put(obj)
            }
            writeStringAtomically(File(dir, "channels.json"), jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving channels cache: ${e.message}")
        }
    }

    suspend fun loadHomeChannels(uid: String): List<HomeChannel> = withContext(Dispatchers.IO) {
        val dir = getUserDir(uid) ?: return@withContext emptyList()
        val file = File(dir, "channels.json")
        if (!file.exists()) return@withContext emptyList()
        try {
            val content = file.readText(StandardCharsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<HomeChannel>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id")
                val name = obj.optString("name")
                if (com.example.kchat.feature.home.HomeViewModel.isWorldChat(id, name)) continue
                list.add(
                    HomeChannel(
                        id = id,
                        name = name,
                        lastMessage = if (obj.isNull("lastMessage")) null else obj.optString("lastMessage"),
                        lastTime = if (obj.isNull("lastTime")) null else obj.optLong("lastTime"),
                        lastSenderName = if (obj.isNull("lastSenderName")) null else obj.optString("lastSenderName"),
                        unreadCount = obj.optInt("unreadCount", 0),
                        creatorUid = if (obj.isNull("creatorUid")) null else obj.optString("creatorUid"),
                        imageUrl = if (obj.isNull("imageUrl")) null else obj.optString("imageUrl")
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading channels cache: ${e.message}")
            emptyList()
        }
    }

    // --- Messages Cache ---

    suspend fun saveMessages(uid: String, channelId: String, messages: List<Message>) = withContext(Dispatchers.IO) {
        val dir = getUserDir(uid) ?: return@withContext
        try {
            val toCache = if (messages.size > 100) messages.takeLast(100) else messages
            val jsonArray = JSONArray()
            for (msg in toCache) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("senderId", msg.senderId)
                    put("message", msg.message ?: JSONObject.NULL)
                    put("createdAt", msg.createdAt)
                    put("senderName", msg.senderName)
                    put("senderImage", msg.senderImage ?: JSONObject.NULL)
                    put("imageUrl", msg.imageUrl ?: JSONObject.NULL)
                    put("profileImageUrl", msg.profileImageUrl ?: JSONObject.NULL)
                    put("audioUrl", msg.audioUrl ?: JSONObject.NULL)
                    put("audioDurationMs", msg.audioDurationMs ?: JSONObject.NULL)
                    put("videoUrl", msg.videoUrl ?: JSONObject.NULL)
                    put("videoDurationMs", msg.videoDurationMs ?: JSONObject.NULL)
                    put("fileUrl", msg.fileUrl ?: JSONObject.NULL)
                    put("fileName", msg.fileName ?: JSONObject.NULL)
                    put("fileMimeType", msg.fileMimeType ?: JSONObject.NULL)
                    put("fileSizeBytes", msg.fileSizeBytes ?: JSONObject.NULL)
                    put("isSavedMessage", msg.isSavedMessage)
                    put("savedAt", msg.savedAt ?: JSONObject.NULL)
                    put("replyToMessageId", msg.replyToMessageId ?: JSONObject.NULL)
                    put("replyToSenderName", msg.replyToSenderName ?: JSONObject.NULL)
                    put("replyToMessageText", msg.replyToMessageText ?: JSONObject.NULL)
                    put("mediaEncrypted", msg.mediaEncrypted ?: JSONObject.NULL)
                    put("mediaVersion", msg.mediaVersion ?: JSONObject.NULL)
                    put("groupEpochId", msg.groupEpochId ?: JSONObject.NULL)
                    put("senderKeyIndex", msg.senderKeyIndex ?: JSONObject.NULL)
                    put("textEncrypted", msg.textEncrypted ?: JSONObject.NULL)
                    put("cipherText", msg.cipherText ?: JSONObject.NULL)
                    put("encryptedMetadata", msg.encryptedMetadata ?: JSONObject.NULL)
                    if (msg.encryptedMediaKeys != null) {
                        val emkObj = JSONObject()
                        msg.encryptedMediaKeys.forEach { (k, v) -> emkObj.put(k, v) }
                        put("encryptedMediaKeys", emkObj)
                    }
                    if (msg.readBy != null) {
                        val rbObj = JSONObject()
                        msg.readBy.forEach { (k, v) -> rbObj.put(k, v) }
                        put("readBy", rbObj)
                    }
                    if (msg.deliveredBy != null) {
                        val dbObj = JSONObject()
                        msg.deliveredBy.forEach { (k, v) -> dbObj.put(k, v) }
                        put("deliveredBy", dbObj)
                    }
                }
                jsonArray.put(obj)
            }
            val targetFile = File(dir, safeChannelFileName(channelId))
            writeStringAtomically(targetFile, jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages cache for $channelId: ${e.message}")
        }
    }

    suspend fun loadMessages(uid: String, channelId: String): List<Message> = withContext(Dispatchers.IO) {
        val dir = getUserDir(uid) ?: return@withContext emptyList()
        val file = File(dir, safeChannelFileName(channelId))
        if (!file.exists()) return@withContext emptyList()
        try {
            val content = file.readText(StandardCharsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<Message>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val readByMap = if (obj.has("readBy")) {
                    val rb = obj.getJSONObject("readBy")
                    val map = mutableMapOf<String, Boolean>()
                    rb.keys().forEach { k -> map[k] = rb.optBoolean(k) }
                    map
                } else null

                val deliveredByMap = if (obj.has("deliveredBy")) {
                    val db = obj.getJSONObject("deliveredBy")
                    val map = mutableMapOf<String, Boolean>()
                    db.keys().forEach { k -> map[k] = db.optBoolean(k) }
                    map
                } else null

                val encryptedMediaKeysMap = if (obj.has("encryptedMediaKeys")) {
                    val emk = obj.getJSONObject("encryptedMediaKeys")
                    val map = mutableMapOf<String, String>()
                    emk.keys().forEach { k -> map[k] = emk.optString(k) }
                    map
                } else null

                list.add(
                    Message(
                        id = obj.optString("id"),
                        senderId = obj.optString("senderId"),
                        message = if (obj.isNull("message")) null else obj.optString("message"),
                        createdAt = obj.optLong("createdAt", 0L),
                        senderName = obj.optString("senderName"),
                        senderImage = if (obj.isNull("senderImage")) null else obj.optString("senderImage"),
                        imageUrl = if (obj.isNull("imageUrl")) null else obj.optString("imageUrl"),
                        profileImageUrl = if (obj.isNull("profileImageUrl")) null else obj.optString("profileImageUrl"),
                        audioUrl = if (obj.isNull("audioUrl")) null else obj.optString("audioUrl"),
                        audioDurationMs = if (obj.isNull("audioDurationMs")) null else obj.optLong("audioDurationMs"),
                        videoUrl = if (obj.isNull("videoUrl")) null else obj.optString("videoUrl"),
                        videoDurationMs = if (obj.isNull("videoDurationMs")) null else obj.optLong("videoDurationMs"),
                        fileUrl = if (obj.isNull("fileUrl")) null else obj.optString("fileUrl"),
                        fileName = if (obj.isNull("fileName")) null else obj.optString("fileName"),
                        fileMimeType = if (obj.isNull("fileMimeType")) null else obj.optString("fileMimeType"),
                        fileSizeBytes = if (obj.isNull("fileSizeBytes")) null else obj.optLong("fileSizeBytes"),
                        isSavedMessage = obj.optBoolean("isSavedMessage", false),
                        savedAt = if (obj.isNull("savedAt")) null else obj.optLong("savedAt"),
                        replyToMessageId = if (obj.isNull("replyToMessageId")) null else obj.optString("replyToMessageId"),
                        replyToSenderName = if (obj.isNull("replyToSenderName")) null else obj.optString("replyToSenderName"),
                        replyToMessageText = if (obj.isNull("replyToMessageText")) null else obj.optString("replyToMessageText"),
                        mediaEncrypted = if (obj.isNull("mediaEncrypted")) null else obj.optBoolean("mediaEncrypted"),
                        mediaVersion = if (obj.isNull("mediaVersion")) null else obj.optInt("mediaVersion"),
                        encryptedMediaKeys = encryptedMediaKeysMap,
                        groupEpochId = if (obj.isNull("groupEpochId")) null else obj.optInt("groupEpochId"),
                        senderKeyIndex = if (obj.isNull("senderKeyIndex")) null else obj.optInt("senderKeyIndex"),
                        textEncrypted = if (obj.isNull("textEncrypted")) null else obj.optBoolean("textEncrypted"),
                        cipherText = if (obj.isNull("cipherText")) null else obj.optString("cipherText"),
                        encryptedMetadata = if (obj.isNull("encryptedMetadata")) null else obj.optString("encryptedMetadata"),
                        readBy = readByMap,
                        deliveredBy = deliveredByMap
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages cache for $channelId: ${e.message}")
            emptyList()
        }
    }

    fun clearUserCache(uid: String) {
        try {
            val dir = getUserDir(uid) ?: return
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing user cache for $uid: ${e.message}")
        }
    }

    suspend fun deleteMessages(uid: String, channelId: String) = withContext(Dispatchers.IO) {
        val dir = getUserDir(uid) ?: return@withContext
        val file = File(dir, safeChannelFileName(channelId))
        if (file.exists()) {
            file.delete()
        }
    }

    private fun writeStringAtomically(targetFile: File, data: String) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        FileOutputStream(tempFile).use { fos ->
            OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                writer.write(data)
                writer.flush()
            }
        }
        if (tempFile.exists()) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)
        }
    }
}
