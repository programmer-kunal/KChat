package com.example.kchat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kchat.feature.auth.signin.SignInScreen
import com.example.kchat.feature.chat.ChatScreen
import com.example.kchat.feature.chat.DirectChatScreen
import com.example.kchat.feature.extension.KChatExtensionScreen
import com.example.kchat.feature.home.HomeScreen
import com.example.kchat.feature.splashscreen.SplashScreen
import com.google.firebase.auth.FirebaseAuth
import android.util.Log

@Composable
fun MainApp(startChannelId: String? = null, startChannelName: String? = null){
    Log.d("NotificationDebug", "MainAppComposable recompose. startChannelId=$startChannelId, startChannelName=$startChannelName")
    Surface(modifier = Modifier.fillMaxSize()) {
        val navController= rememberNavController()
        val currentUser= FirebaseAuth.getInstance().currentUser
        
        val hasDeepLink = !startChannelId.isNullOrEmpty() && !startChannelName.isNullOrEmpty()
        val startDest = if (currentUser != null && currentUser.isEmailVerified && hasDeepLink) "home" else "splash"
        
        Log.d("NotificationDebug", "MainApp startDestination selected: startDest=$startDest (hasDeepLink=$hasDeepLink)")

        NavHost(navController = navController, startDestination = startDest){
            composable("splash"){
                SplashScreen(navController)
            }

            composable("login"){
                SignInScreen(navController)
            }
            composable("home"){
                HomeScreen(navController)
            }
            composable("extension"){
                KChatExtensionScreen(navController)
            }

            composable(
                route = "chat/{channelId}&{channelName}?targetMsgId={targetMsgId}",
                arguments = listOf(
                    navArgument("channelId") {
                        type = NavType.StringType
                    },
                    navArgument("channelName") {
                        type = NavType.StringType
                    },
                    navArgument("targetMsgId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
                val channelName = backStackEntry.arguments?.getString("channelName") ?: ""
                val targetMsgId = backStackEntry.arguments?.getString("targetMsgId")
                ChatScreen(navController, channelId, channelName, initialTargetMessageId = targetMsgId)
            }

        }

        // Auto-navigate if started via notification metadata
        LaunchedEffect(startChannelId, startChannelName) {
            Log.d("NotificationDebug", "LaunchedEffect triggered. startChannelId=$startChannelId, startChannelName=$startChannelName, userVerified=${currentUser != null && currentUser.isEmailVerified}")
            if (currentUser != null && currentUser.isEmailVerified && !startChannelId.isNullOrEmpty() && !startChannelName.isNullOrEmpty()) {
                Log.d("NotificationDebug", "LaunchedEffect conditions PASSED. Navigating to: chat/$startChannelId&$startChannelName")
                
                val currentRoute = navController.currentDestination?.route
                Log.d("NotificationDebug", "Current navigation route before deep link: $currentRoute")
                
                if (currentRoute != "home" && currentRoute?.startsWith("chat/") != true) {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
                navController.navigate("chat/$startChannelId&$startChannelName")
            }
        }
    }
}

