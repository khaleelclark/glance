package com.khaleel.glance.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.SpecialValue

/** Only a fresh source observation can describe the current glucose range. */
internal fun currentReadingBand(reading: Reading?, freshness: ReadingFreshness, alerts: AlertConfig): ChartBand? {
    if (reading == null || freshness != ReadingFreshness.FRESH) return null
    return when (reading.specialValue) {
        SpecialValue.LOW -> ChartBand.LOW
        SpecialValue.HIGH -> ChartBand.HIGH
        SpecialValue.UNAVAILABLE -> null
        null -> reading.valueMgDl?.takeIf { it.isFinite() && it > 0.0 }?.let { chartLimits(alerts).band(it) }
    }
}

internal fun readingBandLabel(band: ChartBand): String = when (band) {
    ChartBand.LOW -> "Below Low limit"
    ChartBand.IN_RANGE -> "In range"
    ChartBand.HIGH -> "Above High limit"
}

internal data class ReadingRangePalette(val foreground: Color, val container: Color, val onContainer: Color)

@Composable
internal fun readingRangePalette(band: ChartBand?): ReadingRangePalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < .5f
    return when (band) {
        ChartBand.IN_RANGE -> if (dark) ReadingRangePalette(scheme.onSurface, scheme.surfaceContainerLow, scheme.onSurface)
            else ReadingRangePalette(Color(0xFF176B38), Color(0xFFE4F5E9), Color(0xFF123A22))
        ChartBand.HIGH -> if (dark) ReadingRangePalette(Color(0xFFFFD967), scheme.surfaceContainerLow, Color(0xFFFFF0B4))
            else ReadingRangePalette(Color(0xFF755600), Color(0xFFFFF3C4), Color(0xFF453200))
        ChartBand.LOW -> if (dark) ReadingRangePalette(Color(0xFFFF9B9B), scheme.surfaceContainerLow, Color(0xFFFFE0E3))
            else ReadingRangePalette(Color(0xFFB31F32), Color(0xFFFFE8EB), Color(0xFF5E1320))
        null -> ReadingRangePalette(scheme.onSurface, scheme.surfaceContainerLow, scheme.onSurface)
    }
}

/** Semantic glucose colors are independent of the user-selected control accent. */
internal fun readingValueColor(band: ChartBand?, neutral: Color): Color = when (band) {
    ChartBand.IN_RANGE -> Color(0xFF22C55E)
    ChartBand.LOW -> Color(0xFFFF9B9B)
    ChartBand.HIGH -> Color(0xFFFFD967)
    null -> neutral
}
