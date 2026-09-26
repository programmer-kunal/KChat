package com.example.kchat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- KChat Signature Dark Theme ---
// Preserves existing KChat dark appearance (dark blue background, light blue accent)
val KChatDarkBackground = Color(0xFF162542)   // R.color.dark_blue
val KChatLightBlueAccent = Color(0xFF6388CE)  // R.color.light_blue
val KChatSlateCard = Color(0xFF1E293B)
val KChatCyanAccent = Color(0xFF38BDF8)

private val DarkColorScheme = darkColorScheme(
    primary = KChatLightBlueAccent,
    onPrimary = Color.White,
    primaryContainer = KChatSlateCard,
    onPrimaryContainer = KChatCyanAccent,
    secondary = KChatCyanAccent,
    onSecondary = Color(0xFF0F172A),
    background = KChatDarkBackground,
    onBackground = Color.White,
    surface = KChatDarkBackground,
    onSurface = Color.White,
    surfaceVariant = KChatSlateCard,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    error = Color(0xFFEF4444),
    onError = Color.White
)

// --- KChat Clean Light Theme ---
// Clean, readable, professional Material 3 theme keeping KChat's visual identity
val KChatLightBackground = Color(0xFFF1F5F9)  // Crisp light slate
val KChatLightSurface = Color(0xFFFFFFFF)     // Clean white surface
val KChatLightPrimary = Color(0xFF3B6CB5)     // High-contrast KChat brand blue
val KChatLightTextPrimary = Color(0xFF0F172A) // Crisp dark slate text
val KChatLightTextSecondary = Color(0xFF475569)

private val LightColorScheme = lightColorScheme(
    primary = KChatLightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = KChatLightBackground,
    onBackground = KChatLightTextPrimary,
    surface = KChatLightSurface,
    onSurface = KChatLightTextPrimary,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = KChatLightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun KChatTheme(
    themeMode: AppTheme = ThemeManager.currentTheme.collectAsState().value,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppTheme.SYSTEM -> isSystemDark
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}