package com.khaleel.glance.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceThemeTest {
    @Test fun defaultPresentationUsesBlackAndReadableLilacControls() {
        assertEquals("#B9AEF5", ThemeAccent.DEFAULT)
        val scheme = ThemeAccent.scheme()
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertTrue(contrast(scheme.primary, scheme.surfaceContainer) >= 4.5f)
        assertTrue(contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
        assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceContainerLow) >= 4.5f)
        assertTrue(contrast(scheme.onSurface, scheme.surfaceContainerLow) >= 7f)
    }

    @Test fun everyPresetKeepsBlackSurfacesAndReadableControls() {
        for (preset in ThemeAccent.presets) {
            val scheme = ThemeAccent.scheme(preset.hex)
            assertEquals(Color.Black, scheme.background)
            assertEquals(Color.Black, scheme.surface)
            assertEquals(ThemeAccent.color(preset.hex), scheme.primary)
            assertTrue(preset.name, contrast(scheme.primary, scheme.surfaceContainer) >= 4.5f)
            assertTrue(preset.name, contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
            assertTrue(preset.name, contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        }
    }

    @Test fun inactiveLegacyOrInvalidColorsFallBackWithoutRevivingCustomThemes() {
        for (hex in listOf("#287F7A", "#000000", "#FFFFFF", "invalid")) {
            assertEquals(ThemeAccent.DEFAULT, ThemeAccent.selected(hex).hex)
            assertEquals(ThemeAccent.scheme().primary, ThemeAccent.scheme(hex).primary)
        }
        assertEquals("Teal", ThemeAccent.selected(" #79d3c5 ").name)
    }

    @Test fun semanticGlucoseColorsStayIndependentOfAccentAndKeepNeutralFallback() {
        val neutral = ThemeAccent.scheme().onSurface
        assertEquals(neutral, readingValueColor(null, neutral))
        assertEquals(Color(0xFF8BDC9F), readingValueColor(ChartBand.IN_RANGE, neutral))
        assertEquals(Color(0xFFFF9B9B), readingValueColor(ChartBand.LOW, neutral))
        assertEquals(Color(0xFFFFD967), readingValueColor(ChartBand.HIGH, neutral))
        for (band in ChartBand.entries) {
            assertTrue(contrast(readingValueColor(band, neutral), ThemeAccent.scheme().surfaceContainerLow) >= 4.5f)
            assertTrue(contrast(Color(0xFF15151A), readingValueColor(band, neutral)) >= 7f)
        }
    }

    @Test fun alertBadgesUseRecordedLowHighCategoryAndReadableText() {
        val low = alertBadgePalette(com.khaleel.glance.alerts.AlertKind.LOW)
        val high = alertBadgePalette(com.khaleel.glance.alerts.AlertKind.HIGH)
        assertEquals(low, alertBadgePalette(com.khaleel.glance.alerts.AlertKind.URGENT_LOW))
        assertEquals(high, alertBadgePalette(com.khaleel.glance.alerts.AlertKind.URGENT_HIGH))
        assertTrue(low.border.red > low.border.green)
        assertTrue(high.border.red > high.border.blue && high.border.green > high.border.blue)
        for (badge in listOf(low, high)) {
            assertTrue(contrast(badge.foreground, badge.background) >= 7f)
            assertTrue(contrast(badge.border, ThemeAccent.scheme().surface) >= 4.5f)
        }
    }

    @Test fun acceptsAndNormalizesOnlySixDigitHexColors() {
        assertEquals("#12ABEF", ThemeAccent.normalize("12abef"))
        assertEquals("#12ABEF", ThemeAccent.normalize(" #12AbEf "))
        assertNull(ThemeAccent.normalize("#FFF"))
        assertNull(ThemeAccent.normalize("#12345678"))
        assertNull(ThemeAccent.normalize("#XYZXYZ"))
    }


    @Test fun wheelColorRoundTripsPresetsAndNeutralColors() {
        for (hex in ThemeAccent.presets.map { it.hex } + listOf("#FFFFFF", "#000000", "#808080", "#FF00FF")) {
            assertEquals(hex, ThemeAccent.fromPicker(ThemeAccent.pickerColor(hex)))
        }
    }

    @Test fun wheelAndBrightnessCanReachPrimaryAndDarkColors() {
        assertEquals("#FF0000", ThemeAccent.fromPicker(PickerColor(0f, 1f, 1f)))
        assertEquals("#00FF00", ThemeAccent.fromPicker(PickerColor(120f, 1f, 1f)))
        assertEquals("#0000FF", ThemeAccent.fromPicker(PickerColor(240f, 1f, 1f)))
        assertEquals("#800000", ThemeAccent.fromPicker(PickerColor(0f, 1f, 128f / 255f)))
    }

    private fun contrast(a: Color, b: Color): Float {
        val aL = a.luminance()
        val bL = b.luminance()
        return (max(aL, bL) + 0.05f) / (min(aL, bL) + 0.05f)
    }
}
