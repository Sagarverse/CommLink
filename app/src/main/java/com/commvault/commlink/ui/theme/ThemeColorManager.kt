package com.commvault.commlink.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// ── Dynamic Primary Color CompositionLocals ─────────────────────────────
val LocalPrimaryColor = compositionLocalOf { PrimaryEmerald }
val LocalPrimaryLight = compositionLocalOf { TertiaryMint }
val LocalPrimaryDark = compositionLocalOf { Color(0xFF027A48) }
val LocalPrimarySoft = compositionLocalOf { Color(0xFFD1FADF) }

// ── Curated Color Palette ───────────────────────────────────────────────
data class ThemeColorOption(
    val name: String,
    val hex: Long,
    val color: Color = Color(hex)
)

val ThemeColorPalette = listOf(
    ThemeColorOption("Emerald",         0xFF039855),
    ThemeColorOption("Ocean Blue",      0xFF2563EB),
    ThemeColorOption("Royal Purple",    0xFF7C3AED),
    ThemeColorOption("Sunset Orange",   0xFFEA580C),
    ThemeColorOption("Rose Pink",       0xFFE11D48),
    ThemeColorOption("Crimson Red",     0xFFDC2626),
    ThemeColorOption("Amber Gold",      0xFFD97706),
    ThemeColorOption("Teal Cyan",       0xFF0891B2),
    ThemeColorOption("Electric Indigo", 0xFF4F46E5),
    ThemeColorOption("Charcoal",        0xFF374151)
)

// ── Color Derivation Helpers ────────────────────────────────────────────
fun deriveLightVariant(base: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), hsl)
    hsl[1] = (hsl[1] * 0.85f).coerceIn(0f, 1f)
    hsl[2] = (hsl[2] + 0.15f).coerceIn(0f, 0.75f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun deriveDarkVariant(base: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), hsl)
    hsl[2] = (hsl[2] - 0.15f).coerceIn(0.1f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

fun deriveSoftVariant(base: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(base.toArgb(), hsl)
    hsl[1] = (hsl[1] * 0.4f).coerceIn(0f, 1f)
    hsl[2] = 0.92f
    return Color(ColorUtils.HSLToColor(hsl))
}

fun hexToColor(hex: String): Color? {
    return try {
        if (hex.isBlank()) null
        else Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    } catch (e: Exception) {
        null
    }
}

fun colorToHex(color: Color): String {
    val argb = color.toArgb()
    return String.format("%06X", argb and 0xFFFFFF)
}
