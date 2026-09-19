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

        // Suppress normal chat message notifications if the app is already in the foreground
        if (KChat.isAppInForeground) {
            Log.d("FCM_PROOF", "App is in foreground. Suppressing normal chat message notification to let UI handle it natively.")
            return
        }

        val senderId = message.data["senderId"]
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

        if (senderId != null && senderId == currentUid) {
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
        if (channelId != null && !channelId.contains("_") && !senderName.isNullOrEmpty() && rawMessageText != null) {
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

        val isGroup = channelId != null && !channelId.contains("_")
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
        if (uid != null) {
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
}