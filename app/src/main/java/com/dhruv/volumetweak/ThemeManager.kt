package com.dhruv.volumetweak

import android.content.Context
import android.graphics.Color

enum class AppTheme(
    val id: String,
    val displayName: String,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    val rootBgHex: String,
    val cardBgHex: String,
    val strokeColorHex: String,
    val subtitle: String
) {
    NOTHING_RED(
        id = "nothing_red",
        displayName = "Crimson Red",
        primaryColorHex = "#D71921",
        secondaryColorHex = "#FF4444",
        rootBgHex = "#000000",
        cardBgHex = "#0D0D0D",
        strokeColorHex = "#261616",
        subtitle = "Nothing OS Signature Style"
    ),
    CYBER_CYAN(
        id = "cyber_cyan",
        displayName = "Electric Cyan",
        primaryColorHex = "#00E5FF",
        secondaryColorHex = "#18FFFF",
        rootBgHex = "#04080B",
        cardBgHex = "#0A1318",
        strokeColorHex = "#12303A",
        subtitle = "Cyberpunk High Contrast"
    ),
    NOTHING_MONOCHROME(
        id = "monochrome",
        displayName = "Monochrome",
        primaryColorHex = "#FFFFFF",
        secondaryColorHex = "#CCCCCC",
        rootBgHex = "#000000",
        cardBgHex = "#121212",
        strokeColorHex = "#2C2C2C",
        subtitle = "Pure Minimalist White"
    ),
    AMBER_GLOW(
        id = "amber_glow",
        displayName = "Amber Glow",
        primaryColorHex = "#FF9100",
        secondaryColorHex = "#FFA726",
        rootBgHex = "#080604",
        cardBgHex = "#14100A",
        strokeColorHex = "#332210",
        subtitle = "Vintage Braun Warmth"
    ),
    MATRIX_GREEN(
        id = "matrix_green",
        displayName = "Matrix Green",
        primaryColorHex = "#00E676",
        secondaryColorHex = "#69F0AE",
        rootBgHex = "#030A05",
        cardBgHex = "#0A140D",
        strokeColorHex = "#13351C",
        subtitle = "Phosphor Hacker Terminal"
    ),
    NEON_VIOLET(
        id = "neon_violet",
        displayName = "Neon Violet",
        primaryColorHex = "#B388FF",
        secondaryColorHex = "#D1C4E9",
        rootBgHex = "#08040C",
        cardBgHex = "#120B1C",
        strokeColorHex = "#2B1642",
        subtitle = "Cyber Tokyo Synthwave"
    ),
    SUNSET_CORAL(
        id = "sunset_coral",
        displayName = "Sunset Coral",
        primaryColorHex = "#FF5722",
        secondaryColorHex = "#FF8A65",
        rootBgHex = "#0A0504",
        cardBgHex = "#170C0A",
        strokeColorHex = "#3D1A15",
        subtitle = "Solar Flare High Energy"
    ),
    NORDIC_FROST(
        id = "nordic_frost",
        displayName = "Nordic Frost",
        primaryColorHex = "#40C4FF",
        secondaryColorHex = "#80D8FF",
        rootBgHex = "#05090F",
        cardBgHex = "#0B1522",
        strokeColorHex = "#182F4D",
        subtitle = "Arctic Glacier Deep Blue"
    );

    fun getPrimaryColor(): Int = Color.parseColor(primaryColorHex)
    fun getSecondaryColor(): Int = Color.parseColor(secondaryColorHex)
    fun getRootBgColor(): Int = Color.parseColor(rootBgHex)
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
