package com.mtracker

import android.content.Context
import com.mlib.future.FutureConfig

object ThemeManager {
    private const val PREFS_NAME = "tracker_prefs"
    private const val KEY_THEME_NAME = "theme_name"
    
    val themeNames = listOf("Masem", "Aurora", "Julee", "Autumn", "Sunrise", "Sunset", "Painting", "Charcoal", "Celeste")

    fun getThemeName(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_NAME, "Masem") ?: "Masem"
    }

    fun setThemeName(context: Context, name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_NAME, name).apply()
    }

    fun getThemeConfig(themeName: String, isDark: Boolean): FutureConfig {
        return when (themeName) {
            "Aurora" -> if (isDark) FutureConfig.AuroraDark else FutureConfig.AuroraLight
            "Julee" -> if (isDark) FutureConfig.JuleeDark else FutureConfig.JuleeLight
            "Autumn" -> if (isDark) FutureConfig.AutumnDark else FutureConfig.AutumnLight
            "Sunrise" -> if (isDark) FutureConfig.SunriseDark else FutureConfig.SunriseLight
            "Sunset" -> if (isDark) FutureConfig.SunsetDark else FutureConfig.SunsetLight
            "Painting" -> if (isDark) FutureConfig.PaintingDark else FutureConfig.PaintingLight
            "Charcoal" -> if (isDark) FutureConfig.CharcoalDark else FutureConfig.CharcoalLight
            "Celeste" -> if (isDark) FutureConfig.CelesteDark else FutureConfig.CelesteLight
            else -> if (isDark) FutureConfig.MasemDark else FutureConfig.MasemLight
        }
    }
}
