package com.khaleel.glance.ui

import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingHistoryChartTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @Test fun chartIncludesTwentyFourHoursAndOnlySelectedPersonsValidPastReadings() {
        val included = listOf(reading("patrick", 23, 101.0), reading("patrick", 2, 115.0))
        val history = listOf(
            included[1],
            reading("patrick", 25, 99.0),
            reading("other", 1, 120.0),
            reading("patrick", -1, 130.0),
            reading("patrick", 3, Double.NaN),
            included[0],
        )

        assertEquals(included, chartPoints(history, "patrick", now))
    }

    private fun reading(person: String, hoursAgo: Long, value: Double) = Reading(
        person, now.minusSeconds(hoursAgo * 3600), value, value, GlucoseUnit.MG_DL,
        null, Trend.STEADY, null, now,
    )
}
