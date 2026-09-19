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
import im.zego.zim.enums.ZIMConnectionState
import im.zego.zim.enums.ZIMConnectionEvent
import org.json.JSONObject
import timber.log.Timber
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KChat: Application() {
    private var currentInitializedUser: String? = null
    private var activeActivities = 0

    companion object {
        var isAppInForeground: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()

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
                currentUser.email?.let { email ->
                    if (email != currentInitializedUser) {
                        currentInitializedUser = email
                        val uid = currentUser.uid

                        // Fetch real-time database user name to display username instead of email in call screen and notifications
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                            .child(uid)
                            .child("name")
                            .get()
                            .addOnSuccessListener { snapshot ->
                                val dbName = snapshot.getValue(String::class.java)
                                val finalName = if (!dbName.isNullOrBlank()) dbName else (currentUser.displayName ?: email)
                                Log.d("NotificationDebug", "AuthStateListener -> User is logged in, initializing Zego with name: $finalName")
                                initZegoService(
                                    appID = AppID,
                                    appSign = AppSign,
                                    userID = email,
                                    userName = finalName
                                )
                            }
                            .addOnFailureListener {
                                val fallbackName = currentUser.displayName ?: email
                                Log.w("NotificationDebug", "Failed to fetch database name, fallback to: $fallbackName")
                                initZegoService(
                                    appID = AppID,
                                    appSign = AppSign,
                                    userID = email,
                                    userName = fallbackName
                                )
                            }

                        // Upload FCM token to database upon login/session restore
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users")
                                .child(uid)
                                .child("fcmToken")
                                .setValue(token)
                            Log.d("NotificationDebug", "FCM token uploaded to database for user: $email")
                        }
                    }
                }
            } else {
                Log.d("NotificationDebug", "AuthStateListener -> User is null (logged out)")
                currentInitializedUser = null
            }
        }
    }

    fun initZegoService(appID: Long, appSign: String, userID: String, userName: String) {
        val callInvitationConfig = ZegoUIKitPrebuiltCallInvitationConfig()
        callInvitationConfig.translationText = ZegoTranslationText(ZegoUIKitLanguage.ENGLISH)

        callInvitationConfig.provider =
            ZegoUIKitPrebuiltCallConfigProvider { invitationData: ZegoCallInvitationData? ->
                ZegoUIKitPrebuiltCallInvitationConfig.generateDefaultConfig(invitationData)
            }

        ZegoUIKitPrebuiltCallService.events.errorEventsListener =
            ErrorEventsListener { errorCode: Int, message: String ->
                Timber.d("onError() called with: errorCode = [$errorCode], message = [$message]")
            }

        ZegoUIKitPrebuiltCallService.events.invitationEvents.pluginConnectListener =
            SignalPluginConnectListener { state: ZIMConnectionState, event: ZIMConnectionEvent, extendedData: JSONObject ->
                Timber.d("onSignalPluginConnectionStateChanged() called")
            }

        ZegoUIKitPrebuiltCallService.init(
            this, appID, appSign, userID, userName, callInvitationConfig
        )

        ZegoUIKitPrebuiltCallService.enableFCMPush()

        ZegoUIKitPrebuiltCallService.events.callEvents.callEndListener =
            CallEndListener { callEndReason, jsonObject ->
                Log.d("CallEndListener", "Call Ended with reason: $callEndReason")
            }
    }
}