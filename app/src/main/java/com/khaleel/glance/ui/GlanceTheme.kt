package com.khaleel.glance.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

internal data class AccentPreset(val name: String, val hex: String)

internal object ThemeAccent {
    const val DEFAULT = "#087E82"
    val presets = listOf(
        AccentPreset("Ocean", DEFAULT),
        AccentPreset("Berry", "#7046A3"),
        AccentPreset("Forest", "#397B50"),
        AccentPreset("Coral", "#AF405B"),
        AccentPreset("Sunset", "#B75E2A"),
    )

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

    fun scheme(hex: String, dark: Boolean): ColorScheme {
        val requested = color(hex)
        val background = if (dark) lerp(Color(0xFF10171A), requested, 0.05f)
            else lerp(Color.White, requested, 0.035f)
        val card = if (dark) lerp(background, requested, 0.13f)
            else lerp(background, requested, 0.09f)
        val primary = readableAgainst(requested, card, dark)
        val onPrimary = if (contrast(primary, Color.White) >= contrast(primary, Color.Black)) {
            Color.White
        } else {
            Color.Black
        }
        val container = if (dark) lerp(background, primary, 0.3f) else lerp(background, primary, 0.16f)
        val onBackground = if (dark) Color(0xFFF0F5F4) else Color(0xFF172122)
        val onVariant = if (dark) Color(0xFFC3CFCD) else Color(0xFF4A5B5C)
        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onBackground,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = container,
            onSecondaryContainer = onBackground,
            tertiary = lerp(primary, if (dark) Color.White else Color.Black, 0.24f),
            onTertiary = onPrimary,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = card,
            onSurfaceVariant = onVariant,
            surfaceContainerLow = lerp(background, card, 0.45f),
            surfaceContainer = card,
            surfaceContainerHigh = lerp(card, primary, 0.05f),
            surfaceContainerHighest = lerp(card, primary, 0.08f),
            outlineVariant = lerp(onVariant, card, 0.7f),
        )
    }

    private fun readableAgainst(requested: Color, background: Color, dark: Boolean): Color {
        var result = requested
        repeat(20) {
            if (contrast(result, background) >= 4.5f) return result
            result = lerp(result, if (dark) Color.White else Color.Black, 0.12f)
        }
        return result
    }

    private fun contrast(a: Color, b: Color): Float {
        val aLuminance = a.luminance()
        val bLuminance = b.luminance()
        return (max(aLuminance, bLuminance) + 0.05f) /
            (min(aLuminance, bLuminance) + 0.05f)
    }
}
