package com.dhruv.volumetweak

import android.content.Context
import android.graphics.Color

enum class AppTheme(
    val id: String,
    val displayName: String,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val cardBgHex: String,
    val strokeColorHex: String,
    val subtitle: String
) {
    NOTHING_RED(
        id = "nothing_red",
        displayName = "Crimson Red",
        primaryColorHex = "#D71921",
        secondaryColorHex = "#FF4444",
        cardBgHex = "#0D0D0D",
        strokeColorHex = "#1F1F1F",
        subtitle = "Nothing OS Signature Style"
    ),
    CYBER_CYAN(
        id = "cyber_cyan",
        displayName = "Electric Cyan",
        primaryColorHex = "#00E5FF",
        secondaryColorHex = "#18FFFF",
        cardBgHex = "#0A1012",
        strokeColorHex = "#16282E",
        subtitle = "Cyberpunk High Contrast"
    ),
    NOTHING_MONOCHROME(
        id = "monochrome",
        displayName = "Monochrome",
        primaryColorHex = "#FFFFFF",
        secondaryColorHex = "#CCCCCC",
        cardBgHex = "#111111",
        strokeColorHex = "#282828",
        subtitle = "Pure Minimalist White"
    ),
    AMBER_GLOW(
        id = "amber_glow",
        displayName = "Amber Glow",
        primaryColorHex = "#FF9100",
        secondaryColorHex = "#FFA726",
        cardBgHex = "#100E0A",
        strokeColorHex = "#2E2010",
        subtitle = "Vintage Braun Warmth"
    ),
    MATRIX_GREEN(
        id = "matrix_green",
        displayName = "Matrix Green",
        primaryColorHex = "#00E676",
        secondaryColorHex = "#69F0AE",
        cardBgHex = "#0A120D",
        strokeColorHex = "#142A1A",
        subtitle = "Phosphor Hacker Terminal"
    );

    fun getPrimaryColor(): Int = Color.parseColor(primaryColorHex)
    fun getSecondaryColor(): Int = Color.parseColor(secondaryColorHex)
    fun getCardBgColor(): Int = Color.parseColor(cardBgHex)
    fun getStrokeColor(): Int = Color.parseColor(strokeColorHex)
}

object ThemeManager {
    private const val PREF_KEY_THEME = "app_theme"

    fun getCurrentTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString(PREF_KEY_THEME, AppTheme.NOTHING_RED.id)
        return AppTheme.values().firstOrNull { it.id == savedId } ?: AppTheme.NOTHING_RED
    }

    fun setTheme(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_THEME, theme.id).apply()
    }
}
