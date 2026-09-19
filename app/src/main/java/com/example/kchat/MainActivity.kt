package com.example.kchat

import android.Manifest.permission
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.fragment.app.FragmentActivity
import com.example.kchat.ui.theme.KChatTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import com.permissionx.guolindev.PermissionX
import com.zegocloud.uikit.internal.ZegoUIKitLanguage
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import com.zegocloud.uikit.prebuilt.call.core.invite.ZegoCallInvitationData
import com.zegocloud.uikit.prebuilt.call.event.CallEndListener
import com.zegocloud.uikit.prebuilt.call.event.ErrorEventsListener
import com.zegocloud.uikit.prebuilt.call.event.SignalPluginConnectListener
import com.zegocloud.uikit.prebuilt.call.event.ZegoCallEndReason
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoTranslationText
import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoUIKitPrebuiltCallConfigProvider
import dagger.hilt.android.AndroidEntryPoint
import im.zego.zim.enums.ZIMConnectionEvent
import im.zego.zim.enums.ZIMConnectionState
import org.json.JSONObject
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var isOnlinePresenceSetup = false
    private val startChannelId: androidx.compose.runtime.MutableState<String?> = androidx.compose.runtime.mutableStateOf(null)
    private val startChannelName: androidx.compose.runtime.MutableState<String?> = androidx.compose.runtime.mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
        Firebase.auth.addAuthStateListener { auth ->
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val uid = currentUser.uid
                Log.d("NotificationDebug", "MainActivity AuthStateListener -> User logged in: $uid")
                
                // Upload token immediately upon login/session restore
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    Log.d("NotificationDebug", "MainActivity AuthStateListener -> FCM token retrieved: $token")
                    Firebase.database.getReference("users")
                        .child(uid)
                        .child("fcmToken")
                        .setValue(token)
                }
                
                // Setup online presence system
                setupOnlinePresence()
            } else {
                Log.d("NotificationDebug", "MainActivity AuthStateListener -> User is null")
                isOnlinePresenceSetup = false
            }
        }
        this.startChannelId.value = intent.getStringExtra("channelId")
        this.startChannelName.value = intent.getStringExtra("channelName") ?: intent.getStringExtra("senderName")
        Log.d(
            "FCM_PROOF",
            "MainActivity onCreate -> channelId=${this.startChannelId.value} senderName=${this.startChannelName.value}"
        )

        // Temporary logging to inspect nested bundle values
        Log.d("NotificationDebug", "=== BEGIN INTENT EXTRA AUDIT ===")
        intent.extras?.let { bundle ->
            Log.d("NotificationDebug", "Root keySet: ${bundle.keySet()}")
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                Log.d("NotificationDebug", "ROOT KEY: $key, TYPE: ${value?.javaClass?.simpleName}, VALUE: $value")
                if (value is android.os.Bundle) {
                    Log.d("NotificationDebug", "  --> Entering Nested Bundle for key: $key")
                    for (nestedKey in value.keySet()) {
                        val nestedValue = value.get(nestedKey)
                        Log.d("NotificationDebug", "  NESTED BUNDLE KEY: $nestedKey, TYPE: ${nestedValue?.javaClass?.simpleName}, VALUE: $nestedValue")
                    }
                }
            }
        } ?: Log.d("NotificationDebug", "intent.extras is NULL")
        Log.d("NotificationDebug", "=== END INTENT EXTRA AUDIT ===")

        Log.d("NotificationDebug", "MainActivity onCreate. startChannelId=${this.startChannelId.value}, startChannelName=${this.startChannelName.value}, hasExtras=${intent.extras != null}")

        setContent {
            KChatTheme {
                MainApp(startChannelId = this.startChannelId.value, startChannelName = this.startChannelName.value)
            }
        }

        permissionHandling(this)

        // 🔥 ADD REAL-TIME ONLINE PRESENCE SYSTEM
        setupOnlinePresence()

        // Zego call service is now initialized early in KChat Application class
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val channelId = intent.getStringExtra("channelId")
        val senderName = intent.getStringExtra("channelName") ?: intent.getStringExtra("senderName")
        Log.d(
            "FCM_PROOF",
            "MainActivity onNewIntent -> channelId=$channelId senderName=$senderName"
        )
        Log.d("NotificationDebug", "MainActivity onNewIntent. channelId=$channelId, senderName=$senderName, hasExtras=${intent.extras != null}")
        if (!channelId.isNullOrEmpty() && !senderName.isNullOrEmpty()) {
            startChannelId.value = channelId
            startChannelName.value = senderName
        }
    }

    // ✅ REAL-TIME ONLINE SYSTEM
    private fun setupOnlinePresence() {
        if (isOnlinePresenceSetup) return
        val uid = Firebase.auth.currentUser?.uid ?: return
        isOnlinePresenceSetup = true
        val database = Firebase.database
        val statusRef = database.getReference("status/$uid")

        val connectedRef = database.getReference(".info/connected")

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false

                if (connected) {

                    // When user disconnects
                    statusRef.onDisconnect().setValue(
                        mapOf(
                            "online" to false,
                            "lastSeen" to ServerValue.TIMESTAMP
                        )
                    )

                    // When user connects
                    statusRef.setValue(
                        mapOf(
                            "online" to true,
                            "lastSeen" to System.currentTimeMillis()
                        )
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }



    private fun permissionHandling(activityContext: FragmentActivity) {
        PermissionX.init(activityContext)
            .permissions(permission.SYSTEM_ALERT_WINDOW)
            .onExplainRequestReason { scope, deniedList ->
                val message =
                    "We need your consent for permissions to use offline call properly"
                scope.showRequestReasonDialog(deniedList, message, "Allow", "Deny")
            }
            .request { _, _, _ -> }
    }
}
