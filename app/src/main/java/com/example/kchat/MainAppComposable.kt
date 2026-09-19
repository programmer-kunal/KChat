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
import com.example.kchat.feature.auth.signup.SignUpScreen
import com.example.kchat.feature.chat.ChatScreen
import com.example.kchat.feature.chat.DirectChatScreen
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
        val start =if (currentUser!=null && currentUser.isEmailVerified) "home" else "login"
        
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
            composable("signup"){
                SignUpScreen(navController)
            }
            composable("home"){
                HomeScreen(navController)
            }

            composable("chat/{channelId}&{channelName}", arguments = listOf(
                navArgument("channelId"){
                    type= NavType.StringType
                },
                navArgument("channelName"){
                    type= NavType.StringType
                }

            )){
                val channelId=it.arguments?.getString("channelId")?:""
                val channelName=it.arguments?.getString("channelName")?:""
                ChatScreen(navController,channelId,channelName)
            }

        }

        // Auto-navigate if started via notification metadata
        LaunchedEffect(startChannelId, startChannelName) {
            Log.d("NotificationDebug", "LaunchedEffect triggered. startChannelId=$startChannelId, startChannelName=$startChannelName, userVerified=${currentUser != null && currentUser.isEmailVerified}")
            if (currentUser != null && currentUser.isEmailVerified && !startChannelId.isNullOrEmpty() && !startChannelName.isNullOrEmpty()) {
                Log.d("NotificationDebug", "LaunchedEffect conditions PASSED. Navigating to: chat/$startChannelId&$startChannelName")
                
                val currentRoute = navController.currentDestination?.route
                Log.d("NotificationDebug", "Current navigation route before deep link: $currentRoute")
                
                if (currentRoute != "home" && currentRoute != "chat/{channelId}&{channelName}") {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
                navController.navigate("chat/$startChannelId&$startChannelName")
            }
        }
    }
}

