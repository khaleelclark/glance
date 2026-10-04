package com.khaleel.glance.ui

import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.AlertRule
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.SpecialValue
import com.khaleel.glance.core.Trend
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingRangePresentationTest {
    private val config = AlertConfig.defaults()
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private fun reading(value: Double? = 113.0, special: SpecialValue? = null) =
        Reading("synthetic", now, value, null, GlucoseUnit.MG_DL, special, Trend.STEADY, null, now)

    @Test fun freshValueUsesTheSameStrictBoundariesAsTheGraph() {
        for ((value, expected) in listOf(69.0 to ChartBand.LOW, 70.0 to ChartBand.IN_RANGE,
            240.0 to ChartBand.IN_RANGE, 241.0 to ChartBand.HIGH)) {
            assertEquals(expected, currentReadingBand(reading(value), ReadingFreshness.FRESH, config))
        }
    }

    @Test fun enabledCustomLimitsAndCanonicalUnitsAgreeWithGraph() {
        val custom = AlertConfig(config.rules + mapOf(AlertKind.LOW to AlertRule(true, 85), AlertKind.HIGH to AlertRule(true, 210)))
        val sourceMmol = reading(220.0).copy(sourceUnit = GlucoseUnit.MMOL_L, sourceValue = 12.2)
        assertEquals(ChartBand.HIGH, currentReadingBand(sourceMmol, ReadingFreshness.FRESH, custom))
        assertEquals(ChartBand.LOW, currentReadingBand(reading(80.0), ReadingFreshness.FRESH, custom))
    }

    @Test fun oldOrInvalidReadingsNeverGetCurrentRangeColors() {
        for (freshness in ReadingFreshness.entries.filter { it != ReadingFreshness.FRESH }) {
            assertNull(currentReadingBand(reading(250.0), freshness, config))
        }
        for (value in listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertNull(currentReadingBand(reading(value), ReadingFreshness.FRESH, config))
        }
        assertNull(currentReadingBand(null, ReadingFreshness.FRESH, config))
    }

    @Test fun sourceSpecialValuesStayCategoricalWithoutInventedNumbers() {
        assertEquals(ChartBand.LOW, currentReadingBand(reading(null, SpecialValue.LOW), ReadingFreshness.FRESH, config))
        assertEquals(ChartBand.HIGH, currentReadingBand(reading(null, SpecialValue.HIGH), ReadingFreshness.FRESH, config))
        assertNull(currentReadingBand(reading(null, SpecialValue.UNAVAILABLE), ReadingFreshness.FRESH, config))
    }
}
