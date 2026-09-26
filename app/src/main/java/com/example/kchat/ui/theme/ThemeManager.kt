package com.example.kchat.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Supported themes in KChat.
 */
enum class AppTheme(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light"),
    DARK("Dark")
}

/**
 * Centralized theme manager for KChat.
 * Persists user preference via local SharedPreferences and exposes reactive theme state via StateFlow.
 */
object ThemeManager {
    private const val PREFS_NAME = "kchat_theme_preferences"
    private const val KEY_THEME = "selected_app_theme"

    private val _currentTheme = MutableStateFlow(AppTheme.SYSTEM)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private var sharedPreferences: SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPreferences == null) {
            val appContext = context.applicationContext ?: context
            sharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedThemeName = sharedPreferences?.getString(KEY_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
            _currentTheme.value = try {
                AppTheme.valueOf(savedThemeName)
            } catch (e: Exception) {
                AppTheme.SYSTEM
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        _currentTheme.value = theme
        sharedPreferences?.edit()?.putString(KEY_THEME, theme.name)?.apply()
    }
}
