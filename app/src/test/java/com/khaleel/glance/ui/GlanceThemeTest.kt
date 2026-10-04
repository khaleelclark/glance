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
    @Test fun fixedPresentationUsesBlackAndReadableLilacControls() {
        assertEquals("#B9AEF5", ThemeAccent.DEFAULT)
        val scheme = ThemeAccent.scheme()
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Black, scheme.surface)
        assertTrue(contrast(scheme.primary, scheme.surfaceContainer) >= 4.5f)
        assertTrue(contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
        assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceContainerLow) >= 4.5f)
        assertTrue(contrast(scheme.onSurface, scheme.surfaceContainerLow) >= 7f)
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
