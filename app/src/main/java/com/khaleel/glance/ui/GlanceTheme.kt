package com.khaleel.glance.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.khaleel.glance.core.AccentPresets

internal object ThemeAccent {
    const val DEFAULT = AccentPresets.DEFAULT
    val presets = AccentPresets.all
    fun selected(hex: String) = AccentPresets.selected(hex)

    fun normalize(input: String): String? {
        val digits = input.trim().removePrefix("#")
        return if (digits.matches(Regex("[0-9a-fA-F]{6}"))) "#${digits.uppercase()}" else null
    }

    fun color(hex: String): Color {
        val normalized = normalize(hex) ?: DEFAULT
        val rgb = normalized.drop(1).toInt(16)
        return Color(
            red = ((rgb shr 16) and 0xFF) / 255f,
            green = ((rgb shr 8) and 0xFF) / 255f,
            blue = (rgb and 0xFF) / 255f,
        )
    }

    /** Presets change controls and the native in-app mark; surfaces remain black/graphite. */
    fun scheme(hex: String = DEFAULT): ColorScheme {
        val accent = color(selected(hex).hex)
        val container = lerp(Color.Black, accent, .18f)
        return darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF15151A),
            primaryContainer = container,
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = Color(0xFF15151A),
            secondaryContainer = container,
            onSecondaryContainer = accent,
            tertiary = accent,
            background = Color.Black,
            onBackground = Color(0xFFF0EEF4),
            surface = Color.Black,
            onSurface = Color(0xFFF0EEF4),
            surfaceVariant = Color(0xFF18181D),
            onSurfaceVariant = Color(0xFFB8B5C0),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF141417),
            surfaceContainer = Color(0xFF19191E),
            surfaceContainerHigh = Color(0xFF222228),
            surfaceContainerHighest = Color(0xFF2B2B33),
            surfaceBright = Color(0xFF303038),
            surfaceDim = Color.Black,
            surfaceTint = Color.Transparent,
            outline = Color(0xFF77737F),
            outlineVariant = Color(0xFF36333E),
            scrim = Color.Black,
        )
    }
}
