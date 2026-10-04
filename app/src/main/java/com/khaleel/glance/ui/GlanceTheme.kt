package com.khaleel.glance.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PickerColor(val hue: Float, val saturation: Float, val brightness: Float)

internal data class AccentPreset(val name: String, val hex: String)

internal object ThemeAccent {
    const val DEFAULT = "#1677D2"
    val presets = listOf(
        AccentPreset("Ocean", DEFAULT),
        AccentPreset("Lagoon", "#087E82"),
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

    fun pickerColor(hex: String): PickerColor {
        val normalized = normalize(hex) ?: DEFAULT
        val rgb = normalized.drop(1).toInt(16)
        val r = ((rgb shr 16) and 0xFF) / 255f
        val g = ((rgb shr 8) and 0xFF) / 255f
        val b = (rgb and 0xFF) / 255f
        val maximum = max(r, max(g, b))
        val minimum = min(r, min(g, b))
        val difference = maximum - minimum
        val hue = when {
            difference == 0f -> 0f
            maximum == r -> 60f * (((g - b) / difference) % 6f)
            maximum == g -> 60f * ((b - r) / difference + 2f)
            else -> 60f * ((r - g) / difference + 4f)
        }
        return PickerColor(
            hue = (hue + 360f) % 360f,
            saturation = if (maximum == 0f) 0f else difference / maximum,
            brightness = maximum,
        )
    }

    fun fromPicker(color: PickerColor): String {
        val hue = ((color.hue % 360f) + 360f) % 360f
        val saturation = color.saturation.coerceIn(0f, 1f)
        val brightness = color.brightness.coerceIn(0f, 1f)
        val chroma = brightness * saturation
        val sector = hue / 60f
        val secondary = chroma * (1f - kotlin.math.abs(sector % 2f - 1f))
        val (r, g, b) = when (sector.toInt()) {
            0 -> Triple(chroma, secondary, 0f)
            1 -> Triple(secondary, chroma, 0f)
            2 -> Triple(0f, chroma, secondary)
            3 -> Triple(0f, secondary, chroma)
            4 -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }
        val offset = brightness - chroma
        fun channel(value: Float) = ((value + offset) * 255f).roundToInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(channel(r), channel(g), channel(b))
    }

    fun scheme(hex: String, dark: Boolean): ColorScheme {
        val requested = color(hex)
        val background = if (dark) Color(0xFF0E171F) else Color(0xFFF5F7FA)
        val surface = if (dark) Color(0xFF15212B) else Color(0xFFFFFFFF)
        val card = if (dark) Color(0xFF1B2A35) else Color(0xFFF0F4F8)
        val primary = readableAgainst(requested, card, dark)
        val onPrimary = if (contrast(primary, Color.White) >= contrast(primary, Color.Black)) {
            Color.White
        } else {
            Color.Black
        }
        val container = if (dark) lerp(background, primary, 0.3f) else lerp(background, primary, 0.16f)
        val onBackground = if (dark) Color(0xFFF2F6FA) else Color(0xFF17212B)
        val onVariant = if (dark) Color(0xFFB8C6D1) else Color(0xFF536270)
        val outline = if (dark) Color(0xFF617280) else Color(0xFF788895)
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
            tertiaryContainer = container,
            onTertiaryContainer = onBackground,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onBackground,
            surfaceVariant = card,
            onSurfaceVariant = onVariant,
            surfaceContainerLowest = if (dark) Color(0xFF0A1218) else Color.White,
            surfaceContainerLow = surface,
            surfaceContainer = card,
            surfaceContainerHigh = lerp(card, primary, 0.05f),
            surfaceContainerHighest = lerp(card, primary, 0.08f),
            surfaceBright = if (dark) Color(0xFF263641) else Color.White,
            surfaceDim = if (dark) Color(0xFF0E171F) else Color(0xFFE2E8EE),
            surfaceTint = primary,
            inverseSurface = onBackground,
            inverseOnSurface = background,
            inversePrimary = readableAgainst(requested, onBackground, !dark),
            outline = outline,
            outlineVariant = lerp(onVariant, card, 0.7f),
            scrim = Color.Black,
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
