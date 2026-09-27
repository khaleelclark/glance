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
    @Test fun acceptsAndNormalizesOnlySixDigitHexColors() {
        assertEquals("#12ABEF", ThemeAccent.normalize("12abef"))
        assertEquals("#12ABEF", ThemeAccent.normalize(" #12AbEf "))
        assertNull(ThemeAccent.normalize("#FFF"))
        assertNull(ThemeAccent.normalize("#12345678"))
        assertNull(ThemeAccent.normalize("#XYZXYZ"))
    }

    @Test fun extremeCustomColorsKeepPrimaryReadableOnBothThemes() {
        for (dark in listOf(false, true)) {
            for (hex in listOf("#FFFFFF", "#000000", "#FFFF00", "#087E82")) {
                val scheme = ThemeAccent.scheme(hex, dark)
                assertTrue("$hex dark=$dark", contrast(scheme.primary, scheme.surfaceContainer) >= 4.5f)
                assertTrue("$hex dark=$dark", contrast(scheme.primary, scheme.onPrimary) >= 4.5f)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val aL = a.luminance()
        val bL = b.luminance()
        return (max(aL, bL) + 0.05f) / (min(aL, bL) + 0.05f)
    }
}
