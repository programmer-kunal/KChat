package com.example.kchat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.Random

class FirebaseMessageService : FirebaseMessagingService() {

    companion object {
        // Cache recently displayed messageIds to prevent duplicate notifications (e.g., from dual-delivery or network retry)
        private val processedMessageIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        private val messageIdQueue = java.util.concurrent.ConcurrentLinkedQueue<String>()
        private const val MAX_PROCESSED_MESSAGES = 200

        private fun recordAndCheckDuplicate(messageId: String): Boolean {
            if (!processedMessageIds.add(messageId)) {
                return true // duplicate
            }
            messageIdQueue.add(messageId)
            if (messageIdQueue.size > MAX_PROCESSED_MESSAGES) {
                messageIdQueue.poll()?.let { oldest ->
                    processedMessageIds.remove(oldest)
                }
            }
            return false // new message
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d("FCM_PROOF", "onMessageReceived EXECUTED")
        Log.d("FCM_DEBUG", "Message received: ${message.data}")

        val channelId = message.data["channelId"]
        if (channelId.isNullOrEmpty()) {
            Log.d("FCM_PROOF", "Received non-chat FCM message (likely Zego call invitation). Forwarding to ZegoFirebaseMessagingService.")
            try {
                val serviceClass = Class.forName("im.zego.zpns.fcm.ZegoFirebaseMessagingService")
                val intent = Intent(this, serviceClass).apply {
                    setAction("com.google.firebase.MESSAGING_EVENT")
                    putExtras(message.toIntent())
                }
                startService(intent)
            } catch (e: Exception) {
                Log.e("FCM_PROOF", "Failed to forward FCM message to Zego", e)
            }
            return
        }

        val messageId = message.data["messageId"]
        if (!messageId.isNullOrEmpty()) {
            if (recordAndCheckDuplicate(messageId)) {
                Log.d("FCM_PROOF", "Duplicate FCM message detected for messageId=$messageId. Suppressing duplicate notification.")
                return
            }
        }

        val senderId = message.data["senderId"]
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: getSharedPreferences("kchat_user_prefs", android.content.Context.MODE_PRIVATE).getString("current_uid", null)

        if (senderId != null && senderId == currentUid) {
            return
        }

        // Record message delivery reliably in RTDB for direct and custom group chats (excluding World Chat to prevent write amplification)
        if (currentUid != null && channelId != "world_chat" && channelId != "worldchat") {
            markMessageAsDelivered(channelId, messageId)
        }

        // Suppress message notifications across all screens whenever KChat is actively in foreground
        if (KChat.isAppInForeground) {
            Log.d("FCM_PROOF", "KChat is currently in foreground. Suppressing message notification across all screens.")
            return
        }

        // Suppress notifications if the user has muted this direct chat or group
        if (com.example.kchat.feature.chat.ConversationMuteManager.isMuted(this, currentUid, channelId)) {
            Log.d("FCM_PROOF", "Conversation/group $channelId is muted for user $currentUid. Suppressing notification.")
            return
        }


        val title: String?
        val body: String?

        if (message.notification != null) {
            title = message.notification?.title
            body = message.notification?.body
        } else {
            title = message.data["title"]
            body = message.data["body"]
        }

        val senderName = message.data["senderName"]
        val senderImage = message.data["senderImage"]
        val channelName = message.data["channelName"]
        var rawMessageText = message.data["messageText"] ?: body
        val isGroupChat = channelId.startsWith("-") || channelId == "world_chat" || channelId == "worldchat" || !channelId.contains("_")
        if (isGroupChat && !senderName.isNullOrEmpty() && rawMessageText != null) {
            val prefix = "$senderName: "
            if (rawMessageText.startsWith(prefix)) {
                rawMessageText = rawMessageText.substring(prefix.length)
            }
        }

        Log.d("NotificationDebug", "FCM Received data: ${message.data}")
        Log.d("NotificationDebug", "FCM Parsed extras: channelId=$channelId, senderName=$senderName, senderImage=$senderImage, channelName=$channelName, rawMessageText=$rawMessageText")

        showNotification(title, rawMessageText, channelId, senderName, senderImage, channelName)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "messages",
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat message notifications"
            }

            val manager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(
        title: String?,
        message: String?,
        channelId: String?,
        senderName: String?,
        senderImage: String?,
        channelName: String?
    ) {
        Log.d("FCM_PROOF", "showNotification EXECUTED")

        val safeTitle = title ?: "New Message"
        val safeMessage = message ?: ""

        val isGroup = channelId != null && (channelId.startsWith("-") || channelId == "world_chat" || channelId == "worldchat" || !channelId.contains("_"))
        val groupName = if (isGroup) {
            channelName?.takeIf { it.isNotBlank() }
                ?: title?.takeIf { it.isNotBlank() && it != "New Message" && it != "Group Chat" }
        } else null
        val intentName = if (isGroup) (groupName ?: "Group Chat") else senderName

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (channelId != null) {
                putExtra("channelId", channelId)
            }
            if (intentName != null) {
                putExtra("senderName", intentName)
                putExtra("channelName", intentName)
            }
            if (senderImage != null) {
                putExtra("senderImage", senderImage)
            }
        }

        Log.d("NotificationDebug", "FCM PendingIntent Intent created. Extras: channelId=${intent.getStringExtra("channelId")}, senderName=${intent.getStringExtra("senderName")}")

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = channelId?.hashCode() ?: Random().nextInt(100000)

        val notificationBuilder = NotificationCompat.Builder(this, "messages")
            .setSmallIcon(R.drawable.logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (isGroup) {
            val groupTitle = groupName ?: "Group Chat"
            notificationBuilder.setContentTitle(groupTitle)
            notificationBuilder.setContentText("$senderName: $safeMessage")

            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(groupTitle)

            // Restore previous group messages from the active notification in the tray
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val activeNotifications = notificationManager.activeNotifications
                    val existingSbn = activeNotifications.firstOrNull { it.id == notificationId }
                    if (existingSbn != null) {
                        val extras = existingSbn.notification.extras
                        val existingLines = extras.getCharSequenceArray("android.textLines")
                        if (existingLines != null) {
                            for (line in existingLines) {
                                inboxStyle.addLine(line)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NotificationDebug", "Error restoring active notification style", e)
                }
            }

            inboxStyle.addLine("$senderName: $safeMessage")
            notificationBuilder.setStyle(inboxStyle)
        } else {
            notificationBuilder.setContentTitle(senderName)

            val userPerson = androidx.core.app.Person.Builder().setName("Me").build()
            val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
                .setConversationTitle(senderName)

            // Restore previous direct messages from the active notification in the tray
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val activeNotifications = notificationManager.activeNotifications
                    val existingSbn = activeNotifications.firstOrNull { it.id == notificationId }
                    if (existingSbn != null) {
                        val existingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(existingSbn.notification)
                        if (existingStyle != null) {
                            for (msg in existingStyle.messages) {
                                messagingStyle.addMessage(msg)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NotificationDebug", "Error restoring active notification style", e)
                }
            }

            val senderPerson = androidx.core.app.Person.Builder()
                .setName(senderName ?: "Sender")
                .build()
            messagingStyle.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    safeMessage,
                    System.currentTimeMillis(),
                    senderPerson
                )
            )
            notificationBuilder.setStyle(messagingStyle)
        }

        val notification = notificationBuilder.build()
        Log.d("NotificationDebug", "Posting notification. channelId=$channelId, notificationId=$notificationId, isGroup=$isGroup")
        notificationManager.notify(notificationId, notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_PROOF", "onNewToken EXECUTED with token: $token")

        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: getSharedPreferences("kchat_user_prefs", android.content.Context.MODE_PRIVATE).getString("current_uid", null)
        if (uid != null) {
            // Primary secure location: user_private/$uid/fcmToken
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("user_private")
                .child(uid)
                .child("fcmToken")
                .setValue(token)
            // Backward compatibility: also write to users/$uid/fcmToken
            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                .child(uid)
                .child("fcmToken")
                .setValue(token)
        }

        try {
            val serviceClass = Class.forName("im.zego.zpns.fcm.ZegoFirebaseMessagingService")
            val intent = Intent(this, serviceClass).apply {
                setAction("com.google.firebase.MESSAGING_EVENT")
                putExtra("token", token)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("FCM_PROOF", "Failed to forward new token to Zego", e)
        }
    }

    private fun markMessageAsDelivered(channelId: String, messageId: String?) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: getSharedPreferences("kchat_user_prefs", android.content.Context.MODE_PRIVATE).getString("current_uid", null)
            ?: return
        val dbRef = com.google.firebase.database.FirebaseDatabase.getInstance().reference
            .child("messages").child(channelId)

        if (!messageId.isNullOrEmpty()) {
            dbRef.child(messageId).child("deliveredBy").child(currentUid).setValue(true)
        } else {
            dbRef.orderByChild("createdAt").limitToLast(5).addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    snapshot.children.forEach { child ->
                        val msg = child.getValue(com.example.kchat.model.Message::class.java)
                        if (msg != null && msg.senderId != currentUid && msg.deliveredBy?.get(currentUid) != true) {
                            child.ref.child("deliveredBy").child(currentUid).setValue(true)
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }
    }

    private fun isSameConversation(
        activeChannelId: String?,
        incomingChannelId: String?,
        senderId: String? = null,
        currentUid: String? = null
    ): Boolean {
        if (activeChannelId.isNullOrBlank() || incomingChannelId.isNullOrBlank()) return false
        val active = activeChannelId.trim()
        val incoming = incomingChannelId.trim()
        if (active.equals(incoming, ignoreCase = true)) return true

        // Direct chat comparison: order-independent canonical check (e.g. uid1_uid2 vs uid2_uid1)
        val isDirectActive = active.contains("_") && !active.startsWith("-") && !active.startsWith("self_chat_") && active != "world_chat" && active != "worldchat"
        val isDirectIncoming = incoming.contains("_") && !incoming.startsWith("-") && !incoming.startsWith("self_chat_") && incoming != "world_chat" && incoming != "worldchat"

        if (isDirectActive && isDirectIncoming) {
            val activeParts = active.split("_")
            val incomingParts = incoming.split("_")
            if (activeParts.size == 2 && incomingParts.size == 2) {
                if (activeParts.sorted() == incomingParts.sorted()) {
                    return true
                }
            }
        }

        // Direct chat participant fallback: if active direct chat contains both senderId and currentUid
        if (isDirectActive && !senderId.isNullOrBlank() && !currentUid.isNullOrBlank()) {
            val activeParts = active.split("_").toSet()
            if (activeParts.size == 2 && activeParts.contains(senderId) && activeParts.contains(currentUid)) {
                return true
            }
        }

        return false
    }
}