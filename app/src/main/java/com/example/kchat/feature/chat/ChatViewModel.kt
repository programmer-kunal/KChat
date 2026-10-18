package com.example.kchat.feature.chat

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.volley.toolbox.StringRequest
import com.example.kchat.data.cache.LocalDataCache
import com.example.kchat.model.Message
import com.example.kchat.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.Volley
import com.example.kchat.R
import com.example.kchat.SupabaseStorageUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.example.kchat.feature.home.HomeViewModel
import android.widget.Toast
import com.example.kchat.crypto.EncryptedMediaManager
import java.io.File
import java.util.UUID
import androidx.collection.LruCache
import javax.inject.Inject
@HiltViewModel
class ChatViewModel @Inject constructor(
    @param:ApplicationContext val context: Context,
    private val requestQueue: RequestQueue
): ViewModel(){
    private val _messages= MutableStateFlow<List<Message>>(emptyList())
    val message = _messages.asStateFlow()

    private val _isAccessDenied = MutableStateFlow(false)
    val isAccessDenied = _isAccessDenied.asStateFlow()

    private val _isMessagesLoaded = MutableStateFlow(false)
    val isMessagesLoaded = _isMessagesLoaded.asStateFlow()

    private val db= Firebase.database

    private val deletedMessageIds = mutableSetOf<String>()
    private var rawMessages = listOf<Message>()

    private var messagesRef: DatabaseReference? = null
    private var messagesListener: ValueEventListener? = null
    private var deletedMessagesRef: DatabaseReference? = null
    private var deletedMessagesListener: ValueEventListener? = null
    private var groupMembersRef: DatabaseReference? = null
    private var groupMembersListener: ValueEventListener? = null
    private var savedMessagesRef: DatabaseReference? = null
    private var savedMessagesListener: ValueEventListener? = null
    private var cachedSelfMessages = listOf<Message>()
    private var cachedSavedMessages = listOf<Message>()

    private val _groupMemberUids = MutableStateFlow<Set<String>>(emptySet())
    val groupMemberUids = _groupMemberUids.asStateFlow()

    private var friendshipBoundaryRef: DatabaseReference? = null
    private var friendshipBoundaryListener: ValueEventListener? = null
    private var directChatBoundaryTimestamp: Long = 0L
    private var saveMessagesJob: Job? = null

    private fun debouncedSaveMessages(currentUid: String, channelID: String, list: List<Message>) {
        saveMessagesJob?.cancel()
        saveMessagesJob = viewModelScope.launch {
            delay(500L)
            LocalDataCache.saveMessages(currentUid, channelID, list)
        }
    }

    private fun refreshFilteredMessages() {
        _messages.value = rawMessages.filter {
            it.id !in deletedMessageIds && (directChatBoundaryTimestamp <= 0L || it.createdAt >= directChatBoundaryTimestamp)
        }
    }

    private val debugSendMessageCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val debugRequestCounter = java.util.concurrent.atomic.AtomicInteger(0)

    @SuppressLint("SuspiciousIndentation")
    fun sendMessage(
        channelID: String,
        messageText: String?,
        image: String? = null,
        channelName: String? = null,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToMessageText: String? = null,
        audioUrl: String? = null,
        audioDurationMs: Long? = null,
        videoUrl: String? = null,
        videoDurationMs: Long? = null,
        fileUrl: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        fileSizeBytes: Long? = null,
        mediaEncrypted: Boolean? = null,
        mediaVersion: Int? = null,
        encryptedMediaKeys: Map<String, String>? = null,
        groupEpochId: Int? = null,
        senderKeyIndex: Int? = null,
        preGeneratedMessageId: String? = null
    ) {
        val sendMsgCount = debugSendMessageCounter.incrementAndGet()
        val threadName = Thread.currentThread().name
        val timestamp = System.currentTimeMillis()
        Log.d("FCM_DUPLICATE_DEBUG", "[sendMessage ENTRY] Timestamp: $timestamp, ExecCount: $sendMsgCount, channelID: $channelID, Thread: $threadName")

        val currentUser = Firebase.auth.currentUser ?: return
        val uid = currentUser.uid

        val listenerInstance = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {

                val username = snapshot.child("name")
                    .getValue(String::class.java) ?: currentUser.displayName ?: ""

                val profileImage = snapshot.child("imageUrl")
                    .getValue(String::class.java)

                val isGroup = (channelID.startsWith("-") || !channelID.contains("_")) && !channelID.startsWith("self_chat_")
                if (isGroup && channelName.isNullOrEmpty()) {
                    db.reference.child("channel").child(channelID).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(chanSnapshot: DataSnapshot) {
                            val fetchedChannelName = chanSnapshot.getValue(String::class.java) ?: "Group Chat"
                            proceedWithSendMessage(
                                channelID, messageText, image, fetchedChannelName, username, profileImage, uid,
                                replyToMessageId, replyToSenderName, replyToMessageText, audioUrl, audioDurationMs,
                                videoUrl, videoDurationMs, fileUrl, fileName, fileMimeType, fileSizeBytes,
                                mediaEncrypted, mediaVersion, encryptedMediaKeys, groupEpochId, senderKeyIndex, preGeneratedMessageId
                            )
                        }
                        override fun onCancelled(error: DatabaseError) {
                            proceedWithSendMessage(
                                channelID, messageText, image, "Group Chat", username, profileImage, uid,
                                replyToMessageId, replyToSenderName, replyToMessageText, audioUrl, audioDurationMs,
                                videoUrl, videoDurationMs, fileUrl, fileName, fileMimeType, fileSizeBytes,
                                mediaEncrypted, mediaVersion, encryptedMediaKeys, groupEpochId, senderKeyIndex, preGeneratedMessageId
                            )
                        }
                    })
                } else {
                    proceedWithSendMessage(
                        channelID, messageText, image, channelName, username, profileImage, uid,
                        replyToMessageId, replyToSenderName, replyToMessageText, audioUrl, audioDurationMs,
                        videoUrl, videoDurationMs, fileUrl, fileName, fileMimeType, fileSizeBytes,
                        mediaEncrypted, mediaVersion, encryptedMediaKeys, groupEpochId, senderKeyIndex, preGeneratedMessageId
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "User fetch failed")
            }
        }

        db.reference.child("users").child(uid).addListenerForSingleValueEvent(listenerInstance)
    }

    private fun proceedWithSendMessage(
        channelID: String,
        messageText: String?,
        image: String?,
        channelName: String?,
        username: String,
        profileImage: String?,
        uid: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToMessageText: String? = null,
        audioUrl: String? = null,
        audioDurationMs: Long? = null,
        videoUrl: String? = null,
        videoDurationMs: Long? = null,
        fileUrl: String? = null,
        fileName: String? = null,
        fileMimeType: String? = null,
        fileSizeBytes: Long? = null,
        mediaEncrypted: Boolean? = null,
        mediaVersion: Int? = null,
        encryptedMediaKeys: Map<String, String>? = null,
        groupEpochId: Int? = null,
        senderKeyIndex: Int? = null,
        preGeneratedMessageId: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val messageId = preGeneratedMessageId ?: (db.reference.child("messages").child(channelID).push().key ?: UUID.randomUUID().toString())
            val pushRef = db.reference.child("messages").child(channelID).child(messageId)
            val isSelfChat = channelID.startsWith("self_chat_")

            var finalMessageText = messageText
            var finalCipherText: String? = null
            var finalEncryptedMetadata: String? = null
            var finalTextEncrypted: Boolean? = null
            var finalEncryptedMediaKeys = encryptedMediaKeys
            var finalGroupEpochId = groupEpochId
            var finalSenderKeyIndex = senderKeyIndex
            var finalReplyToSenderName = replyToSenderName
            var finalReplyToMessageText = replyToMessageText
            var finalFileName = fileName

            val isTextMessage = messageText != null && image == null && audioUrl == null && videoUrl == null && fileUrl == null

            if (isTextMessage) {
                // Construct metadata JSON if reply info exists
                val metadataObj = JSONObject()
                var hasMetadata = false
                if (!replyToSenderName.isNullOrBlank()) {
                    metadataObj.put("replyToSenderName", replyToSenderName)
                    hasMetadata = true
                }
                if (!replyToMessageText.isNullOrBlank()) {
                    metadataObj.put("replyToMessageText", replyToMessageText)
                    hasMetadata = true
                }
                val metadataJson = if (hasMetadata) metadataObj.toString() else null

                val encResult = EncryptedMediaManager.encryptTextMessage(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    senderUid = uid,
                    plaintext = messageText,
                    metadataJson = metadataJson
                )

                if (encResult == null) {
                    Log.e("ChatViewModel", "Text encryption failed for messageId: $messageId. Aborting send to prevent plaintext leak.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Encryption error: could not send message securely", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                finalTextEncrypted = true
                finalCipherText = encResult.cipherText
                finalEncryptedMetadata = encResult.encryptedMetadata
                finalEncryptedMediaKeys = encResult.encryptedMediaKeys
                finalGroupEpochId = encResult.groupEpochId
                finalSenderKeyIndex = encResult.senderKeyIndex
                // Generic placeholder stored in Firebase Realtime Database
                finalMessageText = "New message"
                if (hasMetadata) {
                    finalReplyToSenderName = null
                    finalReplyToMessageText = null
                }
            }

            val message = Message(
                id = messageId,
                senderId = uid,
                message = finalMessageText,
                createdAt = System.currentTimeMillis(),
                senderName = username,
                senderImage = profileImage,
                imageUrl = image,
                readBy = if (isSelfChat) mapOf(uid to true) else null,
                deliveredBy = if (isSelfChat) mapOf(uid to true) else null,
                replyToMessageId = replyToMessageId,
                replyToSenderName = finalReplyToSenderName,
                replyToMessageText = finalReplyToMessageText,
                audioUrl = audioUrl,
                audioDurationMs = audioDurationMs,
                videoUrl = videoUrl,
                videoDurationMs = videoDurationMs,
                fileUrl = fileUrl,
                fileName = finalFileName,
                fileMimeType = fileMimeType,
                fileSizeBytes = fileSizeBytes,
                isSavedMessage = false,
                mediaEncrypted = mediaEncrypted,
                mediaVersion = mediaVersion,
                encryptedMediaKeys = finalEncryptedMediaKeys,
                groupEpochId = finalGroupEpochId,
                senderKeyIndex = finalSenderKeyIndex,
                textEncrypted = finalTextEncrypted,
                cipherText = finalCipherText,
                encryptedMetadata = finalEncryptedMetadata
            )

            pushRef.setValue(message)
                .addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d("ChatViewModel", "Message saved successfully")
                        if (isSelfChat) {
                            return@addOnCompleteListener
                        }
                        val isDirectChat = channelID.contains("_") && !channelID.startsWith("-")
                        if (isDirectChat) {
                            db.reference.child("chat_hidden").child(uid).child(channelID).removeValue()
                        }
                        val notifyText = if (!image.isNullOrEmpty()) {
                            "Sent an image"
                        } else if (!audioUrl.isNullOrEmpty()) {
                            "🎤 Voice message"
                        } else if (!videoUrl.isNullOrEmpty()) {
                            "🎥 Video message"
                        } else if (!fileUrl.isNullOrEmpty()) {
                            "📎 File"
                        } else {
                            messageText ?: ""
                        }
                        triggerNotificationFlow(channelID, notifyText, username, profileImage, uid, channelName, messageId)
                    }
                }
        }
    }

    private fun triggerNotificationFlow(
        channelID: String,
        messageText: String,
        senderName: String,
        senderImage: String?,
        senderUid: String,
        channelName: String?,
        messageId: String
    ) {
        if (channelID.startsWith("self_chat_")) return
        val threadName = Thread.currentThread().name
        val isDirectChat = channelID.contains("_") && !channelID.startsWith("-")
        val parts = channelID.split("_")
        val canonicalChatId = if (isDirectChat && parts.size == 2) {
            val u1 = parts[0]
            val u2 = parts[1]
            if (u1 < u2) "${u1}_$u2" else "${u2}_$u1"
        } else {
            channelID
        }
        val topic = if (isDirectChat) {
            "direct_$canonicalChatId"
        } else {
            "group_$channelID"
        }

        // Primary: Broadcast via topic (delivers reliably to all subscribed devices via FCM broker)
        sendNotificationRequest(
            channelID = canonicalChatId,
            messageText = messageText,
            senderName = senderName,
            senderImage = senderImage,
            senderUid = senderUid,
            receiverUid = null,
            topic = topic,
            channelName = channelName,
            messageId = messageId
        )

        // For direct chats: also attempt direct receiverUid delivery as dual-delivery fallback
        if (isDirectChat) {
            val receiverUid = parts.firstOrNull { it != senderUid }
            if (receiverUid != null && receiverUid != senderUid) {
                db.reference.child("users").child(receiverUid).child("fcmToken")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val token = snapshot.getValue(String::class.java)
                            sendNotificationRequest(
                                channelID = canonicalChatId,
                                messageText = messageText,
                                senderName = senderName,
                                senderImage = senderImage,
                                senderUid = senderUid,
                                receiverUid = receiverUid,
                                topic = null,
                                channelName = channelName,
                                messageId = messageId,
                                receiverToken = token
                            )
                        }

                        override fun onCancelled(error: DatabaseError) {
                            sendNotificationRequest(
                                channelID = canonicalChatId,
                                messageText = messageText,
                                senderName = senderName,
                                senderImage = senderImage,
                                senderUid = senderUid,
                                receiverUid = receiverUid,
                                topic = null,
                                channelName = channelName,
                                messageId = messageId
                            )
                        }
                    })
            }
        }
    }

    private fun sendNotificationRequest(
        channelID: String,
        messageText: String,
        senderName: String,
        senderImage: String?,
        senderUid: String,
        receiverUid: String? = null,
        topic: String? = null,
        channelName: String? = null,
        messageId: String? = null,
        receiverToken: String? = null
    ) {
        val requestCount = debugRequestCounter.incrementAndGet()
        val threadName = Thread.currentThread().name
        Log.d("FCM_DUPLICATE_DEBUG", "[sendNotificationRequest ENTRY] Timestamp: ${System.currentTimeMillis()}, ExecCount: $requestCount, channelID: $channelID, receiverUid: $receiverUid, topic: $topic, Thread: $threadName")
        val url = "https://kchat-notification-server.onrender.com/sendNotification"
        val jsonBody = JSONObject().apply {
            val isGroup = channelID.startsWith("-") || !channelID.contains("_")
            if (isGroup && !channelName.isNullOrEmpty()) {
                put("title", channelName)
                put("body", "$senderName: $messageText")
            } else {
                put("title", senderName)
                put("body", messageText)
            }
            if (!receiverUid.isNullOrEmpty()) {
                put("receiverUid", receiverUid)
            }
            if (!topic.isNullOrEmpty()) {
                put("topic", topic)
            }
            if (!receiverToken.isNullOrEmpty()) {
                put("token", receiverToken)
            }
            if (!messageId.isNullOrEmpty()) {
                put("messageId", messageId)
            }
            put("channelId", channelID)
            put("senderId", senderUid)
            // Put extra data payload fields expected by FCM custom data or Android parsing
            val dataObj = JSONObject().apply {
                put("channelId", channelID)
                if (!messageId.isNullOrEmpty()) {
                    put("messageId", messageId)
                }
                put("senderName", senderName)
                put("senderImage", senderImage ?: "")
                put("senderId", senderUid)
                put("messageText", messageText)
                if (!channelName.isNullOrEmpty()) {
                    put("channelName", channelName)
                }
            }
            put("data", dataObj)
        }

        val requestBody = jsonBody.toString()

        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            Log.e("ChatViewModel", "Cannot send notification: User not authenticated")
            return
        }

        currentUser.getIdToken(false).addOnSuccessListener { tokenResult ->
            val idToken = tokenResult.token
            val stringRequest = object : StringRequest(
                Method.POST, url,
                Response.Listener { response ->
                    Log.d("FCM_DUPLICATE_DEBUG", "[sendNotificationRequest SUCCESS] Response: $response")
                    Log.d("ChatViewModel", "Notification response: $response")
                },
                Response.ErrorListener { error ->
                    Log.e("FCM_DUPLICATE_DEBUG", "[sendNotificationRequest ERROR] Error: ${error.message}")
                    Log.e("ChatViewModel", "Notification request error: ${error.message}")
                }
            ) {
                override fun getBodyContentType(): String {
                    return "application/json; charset=utf-8"
                }

                override fun getBody(): ByteArray {
                    return requestBody.toByteArray(charset("utf-8"))
                }

                override fun getHeaders(): MutableMap<String, String> {
                    val headers = HashMap<String, String>()
                    if (!idToken.isNullOrEmpty()) {
                        headers["Authorization"] = "Bearer $idToken"
                    }
                    return headers
                }
            }

            stringRequest.retryPolicy = com.android.volley.DefaultRetryPolicy(
                15000,
                0,
                com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )

            requestQueue.add(stringRequest)
        }.addOnFailureListener { error ->
            Log.e("ChatViewModel", "Failed to retrieve Firebase ID token for notification: ${error.message}")
        }
    }

    fun sendImageMessage(
        file: File? = null,
        uri: Uri? = null,
        channelID: String,
        channelName: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToMessageText: String? = null
    ) {
        viewModelScope.launch {
            val rawBytes = try {
                if (file != null && file.exists() && file.length() > 0) {
                    file.readBytes()
                } else if (uri != null) {
                    if (uri.scheme == "file") {
                        uri.path?.let { File(it).takeIf { f -> f.exists() }?.readBytes() }
                            ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                } else null
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error reading image data", e)
                null
            }

            if (rawBytes == null || rawBytes.isEmpty()) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
                try { if (file?.exists() == true) file.delete() } catch (_: Exception) {}
                return@launch
            }

            if (rawBytes.size > EncryptedMediaManager.MAX_MEDIA_SIZE_BYTES) {
                Toast.makeText(context, "Media exceeds 50 MB limit", Toast.LENGTH_SHORT).show()
                try { if (file?.exists() == true) file.delete() } catch (_: Exception) {}
                return@launch
            }

            // Phase 2D: Direct Chat or Group Chat E2EE
            val currentUser = Firebase.auth.currentUser ?: return@launch
            val senderUid = currentUser.uid
            val preGenPushRef = db.reference.child("messages").child(channelID).push()
            val messageId = preGenPushRef.key ?: UUID.randomUUID().toString()

            val isDirectChat = (channelID.contains("_") || channelID.startsWith("self_chat_")) && !channelID.startsWith("-")

            if (isDirectChat) {
                val recipientUid = if (channelID.startsWith("self_chat_")) senderUid else channelID.split("_").firstOrNull { it != senderUid } ?: senderUid
                val result = EncryptedMediaManager.encryptAndUploadDirectMedia(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    mediaType = "IMAGE",
                    recipientUid = recipientUid,
                    senderUid = senderUid,
                    rawBytes = rawBytes,
                    mimeType = "image/jpeg"
                )
                if (result != null) {
                    sendMessage(
                        channelID = channelID,
                        messageText = null,
                        image = result.vaultUrl,
                        channelName = channelName,
                        replyToMessageId = replyToMessageId,
                        replyToSenderName = replyToSenderName,
                        replyToMessageText = replyToMessageText,
                        mediaEncrypted = true,
                        mediaVersion = 1,
                        encryptedMediaKeys = result.encryptedMediaKeys,
                        preGeneratedMessageId = messageId
                    )
                } else {
                    Toast.makeText(context, "Failed to send encrypted image", Toast.LENGTH_SHORT).show()
                }
            } else {
                val result = EncryptedMediaManager.encryptAndUploadGroupMedia(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    mediaType = "IMAGE",
                    senderUid = senderUid,
                    rawBytes = rawBytes,
                    mimeType = "image/jpeg"
                )
                if (result != null) {
                    sendMessage(
                        channelID = channelID,
                        messageText = null,
                        image = result.vaultUrl,
                        channelName = channelName,
                        replyToMessageId = replyToMessageId,
                        replyToSenderName = replyToSenderName,
                        replyToMessageText = replyToMessageText,
                        mediaEncrypted = true,
                        mediaVersion = 1,
                        groupEpochId = result.epochId,
                        senderKeyIndex = result.senderKeyIndex,
                        preGeneratedMessageId = messageId
                    )
                } else {
                    Toast.makeText(context, "Failed to send encrypted group image", Toast.LENGTH_SHORT).show()
                }
            }
            try { if (file?.exists() == true) file.delete() } catch (_: Exception) {}
        }
    }

    fun sendImageMessage(
        uri: Uri,
        channelID: String,
        channelName: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToMessageText: String? = null
    ) = sendImageMessage(
        file = null,
        uri = uri,
        channelID = channelID,
        channelName = channelName,
        replyToMessageId = replyToMessageId,
        replyToSenderName = replyToSenderName,
        replyToMessageText = replyToMessageText
    )

    fun sendAudioMessage(
        file: File,
        durationMs: Long,
        channelID: String,
        channelName: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToMessageText: String? = null
    ) {
        viewModelScope.launch {
            if (file.length() > EncryptedMediaManager.MAX_MEDIA_SIZE_BYTES) {
                Toast.makeText(context, "Media exceeds 50 MB limit", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val currentUser = Firebase.auth.currentUser ?: return@launch
            val senderUid = currentUser.uid
            val preGenPushRef = db.reference.child("messages").child(channelID).push()
            val messageId = preGenPushRef.key ?: UUID.randomUUID().toString()
            val isDirectChat = (channelID.contains("_") || channelID.startsWith("self_chat_")) && !channelID.startsWith("-")

            if (isDirectChat) {
                val recipientUid = if (channelID.startsWith("self_chat_")) senderUid else channelID.split("_").firstOrNull { it != senderUid } ?: senderUid
                val result = EncryptedMediaManager.encryptAndUploadDirectMedia(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    mediaType = "AUDIO",
                    recipientUid = recipientUid,
                    senderUid = senderUid,
                    rawFile = file,
                    mimeType = "audio/mp4"
                )
                if (result != null) {
                    sendMessage(
                        channelID = channelID,
                        messageText = null,
                        image = null,
                        channelName = channelName,
                        replyToMessageId = replyToMessageId,
                        replyToSenderName = replyToSenderName,
                        replyToMessageText = replyToMessageText,
                        audioUrl = result.vaultUrl,
                        audioDurationMs = durationMs,
                        mediaEncrypted = true,
                        mediaVersion = 1,
                        encryptedMediaKeys = result.encryptedMediaKeys,
                        preGeneratedMessageId = messageId
                    )
                } else {
                    Toast.makeText(context, "Failed to send encrypted audio", Toast.LENGTH_SHORT).show()
                }
            } else {
                val result = EncryptedMediaManager.encryptAndUploadGroupMedia(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    mediaType = "AUDIO",
                    senderUid = senderUid,
                    rawFile = file,
                    mimeType = "audio/mp4"
                )
                if (result != null) {
                    sendMessage(
                        channelID = channelID,
                        messageText = null,
                        image = null,
                        channelName = channelName,
                        replyToMessageId = replyToMessageId,
                        replyToSenderName = replyToSenderName,
                        replyToMessageText = replyToMessageText,
                        audioUrl = result.vaultUrl,
                        audioDurationMs = durationMs,
                        mediaEncrypted = true,
                        mediaVersion = 1,
                        groupEpochId = result.epochId,
                        senderKeyIndex = result.senderKeyIndex,
                        preGeneratedMessageId = messageId
                    )
                } else {
                    Toast.makeText(context, "Failed to send encrypted group audio", Toast.LENGTH_SHORT).show()
                }
            }
            val cachedDec = File(context.noBackupFilesDir, "temp_dec_audio_${messageId}.m4a")
            try { file.copyTo(cachedDec, overwrite = true) } catch (_: Exception) {}
            try { if (file.exists()) file.delete() } catch (_: Exception) {}
        }
    }

    fun sendVideoMessage(
        file: File,
        durationMs: Long,
        channelID: String,
        channelName: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToMessageText: String? = null
    ) {
        viewModelScope.launch {
            if (file.length() > EncryptedMediaManager.MAX_MEDIA_SIZE_BYTES) {
                Toast.makeText(context, "Media exceeds 50 MB limit", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val currentUser = Firebase.auth.currentUser ?: return@launch
            val senderUid = currentUser.uid
            val preGenPushRef = db.reference.child("messages").child(channelID).push()
            val messageId = preGenPushRef.key ?: UUID.randomUUID().toString()
            val isDirectChat = (channelID.contains("_") || channelID.startsWith("self_chat_")) && !channelID.startsWith("-")

            if (isDirectChat) {
                val recipientUid = if (channelID.startsWith("self_chat_")) senderUid else channelID.split("_").firstOrNull { it != senderUid } ?: senderUid
                val result = EncryptedMediaManager.encryptAndUploadDirectMedia(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    mediaType = "VIDEO",
                    recipientUid = recipientUid,
                    senderUid = senderUid,
                    rawFile = file,
                    mimeType = "video/mp4"
                )
                if (result != null) {
                    sendMessage(
                        channelID = channelID,
                        messageText = null,
                        image = null,
                        channelName = channelName,
                        replyToMessageId = replyToMessageId,
                        replyToSenderName = replyToSenderName,
                        replyToMessageText = replyToMessageText,
                        videoUrl = result.vaultUrl,
                        videoDurationMs = durationMs,
                        mediaEncrypted = true,
                        mediaVersion = 1,
                        encryptedMediaKeys = result.encryptedMediaKeys,
                        preGeneratedMessageId = messageId
                    )
                } else {
                    Toast.makeText(context, "Failed to send encrypted video", Toast.LENGTH_SHORT).show()
                }
            } else {
                val result = EncryptedMediaManager.encryptAndUploadGroupMedia(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    mediaType = "VIDEO",
                    senderUid = senderUid,
                    rawFile = file,
                    mimeType = "video/mp4"
                )
                if (result != null) {
                    sendMessage(
                        channelID = channelID,
                        messageText = null,
                        image = null,
                        channelName = channelName,
                        replyToMessageId = replyToMessageId,
                        replyToSenderName = replyToSenderName,
                        replyToMessageText = replyToMessageText,
                        videoUrl = result.vaultUrl,
                        videoDurationMs = durationMs,
                        mediaEncrypted = true,
                        mediaVersion = 1,
                        groupEpochId = result.epochId,
                        senderKeyIndex = result.senderKeyIndex,
                        preGeneratedMessageId = messageId
                    )
                } else {
                    Toast.makeText(context, "Failed to send encrypted group video", Toast.LENGTH_SHORT).show()
                }
            }
            val cachedDec = File(context.noBackupFilesDir, "temp_dec_video_${messageId}.mp4")
            try { file.copyTo(cachedDec, overwrite = true) } catch (_: Exception) {}
            try { if (file.exists()) file.delete() } catch (_: Exception) {}
        }
    }

    fun sendFileMessage(
        file: File,
        fileName: String,
        mimeType: String,
        fileSizeBytes: Long,
        channelID: String,
        channelName: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToMessageText: String? = null
    ) {
        viewModelScope.launch {
            if (file.length() > EncryptedMediaManager.MAX_MEDIA_SIZE_BYTES) {
                Toast.makeText(context, "Media exceeds 50 MB limit", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val currentUser = Firebase.auth.currentUser ?: return@launch
            val senderUid = currentUser.uid
            val preGenPushRef = db.reference.child("messages").child(channelID).push()
            val messageId = preGenPushRef.key ?: UUID.randomUUID().toString()
            val isDirectChat = (channelID.contains("_") || channelID.startsWith("self_chat_")) && !channelID.startsWith("-")

            if (isDirectChat) {
                val recipientUid = if (channelID.startsWith("self_chat_")) senderUid else channelID.split("_").firstOrNull { it != senderUid } ?: senderUid
                val result = EncryptedMediaManager.encryptAndUploadDirectMedia(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    mediaType = "DOCUMENT",
                    recipientUid = recipientUid,
                    senderUid = senderUid,
                    rawFile = file,
                    mimeType = mimeType
                )
                if (result != null) {
                    sendMessage(
                        channelID = channelID,
                        messageText = null,
                        image = null,
                        channelName = channelName,
                        replyToMessageId = replyToMessageId,
                        replyToSenderName = replyToSenderName,
                        replyToMessageText = replyToMessageText,
                        fileUrl = result.vaultUrl,
                        fileName = fileName,
                        fileMimeType = mimeType,
                        fileSizeBytes = fileSizeBytes,
                        mediaEncrypted = true,
                        mediaVersion = 1,
                        encryptedMediaKeys = result.encryptedMediaKeys,
                        preGeneratedMessageId = messageId
                    )
                } else {
                    Toast.makeText(context, "Failed to send encrypted document", Toast.LENGTH_SHORT).show()
                }
            } else {
                val result = EncryptedMediaManager.encryptAndUploadGroupMedia(
                    context = context,
                    channelId = channelID,
                    messageId = messageId,
                    mediaType = "DOCUMENT",
                    senderUid = senderUid,
                    rawFile = file,
                    mimeType = mimeType
                )
                if (result != null) {
                    sendMessage(
                        channelID = channelID,
                        messageText = null,
                        image = null,
                        channelName = channelName,
                        replyToMessageId = replyToMessageId,
                        replyToSenderName = replyToSenderName,
                        replyToMessageText = replyToMessageText,
                        fileUrl = result.vaultUrl,
                        fileName = fileName,
                        fileMimeType = mimeType,
                        fileSizeBytes = fileSizeBytes,
                        mediaEncrypted = true,
                        mediaVersion = 1,
                        groupEpochId = result.epochId,
                        senderKeyIndex = result.senderKeyIndex,
                        preGeneratedMessageId = messageId
                    )
                } else {
                    Toast.makeText(context, "Failed to send encrypted group document", Toast.LENGTH_SHORT).show()
                }
            }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val cachedDec = File(context.noBackupFilesDir, "temp_dec_doc_${messageId}_$safeName")
            try { file.copyTo(cachedDec, overwrite = true) } catch (_: Exception) {}
            try { if (file.exists()) file.delete() } catch (_: Exception) {}
        }
    }

    fun listenForMessages(channelID: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        _isMessagesLoaded.value = false

        val cached = memoryMessageCache.get(channelID)
        if (!cached.isNullOrEmpty()) {
            rawMessages = cached
            refreshFilteredMessages()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val diskCached = LocalDataCache.loadMessages(currentUid, channelID)
                if (diskCached.isNotEmpty() && rawMessages.isEmpty()) {
                    val decryptedDisk = diskCached.map { msg ->
                        if (msg.textEncrypted == true) {
                            EncryptedMediaManager.decryptTextMessage(context, channelID, msg)
                        } else {
                            msg
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (rawMessages.isEmpty()) {
                            rawMessages = decryptedDisk
                            memoryMessageCache.put(channelID, decryptedDisk)
                            refreshFilteredMessages()
                        }
                    }
                }
            }
        }

        deletedMessagesRef?.let { ref ->
            deletedMessagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        val delRef = db.getReference("deleted_messages").child(currentUid).child(channelID)
        deletedMessagesRef = delRef
        val delListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                deletedMessageIds.clear()
                snapshot.children.forEach {
                    it.key?.let { msgId -> deletedMessageIds.add(msgId) }
                }
                refreshFilteredMessages()
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Deleted messages load failed")
            }
        }
        deletedMessagesListener = delListener
        delRef.addValueEventListener(delListener)

        groupMembersRef?.let { ref ->
            groupMembersListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        groupMembersRef = null
        groupMembersListener = null

        savedMessagesRef?.let { ref ->
            savedMessagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        savedMessagesRef = null
        savedMessagesListener = null
        cachedSelfMessages = emptyList()
        cachedSavedMessages = emptyList()

        friendshipBoundaryRef?.let { ref ->
            friendshipBoundaryListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        friendshipBoundaryRef = null
        friendshipBoundaryListener = null
        directChatBoundaryTimestamp = 0L

        val isSelfChat = channelID.startsWith("self_chat_")
        val isDirectChat = !isSelfChat && channelID.contains("_") && !channelID.startsWith("-")
        if (isSelfChat) {
            _groupMemberUids.value = setOf(currentUid)
            _isAccessDenied.value = false
            attachSelfAndSavedMessagesListener(channelID, currentUid)
        } else if (isDirectChat) {
            val parts = channelID.split("_").toSet()
            _groupMemberUids.value = parts
            val otherUid = channelID.split("_").firstOrNull { it != currentUid } ?: ""
            if (otherUid.isNotEmpty()) {
                val boundaryRef = db.reference.child("friends").child(currentUid).child(otherUid)
                friendshipBoundaryRef = boundaryRef
                val bListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        directChatBoundaryTimestamp = when (val v = snapshot.value) {
                            is Number -> v.toLong()
                            else -> 0L
                        }
                        refreshFilteredMessages()
                    }
                    override fun onCancelled(error: DatabaseError) {}
                }
                friendshipBoundaryListener = bListener
                boundaryRef.addValueEventListener(bListener)
            }
            subscribeForNotification(channelID)
            attachMessageListener(channelID)
        } else if (channelID == HomeViewModel.WORLD_CHAT_ID) {
            _isAccessDenied.value = true
            _groupMemberUids.value = emptySet()
            return
        } else {
            val channelsRef = db.reference.child("channels").child(channelID).child("users")
            val creatorRef = db.reference.child("channel_creator").child(channelID)
            groupMembersRef = channelsRef
            val memberListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val memberUids = snapshot.children
                        .mapNotNull { it.key }
                        .filter { it.isNotBlank() }
                        .toMutableSet()

                    creatorRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(creatorSnap: DataSnapshot) {
                            val creatorUid = creatorSnap.getValue(String::class.java)
                            if (!creatorUid.isNullOrBlank()) {
                                memberUids.add(creatorUid)
                            }
                            _groupMemberUids.value = memberUids
                        }

                        override fun onCancelled(error: DatabaseError) {
                            _groupMemberUids.value = memberUids
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    _groupMemberUids.value = emptySet()
                }
            }
            groupMembersListener = memberListener
            channelsRef.addValueEventListener(memberListener)

            verifyCustomGroupMembership(channelID) { isMember ->
                if (isMember) {
                    _isAccessDenied.value = false
                    subscribeForNotification(channelID)
                    attachMessageListener(channelID)
                } else {
                    Log.w("ChatViewModel", "Access denied: User is not a member of custom group $channelID")
                    _isAccessDenied.value = true
                }
            }
        }
    }

    private fun attachMessageListener(channelID: String) {
        messagesRef?.let { ref ->
            messagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        val ref = db.getReference("messages").child(channelID)
        messagesRef = ref
        val currentUid = Firebase.auth.currentUser?.uid
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Message>()

                snapshot.children.forEach { data ->
                    val message = data.getValue(Message::class.java)
                    val key = data.key
                    val resolved = if (message != null && message.id.isEmpty() && key != null) {
                        message.copy(id = key)
                    } else {
                        message
                    }
                    resolved?.let {
                        list.add(it)
                        if (currentUid != null && it.senderId != currentUid && it.deliveredBy?.get(currentUid) != true) {
                            val now = System.currentTimeMillis()
                            data.ref.child("deliveredBy").child(currentUid).setValue(true)
                            data.ref.child("deliveredAt").child(currentUid).setValue(now)
                        }
                    }
                }

                viewModelScope.launch(Dispatchers.IO) {
                    val decryptedList = list.map { msg ->
                        if (msg.textEncrypted == true) {
                            EncryptedMediaManager.decryptTextMessage(context, channelID, msg)
                        } else {
                            msg
                        }
                    }

                    withContext(Dispatchers.Main) {
                        rawMessages = decryptedList
                        val toCache = if (directChatBoundaryTimestamp > 0L) {
                            decryptedList.filter { it.createdAt >= directChatBoundaryTimestamp }
                        } else {
                            decryptedList
                        }
                        memoryMessageCache.put(channelID, toCache.takeLast(100))
                        refreshFilteredMessages()
                        _isMessagesLoaded.value = true
                        if (currentUid != null) {
                            debouncedSaveMessages(currentUid, channelID, toCache)
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Message load failed: ${error.message}")
                _isMessagesLoaded.value = true
            }
        }
        messagesListener = listener
        ref.orderByChild("createdAt").addValueEventListener(listener)
    }

    private fun attachSelfAndSavedMessagesListener(channelID: String, currentUid: String) {
        messagesRef?.let { ref ->
            messagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        savedMessagesRef?.let { ref ->
            savedMessagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }

        val selfRef = db.getReference("messages").child(channelID)
        messagesRef = selfRef
        val selfListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Message>()
                snapshot.children.forEach { data ->
                    val message = data.getValue(Message::class.java)
                    val key = data.key
                    val resolved = if (message != null && message.id.isEmpty() && key != null) {
                        message.copy(id = key)
                    } else {
                        message
                    }
                    resolved?.let { list.add(it) }
                }
                cachedSelfMessages = list
                updateSelfChatCombinedMessages(channelID)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Self messages load failed: ${error.message}")
                _isMessagesLoaded.value = true
            }
        }
        messagesListener = selfListener
        selfRef.orderByChild("createdAt").addValueEventListener(selfListener)

        val sRef = db.getReference("saved_messages").child(currentUid)
        savedMessagesRef = sRef
        val sListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Message>()
                snapshot.children.forEach { data ->
                    val message = data.getValue(Message::class.java)
                    val key = data.key
                    val resolved = if (message != null && message.id.isEmpty() && key != null) {
                        message.copy(id = key)
                    } else {
                        message
                    }
                    resolved?.let {
                        list.add(it.copy(isSavedMessage = true))
                    }
                }
                cachedSavedMessages = list
                updateSelfChatCombinedMessages(channelID)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Saved messages load failed: ${error.message}")
                _isMessagesLoaded.value = true
            }
        }
        savedMessagesListener = sListener
        sRef.addValueEventListener(sListener)
    }

    private fun updateSelfChatCombinedMessages(channelID: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val selfMsgIds = cachedSelfMessages.map { it.id }.toSet()

            val filteredSavedMessages = cachedSavedMessages.filter { savedMsg ->
                val origChatId = savedMsg.originalChatId ?: ""
                val origMsgId = savedMsg.originalMessageId ?: ""
                val isDuplicateOfSelfChat = (origChatId == channelID || origChatId.startsWith("self_chat_")) && origMsgId in selfMsgIds
                !isDuplicateOfSelfChat
            }

            val combined = (cachedSelfMessages + filteredSavedMessages).sortedBy { msg ->
                if (msg.isSavedMessage && (msg.savedAt ?: 0L) > 0L) {
                    msg.savedAt ?: msg.createdAt
                } else {
                    msg.createdAt
                }
            }

            val decryptedCombined = combined.map { msg ->
                if (msg.textEncrypted == true) {
                    val targetChatId = if (msg.isSavedMessage && !msg.originalChatId.isNullOrBlank()) msg.originalChatId else channelID
                    EncryptedMediaManager.decryptTextMessage(context, targetChatId, msg)
                } else {
                    msg
                }
            }

            withContext(Dispatchers.Main) {
                rawMessages = decryptedCombined
                memoryMessageCache.put(channelID, decryptedCombined.takeLast(100))
                refreshFilteredMessages()
                _isMessagesLoaded.value = true
                val currentUid = Firebase.auth.currentUser?.uid
                if (currentUid != null) {
                    debouncedSaveMessages(currentUid, channelID, decryptedCombined)
                }
            }
        }
    }

    fun getGroupCallInvitees(
        channelID: String,
        callback: (List<com.zegocloud.uikit.service.defines.ZegoUIKitUser>) -> Unit
    ) {
        getGroupCallInviteesWithCount(channelID) { invitees, _ ->
            callback.invoke(invitees)
        }
    }

    fun getGroupCallInviteesWithCount(
        channelID: String,
        callback: (List<com.zegocloud.uikit.service.defines.ZegoUIKitUser>, Int) -> Unit
    ) {
        val currentUid = Firebase.auth.currentUser?.uid ?: ""
        val channelsRef = db.reference.child("channels").child(channelID).child("users")
        val creatorRef = db.reference.child("channel_creator").child(channelID)

        channelsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val memberUids = snapshot.children
                    .mapNotNull { it.key }
                    .filter { it.isNotBlank() && it != currentUid }
                    .toMutableSet()

                creatorRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(creatorSnap: DataSnapshot) {
                        val creatorUid = creatorSnap.getValue(String::class.java)
                        if (!creatorUid.isNullOrBlank() && creatorUid != currentUid) {
                            memberUids.add(creatorUid)
                        }

                        val totalGroupMembers = memberUids.size + 1

                        if (memberUids.isEmpty()) {
                            callback.invoke(emptyList(), totalGroupMembers)
                            return
                        }

                        val invitees = mutableListOf<com.zegocloud.uikit.service.defines.ZegoUIKitUser>()
                        var pending = memberUids.size

                        memberUids.forEach { uid ->
                            db.reference.child("users").child(uid)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(userSnap: DataSnapshot) {
                                        val email = userSnap.child("email").getValue(String::class.java)?.takeIf { it.isNotBlank() } ?: "user_${uid.take(5)}@kchat.com"
                                        val name = userSnap.child("name").getValue(String::class.java)?.takeIf { it.isNotBlank() } ?: email
                                        invitees.add(
                                            com.zegocloud.uikit.service.defines.ZegoUIKitUser(email.take(32), name)
                                        )
                                        pending--
                                        if (pending <= 0) {
                                            callback.invoke(invitees, totalGroupMembers)
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        pending--
                                        if (pending <= 0) {
                                            callback.invoke(invitees, totalGroupMembers)
                                        }
                                    }
                                })
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        val totalGroupMembers = memberUids.size + 1
                        if (memberUids.isEmpty()) {
                            callback.invoke(emptyList(), totalGroupMembers)
                            return
                        }
                        val invitees = mutableListOf<com.zegocloud.uikit.service.defines.ZegoUIKitUser>()
                        var pending = memberUids.size
                        memberUids.forEach { uid ->
                            db.reference.child("users").child(uid)
                                .addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(userSnap: DataSnapshot) {
                                        val email = userSnap.child("email").getValue(String::class.java)
                                        val name = userSnap.child("name").getValue(String::class.java) ?: email ?: ""
                                        if (!email.isNullOrBlank()) {
                                            invitees.add(com.zegocloud.uikit.service.defines.ZegoUIKitUser(email, name))
                                        }
                                        pending--
                                        if (pending <= 0) callback.invoke(invitees, totalGroupMembers)
                                    }
                                    override fun onCancelled(error: DatabaseError) {
                                        pending--
                                        if (pending <= 0) callback.invoke(invitees, totalGroupMembers)
                                    }
                                })
                        }
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                callback.invoke(emptyList(), 0)
            }
        })
    }

    fun getAllUserEmails(channelID: String, callback: (List<String>) -> Unit) {
        getGroupCallInvitees(channelID) { invitees ->
            callback.invoke(invitees.map { it.userID })
        }
    }

    fun registerUserIdtoChannel(channelID: String, onRegistered: (() -> Unit)? = null) {
        if (channelID != HomeViewModel.WORLD_CHAT_ID) {
            onRegistered?.invoke()
            return
        }
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            onRegistered?.invoke()
            return
        }
        val currentUid = currentUser.uid
        val ref = db.reference.child("channels").child(channelID).child("users").child(currentUid)
        ref.addListenerForSingleValueEvent(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        ref.setValue(true).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                onRegistered?.invoke()
                            } else {
                                Log.e("ChatViewModel", "Failed to register user to channel", task.exception)
                            }
                        }
                    } else {
                        onRegistered?.invoke()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatViewModel", "Membership check cancelled: ${error.message}")
                }
            }
        )
    }

    fun verifyCustomGroupMembership(channelID: String, onResult: (Boolean) -> Unit) {
        val currentUid = Firebase.auth.currentUser?.uid
        if (currentUid == null) {
            onResult(false)
            return
        }
        db.reference.child("channel_creator").child(channelID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(creatorSnapshot: DataSnapshot) {
                    val creatorUid = creatorSnapshot.getValue(String::class.java)
                    if (creatorUid == currentUid) {
                        onResult(true)
                    } else {
                        db.reference.child("channels").child(channelID).child("users").child(currentUid)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(userSnapshot: DataSnapshot) {
                                    onResult(userSnapshot.exists())
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    onResult(false)
                                }
                            })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onResult(false)
                }
            })
    }


    private fun subscribeForNotification(channelID: String) {
        if (channelID.startsWith("self_chat_")) return

        val isDirect = channelID.contains("_") && !channelID.startsWith("-")
        val topic = if (isDirect) {
            val parts = channelID.split("_")
            if (parts.size == 2) {
                val u1 = parts[0]
                val u2 = parts[1]
                val canonicalChatId = if (u1 < u2) "${u1}_$u2" else "${u2}_$u1"
                "direct_$canonicalChatId"
            } else {
                "direct_$channelID"
            }
        } else {
            "group_$channelID"
        }

        FirebaseMessaging.getInstance()
            .subscribeToTopic(topic)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("ChatViewModel", "Subscribed to topic: $topic")
                } else {
                    Log.d("ChatViewModel", "Failed to subscribe to topic: $topic")
                }
            }
    }

    fun markMessagesAsRead(channelID: String) {
        if (channelID.startsWith("self_chat_") || channelID == HomeViewModel.WORLD_CHAT_ID) return
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        db.reference.child("messages")
            .child(channelID)
            .get()
            .addOnSuccessListener { snapshot ->

                snapshot.children.forEach { data ->
                    val message = data.getValue(Message::class.java)

                    // If message is not mine
                    if (message?.senderId != currentUid) {
                        val now = System.currentTimeMillis()
                        if (message?.deliveredBy?.get(currentUid) != true) {
                            data.ref.child("deliveredBy")
                                .child(currentUid)
                                .setValue(true)
                            data.ref.child("deliveredAt")
                                .child(currentUid)
                                .setValue(now)
                        }
                        if (message?.readBy?.get(currentUid) != true) {
                            data.ref.child("readBy")
                                .child(currentUid)
                                .setValue(true)
                            data.ref.child("readAt")
                                .child(currentUid)
                                .setValue(now)
                        }
                    }
                }
            }
    }

    fun getGroupMemberUids(channelID: String, callback: (Set<String>) -> Unit) {
        if (channelID.startsWith("self_chat_")) {
            val currentUid = Firebase.auth.currentUser?.uid ?: ""
            callback(setOf(currentUid))
            return
        }
        if (channelID == HomeViewModel.WORLD_CHAT_ID) {
            callback(emptySet())
            return
        }
        val isDirectChat = channelID.contains("_") && !channelID.startsWith("-")
        if (isDirectChat) {
            callback(channelID.split("_").toSet())
            return
        }

        val channelsRef = db.reference.child("channels").child(channelID).child("users")
        val creatorRef = db.reference.child("channel_creator").child(channelID)

        channelsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val memberUids = snapshot.children
                    .mapNotNull { it.key }
                    .filter { it.isNotBlank() }
                    .toMutableSet()

                creatorRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(creatorSnap: DataSnapshot) {
                        val creatorUid = creatorSnap.getValue(String::class.java)
                        if (!creatorUid.isNullOrBlank()) {
                            memberUids.add(creatorUid)
                        }
                        callback(memberUids)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        callback(memberUids)
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                callback(emptySet())
            }
        })
    }
    // ================= FRIEND REQUEST SYSTEM =================

    fun sendFriendRequest(receiverUid: String, receiverName: String) {
        val currentUser = Firebase.auth.currentUser ?: return
        val senderUid = currentUser.uid

        db.reference
            .child("friend_requests")
            .child(receiverUid)
            .child(senderUid)
            .setValue(currentUser.displayName ?: "")
    }

    fun acceptFriendRequest(senderUid: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()

        db.reference.child("friends")
            .child(currentUid)
            .child(senderUid)
            .setValue(now)

        db.reference.child("friends")
            .child(senderUid)
            .child(currentUid)
            .setValue(now)

        db.reference.child("friend_requests")
            .child(currentUid)
            .child(senderUid)
            .removeValue()
    }

    fun rejectFriendRequest(senderUid: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        db.reference
            .child("friend_requests")
            .child(currentUid)
            .child(senderUid)
            .removeValue()
    }

    fun deleteMessage(channelID: String, messageId: String) {
        if (channelID.startsWith("self_chat_")) {
            val currentUid = Firebase.auth.currentUser?.uid ?: return
            db.reference.child("messages").child(channelID).orderByChild("id").equalTo(messageId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        snapshot.children.forEach { child -> child.ref.removeValue() }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            db.reference.child("saved_messages").child(currentUid).child(messageId).removeValue()
            db.reference.child("saved_messages").child(currentUid).orderByChild("originalMessageId").equalTo(messageId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        snapshot.children.forEach { child -> child.ref.removeValue() }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            return
        }

        db.reference.child("messages").child(channelID).orderByChild("id").equalTo(messageId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { child ->
                        child.ref.removeValue()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun deleteMessageForMe(channelID: String, messageId: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        if (channelID.startsWith("self_chat_")) {
            deleteMessage(channelID, messageId)
            return
        }
        db.reference.child("deleted_messages")
            .child(currentUid)
            .child(channelID)
            .child(messageId)
            .setValue(true)
    }

    fun deleteSavedMessage(savedMessageId: String, onComplete: () -> Unit = {}) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        db.reference.child("saved_messages")
            .child(currentUid)
            .child(savedMessageId)
            .removeValue()
            .addOnCompleteListener { onComplete() }
    }

    fun unstarSavedMessages(messages: List<Message>, onComplete: (Int) -> Unit = {}) {
        val currentUid = Firebase.auth.currentUser?.uid ?: run {
            onComplete(0)
            return
        }
        val savedMessagesToUnstar = messages.filter { it.isSavedMessage }
        if (savedMessagesToUnstar.isEmpty()) {
            onComplete(0)
            return
        }
        val updates = mutableMapOf<String, Any?>()
        savedMessagesToUnstar.forEach { msg ->
            updates[msg.id] = null
        }
        db.reference.child("saved_messages").child(currentUid).updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(savedMessagesToUnstar.size)
                } else {
                    onComplete(0)
                }
            }
    }

    fun saveMessagesToSavedMessages(
        messagesToSave: List<Message>,
        channelID: String,
        channelName: String,
        onComplete: (Int) -> Unit
    ) {
        val currentUid = Firebase.auth.currentUser?.uid ?: run {
            onComplete(0)
            return
        }
        if (messagesToSave.isEmpty()) {
            onComplete(0)
            return
        }

        val savedAt = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any?>()

        messagesToSave.forEach { msg ->
            val origMsgId = msg.originalMessageId?.takeIf { it.isNotBlank() } ?: msg.id
            val origChatId = msg.originalChatId?.takeIf { it.isNotBlank() } ?: channelID
            val origChatName = msg.originalChatName?.takeIf { it.isNotBlank() } ?: channelName
            val origSenderId = msg.originalSenderId?.takeIf { it.isNotBlank() } ?: msg.senderId
            val origSenderName = msg.originalSenderName?.takeIf { it.isNotBlank() } ?: msg.senderName

            val safeKey = "${origChatId}_${origMsgId}".replace(Regex("[.#$\\[\\]/]"), "_")

            val savedMsg = msg.copy(
                id = safeKey,
                isSavedMessage = true,
                savedAt = savedAt,
                originalMessageId = origMsgId,
                originalChatId = origChatId,
                originalChatName = origChatName,
                originalSenderId = origSenderId,
                originalSenderName = origSenderName
            )
            updates[safeKey] = savedMsg
        }

        db.reference.child("saved_messages").child(currentUid).updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(messagesToSave.size)
                } else {
                    onComplete(0)
                }
            }
    }

    // ================= GROUP MANAGEMENT & USER INFO =================

    fun getGroupMembers(channelID: String, callback: (List<User>) -> Unit) {
        val membersRef = db.reference.child("channels").child(channelID).child("users")
        val creatorRef = db.reference.child("channel_creator").child(channelID)

        membersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val memberUids = snapshot.children.mapNotNull { it.key }.toMutableSet()
                creatorRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(creatorSnap: DataSnapshot) {
                        val creatorUid = creatorSnap.getValue(String::class.java)
                        if (!creatorUid.isNullOrBlank()) {
                            memberUids.add(creatorUid)
                        }
                        if (memberUids.isEmpty()) {
                            callback(emptyList())
                            return
                        }
                        val usersRef = db.reference.child("users")
                        usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(usersSnapshot: DataSnapshot) {
                                val memberList = mutableListOf<User>()
                                for (uid in memberUids) {
                                    val userSnap = usersSnapshot.child(uid)
                                    val name = userSnap.child("name").getValue(String::class.java)
                                        ?: userSnap.child("email").getValue(String::class.java)
                                        ?: "User"
                                    val email = userSnap.child("email").getValue(String::class.java) ?: ""
                                    val imageUrl = userSnap.child("imageUrl").getValue(String::class.java)
                                    val about = userSnap.child("about").getValue(String::class.java)
                                    memberList.add(
                                        User(
                                            uid = uid,
                                            name = name,
                                            email = email,
                                            profileImage = imageUrl,
                                            about = about
                                        )
                                    )
                                }
                                callback(memberList)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                callback(emptyList())
                            }
                        })
                    }

                    override fun onCancelled(error: DatabaseError) {
                        callback(emptyList())
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                callback(emptyList())
            }
        })
    }

    fun getUserProfile(uid: String, callback: (User?) -> Unit) {
        db.reference.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val name = snapshot.child("name").getValue(String::class.java)
                    ?: snapshot.child("email").getValue(String::class.java)
                    ?: "User"
                val email = snapshot.child("email").getValue(String::class.java) ?: ""
                val imageUrl = snapshot.child("imageUrl").getValue(String::class.java)
                val about = snapshot.child("about").getValue(String::class.java)
                callback(
                    User(
                        uid = uid,
                        name = name,
                        email = email,
                        profileImage = imageUrl,
                        about = about
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                callback(null)
            }
        })
    }

    fun getChannelCreator(channelID: String, callback: (String?) -> Unit) {
        db.reference.child("channel_creator").child(channelID)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val creatorUid = snapshot.getValue(String::class.java)
                    callback(creatorUid)
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(null)
                }
            })
    }

    fun removeMemberFromGroup(channelID: String, memberUid: String, callback: (Boolean, String?) -> Unit) {
        val currentUid = Firebase.auth.currentUser?.uid
        if (currentUid == null) {
            callback(false, "User not authenticated")
            return
        }
        if (channelID == HomeViewModel.WORLD_CHAT_ID) {
            callback(false, "Cannot remove members from World Chat")
            return
        }

        getChannelCreator(channelID) { creatorUid ->
            if (creatorUid != currentUid) {
                callback(false, "Only the group creator can remove members")
                return@getChannelCreator
            }
            if (memberUid == creatorUid) {
                callback(false, "Creator cannot be removed from the group")
                return@getChannelCreator
            }

            db.reference.child("channels").child(channelID).child("users").child(memberUid)
                .removeValue()
                .addOnSuccessListener {
                    callback(true, null)
                }
                .addOnFailureListener { error ->
                    callback(false, error.message)
                }
        }
    }

    fun updateGroupImage(channelID: String, imageUrl: String, callback: (Boolean, String?) -> Unit) {
        val currentUid = Firebase.auth.currentUser?.uid
        if (currentUid == null) {
            callback(false, "User not authenticated")
            return
        }
        if (channelID == HomeViewModel.WORLD_CHAT_ID) {
            callback(false, "World Chat photo cannot be modified")
            return
        }

        db.reference.child("channels").child(channelID).child("users").child(currentUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        db.reference.child("channel_image").child(channelID).setValue(imageUrl)
                            .addOnSuccessListener {
                                callback(true, null)
                            }
                            .addOnFailureListener { error ->
                                callback(false, error.message)
                            }
                    } else {
                        callback(false, "Only group members can update the group photo")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(false, error.message)
                }
            })
    }

    fun getAllUsers(callback: (List<User>) -> Unit) {
        db.reference.child("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<User>()
                snapshot.children.forEach { child ->
                    val uid = child.key ?: return@forEach
                    val name = child.child("name").getValue(String::class.java)
                        ?: child.child("email").getValue(String::class.java)
                        ?: "User"
                    val email = child.child("email").getValue(String::class.java) ?: ""
                    val imageUrl = child.child("imageUrl").getValue(String::class.java)
                    val about = child.child("about").getValue(String::class.java)
                    list.add(
                        User(
                            uid = uid,
                            name = name,
                            email = email,
                            profileImage = imageUrl,
                            about = about
                        )
                    )
                }
                callback(list)
            }

            override fun onCancelled(error: DatabaseError) {
                callback(emptyList())
            }
        })
    }

    fun getFriendsAndRequests(callback: (friendIds: Set<String>, sentRequestUids: Set<String>) -> Unit) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return callback(emptySet(), emptySet())

        db.reference.child("friends").child(currentUid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(friendsSnap: DataSnapshot) {
                val friendIds = friendsSnap.children.mapNotNull { it.key }.toSet()

                db.reference.child("friend_requests").addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(reqsSnap: DataSnapshot) {
                        val sentUids = mutableSetOf<String>()
                        reqsSnap.children.forEach { receiverSnap ->
                            if (receiverSnap.hasChild(currentUid)) {
                                receiverSnap.key?.let { sentUids.add(it) }
                            }
                        }
                        callback(friendIds, sentUids)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        callback(friendIds, emptySet())
                    }
                })
            }

            override fun onCancelled(error: DatabaseError) {
                callback(emptySet(), emptySet())
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        messagesRef?.let { ref ->
            messagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        messagesListener = null
        messagesRef = null

        deletedMessagesRef?.let { ref ->
            deletedMessagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        deletedMessagesListener = null
        deletedMessagesRef = null

        groupMembersRef?.let { ref ->
            groupMembersListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        groupMembersListener = null
        groupMembersRef = null

        savedMessagesRef?.let { ref ->
            savedMessagesListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        savedMessagesListener = null
        savedMessagesRef = null

        friendshipBoundaryRef?.let { ref ->
            friendshipBoundaryListener?.let { listener ->
                ref.removeEventListener(listener)
            }
        }
        friendshipBoundaryListener = null
        friendshipBoundaryRef = null
    }

    companion object {
        private val memoryMessageCache = LruCache<String, List<Message>>(50)

        fun clearMemoryCache(channelId: String? = null) {
            if (channelId != null) {
                memoryMessageCache.remove(channelId)
            } else {
                memoryMessageCache.evictAll()
            }
        }
    }

}
