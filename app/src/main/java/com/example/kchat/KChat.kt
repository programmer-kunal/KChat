package com.example.kchat

import android.app.Application
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.zegocloud.uikit.prebuilt.call.ZegoUIKitPrebuiltCallService
import com.zegocloud.uikit.prebuilt.call.core.invite.ZegoCallInvitationData
import com.zegocloud.uikit.prebuilt.call.invite.ZegoUIKitPrebuiltCallInvitationConfig
import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoTranslationText
import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoUIKitPrebuiltCallConfigProvider
import com.zegocloud.uikit.internal.ZegoUIKitLanguage
import com.zegocloud.uikit.prebuilt.call.event.ErrorEventsListener
import com.zegocloud.uikit.prebuilt.call.event.SignalPluginConnectListener
import com.zegocloud.uikit.prebuilt.call.event.CallEndListener
import com.zegocloud.uikit.service.defines.ZegoOnlySelfInRoomListener
import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoInvitationCallListener
import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoCallUser
import com.zegocloud.uikit.prebuilt.call.invite.internal.ZegoCallType
import im.zego.zim.enums.ZIMConnectionState
import im.zego.zim.enums.ZIMConnectionEvent
import org.json.JSONObject
import timber.log.Timber
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

@HiltAndroidApp
class KChat: Application(), ImageLoaderFactory {
    private var currentInitializedUser: String? = null
    private var activeActivities = 0

