package com.khaleel.glance.ui

import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import java.time.Instant
import java.time.ZoneId
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

    @Test fun draggingSelectsNearestObservedPointByTimeEvenAwayFromDotHeight() {
        val points = chartPoints(
            listOf(reading("patrick", 18, 100.0), reading("patrick", 12, 120.0), reading("patrick", 6, 90.0)),
            "patrick", now,
        )
        assertEquals(0, nearestChartPointIndex(points, now, 240f, 62f))
        assertEquals(1, nearestChartPointIndex(points, now, 240f, 122f))
        assertEquals(2, nearestChartPointIndex(points, now, 240f, 178f))
    }

    @Test fun timeTicksUseRoundLocalClockHoursWithinOneDay() {
        val ticks = chartTimeTicks(Instant.parse("2026-09-27T21:47:00Z"), ZoneId.of("America/Chicago"))

        assertEquals(Instant.parse("2026-09-27T21:00:00Z"), ticks.last())
        assertEquals(0, ticks.last().atZone(ZoneId.of("America/Chicago")).minute)
        assertEquals(true, ticks.all { it >= Instant.parse("2026-09-26T21:47:00Z") })
    }

    private fun reading(person: String, hoursAgo: Long, value: Double) = Reading(
        person, now.minusSeconds(hoursAgo * 3600), value, value, GlucoseUnit.MG_DL,
        null, Trend.STEADY, null, now,
    )
}
