package com.khaleel.glance.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PickerColor(val hue: Float, val saturation: Float, val brightness: Float)

internal data class AccentPreset(val name: String, val hex: String)

internal object ThemeAccent {
    const val DEFAULT = "#B9AEF5"
    val presets = listOf(
        AccentPreset("Teal", "#287F7A"),
        AccentPreset("Ocean", "#1677D2"),
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

    /** Fixed black/graphite presentation; saved custom accents are currently unused. */
    fun scheme(): ColorScheme = darkColorScheme(
        primary = Color(0xFFB9AEF5),
        onPrimary = Color(0xFF211B38),
        primaryContainer = Color(0xFF29243B),
        onPrimaryContainer = Color(0xFFEAE5FF),
        secondary = Color(0xFFB9AEF5),
        onSecondary = Color(0xFF211B38),
        secondaryContainer = Color(0xFF29243B),
        onSecondaryContainer = Color(0xFFEAE5FF),
        tertiary = Color(0xFFCCC3EF),
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