    companion object {
        var isAppInForeground: Boolean = false
            private set
        @Volatile
        var currentlyActiveChannelId: String? = null
        @Volatile
        var isZegoConnected: Boolean = false
        @Volatile
        var isZegoInitializing: Boolean = false
        val isZegoConnectedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Whole-App Local-First Caching
        com.example.kchat.data.cache.LocalDataCache.init(this)

        // Enable Firebase Realtime Database disk persistence (offline SQLite caching)
        try {
            com.google.firebase.database.FirebaseDatabase.getInstance().apply {
                setPersistenceEnabled(true)
                setPersistenceCacheSizeBytes(100L * 1024 * 1024)
            }
        } catch (e: Exception) {
            Log.w("KChat", "Firebase persistence note: ${e.message}")
        }

        // Initialize Google Tink Cryptographic Providers (Phase 2D E2EE)
        try {
            com.google.crypto.tink.aead.AeadConfig.register()
            com.google.crypto.tink.hybrid.HybridConfig.register()
        } catch (e: Exception) {
            Log.e("KChat", "Failed to register Tink configs: ${e.message}")
        }
        com.example.kchat.crypto.EncryptedMediaManager.cleanupOldTempFiles(this)

        // Initialize theme preference from SharedPreferences
        com.example.kchat.ui.theme.ThemeManager.init(this)

        // Initialize Firebase App Check: Debug provider for debug builds, reCAPTCHA Enterprise for release builds
        val appCheck = com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            )
        } else {
            val siteKey = BuildConfig.RECAPTCHA_ENTERPRISE_SITE_KEY
            if (siteKey.isNotBlank()) {
                appCheck.installAppCheckProviderFactory(
                    com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
            } else {
                Log.w("AppCheck", "reCAPTCHA Enterprise site key is missing; App Check not installed for release.")
            }
        }

        // Track Activity lifecycle to determine foreground/background state
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivityStarted(activity: android.app.Activity) {
                activeActivities++
                isAppInForeground = activeActivities > 0
                Log.d("NotificationDebug", "onActivityStarted: activeActivities=$activeActivities, isAppInForeground=$isAppInForeground")
            }
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {
                activeActivities--
                isAppInForeground = activeActivities > 0
                Log.d("NotificationDebug", "onActivityStopped: activeActivities=$activeActivities, isAppInForeground=$isAppInForeground")
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })

        // Register AuthStateListener to guarantee Zego initialization as soon as Firebase restores user session
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val currentUser = auth.currentUser
            if (currentUser != null) {
                val uid = currentUser.uid
                val email = currentUser.email ?: "user_${uid.take(5)}@kchat.com"
                val initialName = currentUser.displayName?.takeIf { it.isNotBlank() } ?: email

                val cleanUserId = email.take(32)
                if (currentInitializedUser != cleanUserId) {
                    initZegoService(
                        appID = AppID,
                        appSign = AppSign,
                        userID = cleanUserId,
                        userName = initialName
                    )
                }

                // Upload FCM token to database upon login/session restore
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
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
                    Log.d("NotificationDebug", "FCM token uploaded to database for user: $email")
                }

                // Phase 2D: Register device identity and HPKE keys in /user_devices/$uid/$deviceId
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    com.example.kchat.crypto.DeviceKeyManager.registerCurrentDevice(this@KChat, uid)
                }
            } else {
                Log.d("NotificationDebug", "AuthStateListener -> User is null (logged out)")
                currentInitializedUser = null
                isZegoConnected = false
                isZegoInitializing = false
                isZegoConnectedFlow.value = false
                try {
                    ZegoUIKitPrebuiltCallService.unInit()
                } catch (e: Exception) {
                    Log.w("NotificationDebug", "Error uninitializing Zego on logout: ${e.message}")
                }
                com.example.kchat.crypto.EncryptedMediaManager.clear(this@KChat)
            }
        }
    }

    @Synchronized
    fun initZegoService(appID: Long, appSign: String, userID: String, userName: String) {
        val cleanUserId = if (userID.length > 32) userID.take(32) else userID
        if (currentInitializedUser == cleanUserId && (isZegoConnected || isZegoInitializing)) {
            Log.d("NotificationDebug", "initZegoService -> Zego already connected or initializing for $cleanUserId")
            return
        }

        currentInitializedUser = cleanUserId
        isZegoInitializing = true

        val callInvitationConfig = ZegoUIKitPrebuiltCallInvitationConfig()
        callInvitationConfig.translationText = ZegoTranslationText(ZegoUIKitLanguage.ENGLISH)
        callInvitationConfig.endCallWhenInitiatorLeave = true

        callInvitationConfig.provider =
            ZegoUIKitPrebuiltCallConfigProvider { invitationData: ZegoCallInvitationData? ->
                ZegoUIKitPrebuiltCallInvitationConfig.generateDefaultConfig(invitationData)
            }

        ZegoUIKitPrebuiltCallService.events.errorEventsListener =
            ErrorEventsListener { errorCode: Int, message: String ->
                Timber.d("onError() called with: errorCode = [$errorCode], message = [$message]")
                if (errorCode == 6000121) {
                    isZegoConnected = false
                    isZegoConnectedFlow.value = false
                }
            }

        ZegoUIKitPrebuiltCallService.events.invitationEvents.pluginConnectListener =
            SignalPluginConnectListener { state: ZIMConnectionState, event: ZIMConnectionEvent, extendedData: JSONObject ->
                Timber.d("onSignalPluginConnectionStateChanged() called with state=$state, event=$event")
                val connected = (state == ZIMConnectionState.CONNECTED)
                isZegoConnected = connected
                isZegoConnectedFlow.value = connected
                if (connected) {
                    isZegoInitializing = false
                }
            }

        // Listener for when only self is left in room -> end active call immediately so neither side stays stuck
        ZegoUIKitPrebuiltCallService.events.callEvents.onlySelfInRoomListener =
            ZegoOnlySelfInRoomListener {
                Log.d("NotificationDebug", "onlySelfInRoomListener -> only self left in room, ending call")
                ZegoUIKitPrebuiltCallService.endCall()
            }

        // Synchronize invitation decline, cancellation, and timeout so ringing screens dismiss cleanly
        ZegoUIKitPrebuiltCallService.events.invitationEvents.invitationListener =
            object : ZegoInvitationCallListener {
                override fun onIncomingCallReceived(
                    callID: String?,
                    caller: ZegoCallUser?,
                    callType: ZegoCallType?,
                    callees: MutableList<ZegoCallUser>?
                ) {
                    Log.d("NotificationDebug", "onIncomingCallReceived: callID=$callID, caller=${caller?.id}")
                }

                override fun onIncomingCallCanceled(callID: String?, caller: ZegoCallUser?) {
                    Log.d("NotificationDebug", "onIncomingCallCanceled: caller cancelled, ending incoming call UI")
                    ZegoUIKitPrebuiltCallService.endCall()
                }

                override fun onIncomingCallTimeout(callID: String?, caller: ZegoCallUser?) {
                    Log.d("NotificationDebug", "onIncomingCallTimeout: callID=$callID")
                    ZegoUIKitPrebuiltCallService.endCall()
                }

                override fun onOutgoingCallAccepted(callID: String?, callee: ZegoCallUser?) {
                    Log.d("NotificationDebug", "onOutgoingCallAccepted: callID=$callID, callee=${callee?.id}")
                }

                override fun onOutgoingCallRejectedCauseBusy(callID: String?, callee: ZegoCallUser?) {
                    Log.d("NotificationDebug", "onOutgoingCallRejectedCauseBusy: callee busy: ${callee?.id}")
                    if (callID == null || !callID.startsWith("call_group_")) {
                        ZegoUIKitPrebuiltCallService.endCall()
                    }
                }

                override fun onOutgoingCallDeclined(callID: String?, callee: ZegoCallUser?) {
                    Log.d("NotificationDebug", "onOutgoingCallDeclined: callee declined: ${callee?.id}")
                    // For direct 1-on-1 call, end outgoing call immediately so caller doesn't stay stuck ringing
                    if (callID == null || !callID.startsWith("call_group_")) {
                        ZegoUIKitPrebuiltCallService.endCall()
                    }
                }

                override fun onOutgoingCallTimeout(callID: String?, callees: MutableList<ZegoCallUser>?) {
                    Log.d("NotificationDebug", "onOutgoingCallTimeout: callID=$callID")
                    if (callID == null || !callID.startsWith("call_group_")) {
                        ZegoUIKitPrebuiltCallService.endCall()
                    }
                }
            }

        ZegoUIKitPrebuiltCallService.init(
            this, appID, appSign, cleanUserId, userName, callInvitationConfig
        )

        ZegoUIKitPrebuiltCallService.enableFCMPush()

        ZegoUIKitPrebuiltCallService.events.callEvents.callEndListener =
            CallEndListener { callEndReason, jsonObject ->
                Log.d("CallEndListener", "Call Ended with reason: $callEndReason")
            }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}