package com.example.kchat.feature.chat

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.volley.toolbox.StringRequest
import com.example.kchat.model.Message
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
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
@HiltViewModel
class ChatViewModel @Inject constructor(
    @param:ApplicationContext val context: Context,
    private val requestQueue: RequestQueue
): ViewModel(){
    private val _messages= MutableStateFlow<List<Message>>(emptyList())
    val message = _messages.asStateFlow()
    private val db= Firebase.database

    private val deletedMessageIds = mutableSetOf<String>()
    private var rawMessages = listOf<Message>()

    private var messagesRef: DatabaseReference? = null
    private var messagesListener: ValueEventListener? = null
    private var deletedMessagesRef: DatabaseReference? = null
    private var deletedMessagesListener: ValueEventListener? = null

    private fun refreshFilteredMessages() {
        _messages.value = rawMessages.filter { it.id !in deletedMessageIds }
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
        fileSizeBytes: Long? = null
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

                if (!channelID.contains("_") && channelName.isNullOrEmpty()) {
                    db.reference.child("channel").child(channelID).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(chanSnapshot: DataSnapshot) {
                            val fetchedChannelName = chanSnapshot.getValue(String::class.java) ?: "Group Chat"
                            proceedWithSendMessage(
                                channelID, messageText, image, fetchedChannelName, username, profileImage, uid,
                                replyToMessageId, replyToSenderName, replyToMessageText, audioUrl, audioDurationMs,
                                videoUrl, videoDurationMs, fileUrl, fileName, fileMimeType, fileSizeBytes
                            )
                        }
                        override fun onCancelled(error: DatabaseError) {
                            proceedWithSendMessage(
                                channelID, messageText, image, "Group Chat", username, profileImage, uid,
                                replyToMessageId, replyToSenderName, replyToMessageText, audioUrl, audioDurationMs,
                                videoUrl, videoDurationMs, fileUrl, fileName, fileMimeType, fileSizeBytes
                            )
                        }
                    })
                } else {
                    proceedWithSendMessage(
                        channelID, messageText, image, channelName, username, profileImage, uid,
                        replyToMessageId, replyToSenderName, replyToMessageText, audioUrl, audioDurationMs,
                        videoUrl, videoDurationMs, fileUrl, fileName, fileMimeType, fileSizeBytes
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
        fileSizeBytes: Long? = null
    ) {
        val message = Message(
            id = db.reference.push().key ?: UUID.randomUUID().toString(),
            senderId = uid,
            message = messageText,
            createdAt = System.currentTimeMillis(),
            senderName = username,
            senderImage = profileImage,
            imageUrl = image,
            replyToMessageId = replyToMessageId,
            replyToSenderName = replyToSenderName,
            replyToMessageText = replyToMessageText,
            audioUrl = audioUrl,
            audioDurationMs = audioDurationMs,
            videoUrl = videoUrl,
            videoDurationMs = videoDurationMs,
            fileUrl = fileUrl,
            fileName = fileName,
            fileMimeType = fileMimeType,
            fileSizeBytes = fileSizeBytes
        )

        db.reference.child("messages")
            .child(channelID)
            .push()
            .setValue(message)
            .addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d("ChatViewModel", "Message saved successfully")
                    if (channelID.contains("_")) {
                        val parts = channelID.split("_")
                        if (parts.size == 2) {
                            db.reference.child("chat_hidden").child(parts[0]).child(channelID).removeValue()
                            db.reference.child("chat_hidden").child(parts[1]).child(channelID).removeValue()
                        }
                    }
                    // Trigger notification flow safely
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
                    triggerNotificationFlow(channelID, notifyText, username, profileImage, uid, channelName)
                }
            }
    }

    private fun triggerNotificationFlow(
        channelID: String,
        messageText: String,
        senderName: String,
        senderImage: String?,
        senderUid: String,
        channelName: String?
    ) {
        val threadName = Thread.currentThread().name
        if (channelID.contains("_")) {
            // Direct chat: Send to the receiver's FCM token
            val parts = channelID.split("_")
            val receiverUid = parts.firstOrNull { it != senderUid }
            if (receiverUid != null && receiverUid != senderUid) {
                db.reference.child("users").child(receiverUid).child("fcmToken")
                    .addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val token = snapshot.getValue(String::class.java)
                            if (!token.isNullOrEmpty()) {
                                sendNotificationRequest(
                                    channelID = channelID,
                                    messageText = messageText,
                                    senderName = senderName,
                                    senderImage = senderImage,
                                    senderUid = senderUid,
                                    receiverToken = token,
                                    topic = null,
                                    channelName = channelName
                                )
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            Log.e("ChatViewModel", "FCM Token fetch cancelled")
                        }
                    })
            }
        } else {
            // Group chat: Distribute notifications directly to all registered users' tokens
            db.reference.child("users")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        snapshot.children.forEach { userSnapshot ->
                            val userUid = userSnapshot.key
                            if (userUid != null && userUid != senderUid) {
                                val token = userSnapshot.child("fcmToken").getValue(String::class.java)
                                if (!token.isNullOrEmpty()) {
                                    sendNotificationRequest(
                                        channelID = channelID,
                                        messageText = messageText,
                                        senderName = senderName,
                                        senderImage = senderImage,
                                        senderUid = senderUid,
                                        receiverToken = token,
                                        topic = null,
                                        channelName = channelName
                                    )
                                }
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("ChatViewModel", "Group members fetch failed")
                    }
                })
        }
    }

    private fun sendNotificationRequest(
        channelID: String,
        messageText: String,
        senderName: String,
        senderImage: String?,
        senderUid: String,
        receiverToken: String?,
        topic: String?,
        channelName: String?
    ) {
        val requestCount = debugRequestCounter.incrementAndGet()
        val threadName = Thread.currentThread().name
        Log.d("FCM_DUPLICATE_DEBUG", "[sendNotificationRequest ENTRY] Timestamp: ${System.currentTimeMillis()}, ExecCount: $requestCount, channelID: $channelID, receiverTokenIsNull: ${receiverToken.isNullOrEmpty()}, topic: $topic, Thread: $threadName")
        val url = "https://kchat-notification-server.onrender.com/sendNotification"
        val jsonBody = JSONObject().apply {
            val isGroup = !channelID.contains("_")
            if (isGroup && !channelName.isNullOrEmpty()) {
                put("title", channelName)
                put("body", "$senderName: $messageText")
            } else {
                put("title", senderName)
                put("body", messageText)
            }
            if (!receiverToken.isNullOrEmpty()) {
                put("token", receiverToken)
            }
            if (!topic.isNullOrEmpty()) {
                put("topic", topic)
            }
            // Put extra data payload fields expected by FCM custom data or Android parsing
            val dataObj = JSONObject().apply {
                put("channelId", channelID)
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
        }

        stringRequest.retryPolicy = com.android.volley.DefaultRetryPolicy(
            15000,
            0,
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        requestQueue.add(stringRequest)
    }

    fun sendImageMessage(
        uri: Uri,
        channelID: String,
        channelName: String,
        replyToMessageId: String? = null,
        replyToSenderName: String? = null,
        replyToMessageText: String? = null
    ) {
        viewModelScope.launch {
            val storageUtils = SupabaseStorageUtils(context)
            val downloadUri = storageUtils.uploadImage(uri)
            downloadUri?.let {
                sendMessage(
                    channelID = channelID,
                    messageText = null,
                    image = downloadUri,
                    channelName = channelName,
                    replyToMessageId = replyToMessageId,
                    replyToSenderName = replyToSenderName,
                    replyToMessageText = replyToMessageText
                )
            }
        }
    }

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
            val storageUtils = SupabaseStorageUtils(context)
            val downloadUri = storageUtils.uploadAudio(file)
            downloadUri?.let { audioUrl ->
                sendMessage(
                    channelID = channelID,
                    messageText = null,
                    image = null,
                    channelName = channelName,
                    replyToMessageId = replyToMessageId,
                    replyToSenderName = replyToSenderName,
                    replyToMessageText = replyToMessageText,
                    audioUrl = audioUrl,
                    audioDurationMs = durationMs
                )
            }
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }
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
            val storageUtils = SupabaseStorageUtils(context)
            val downloadUri = storageUtils.uploadVideo(file)
            downloadUri?.let { videoUrl ->
                sendMessage(
                    channelID = channelID,
                    messageText = null,
                    image = null,
                    channelName = channelName,
                    replyToMessageId = replyToMessageId,
                    replyToSenderName = replyToSenderName,
                    replyToMessageText = replyToMessageText,
                    videoUrl = videoUrl,
                    videoDurationMs = durationMs
                )
            }
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }
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
            val storageUtils = SupabaseStorageUtils(context)
            val downloadUri = storageUtils.uploadFile(file, fileName, mimeType)
            downloadUri?.let { fileUrl ->
                sendMessage(
                    channelID = channelID,
                    messageText = null,
                    image = null,
                    channelName = channelName,
                    replyToMessageId = replyToMessageId,
                    replyToSenderName = replyToSenderName,
                    replyToMessageText = replyToMessageText,
                    fileUrl = fileUrl,
                    fileName = fileName,
                    fileMimeType = mimeType,
                    fileSizeBytes = fileSizeBytes
                )
            }
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun listenForMessages(channelID: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return

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

        // 🔥 IMPORTANT: Only apply group logic if NOT direct chat
        subscribeForNotification(channelID)

        if (channelID.contains("_")) {
            attachMessageListener(channelID)
        } else {
            registerUserIdtoChannel(channelID) {
                attachMessageListener(channelID)
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
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Message>()

                snapshot.children.forEach { data ->
                    val message = data.getValue(Message::class.java)
                    message?.let { list.add(it) }
                }

                rawMessages = list
                refreshFilteredMessages()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatViewModel", "Message load failed: ${error.message}")
            }
        }
        messagesListener = listener
        ref.orderByChild("createdAt").addValueEventListener(listener)
    }

    fun getAllUserEmails(channelID: String, callback: (List<String>) -> Unit) {
        val ref = db.reference.child("channels").child(channelID).child("users")
        val userIds = mutableListOf<String>()
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach {
                    userIds.add(it.value.toString())
                }
                callback.invoke(userIds)
            }

            override fun onCancelled(error: DatabaseError) {
                callback.invoke(emptyList())
            }
        })
    }

    fun registerUserIdtoChannel(channelID: String, onRegistered: (() -> Unit)? = null) {
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
                        ref.setValue(currentUser.email).addOnCompleteListener { task ->
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


    private fun subscribeForNotification(channelID: String) {

        val topic = if (channelID.contains("_")) {
            "direct_$channelID"
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
    }    fun markMessagesAsRead(channelID: String) {
        val currentUid = Firebase.auth.currentUser?.uid ?: return

        db.reference.child("messages")
            .child(channelID)
            .get()
            .addOnSuccessListener { snapshot ->

                snapshot.children.forEach { data ->
                    val message = data.getValue(Message::class.java)

                    // If message is not mine and not already read
                    if (message?.senderId != currentUid) {
                        data.ref.child("readBy")
                            .child(currentUid)
                            .setValue(true)
                    }
                }
            }
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

        db.reference.child("friends")
            .child(currentUid)
            .child(senderUid)
            .setValue(true)

        db.reference.child("friends")
            .child(senderUid)
            .child(currentUid)
            .setValue(true)

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
        db.reference.child("deleted_messages")
            .child(currentUid)
            .child(channelID)
            .child(messageId)
            .setValue(true)
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
    }

}
