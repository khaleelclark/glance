package com.khaleel.glance.ui

import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SpecialValue
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

    @Test fun sourceSpecialValuesNeverBecomeExactNumericGraphPoints() {
        val special = reading("patrick", 1, 40.0).copy(specialValue = SpecialValue.LOW)
        assertEquals(emptyList<Reading>(), chartPoints(listOf(special), "patrick", now))
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

    @Test fun timeChoicesShowLatestThreeSixTwelveOrTwentyFourHoursWithSixDefault() {
        assertEquals(ChartViewport(0.875, 0.125), ChartViewport.latest(3))
        assertEquals(ChartViewport(0.75, 0.25), ChartViewport.latest(6))
        assertEquals(ChartViewport(0.5, 0.5), ChartViewport.latest(12))
        assertEquals(ChartViewport(0.0, 1.0), ChartViewport.latest(24))
        assertEquals(ChartViewport.latest(6), ChartViewport())
        for (hours in listOf(3, 6, 12, 24)) assertEquals(1.0, ChartViewport.latest(hours).end, 0.000001)
    }

    @Test fun selectionWithinPresetOnlyUsesVisibleRealSamples() {
        val points = listOf(reading("patrick", 18, 100.0), reading("patrick", 12, 120.0), reading("patrick", 6, 90.0))
        assertEquals(1, nearestChartPointIndex(points, now, 240f, 0f, ChartViewport(0.4, 0.2)))
        assertEquals(2, nearestChartPointIndex(points, now, 240f, 120f, ChartViewport()))
    }

    @Test fun accessibilityReadingNavigationStopsAtPresetEdgesWithoutMovingWindow() {
        val points = listOf(reading("patrick", 8, 100.0), reading("patrick", 5, 120.0), reading("patrick", 2, 90.0))
        val latestSix = ChartViewport.latest(6)

        assertEquals(1, adjacentVisibleChartPointIndex(points, 2, now, latestSix, -1))
        assertEquals(2, adjacentVisibleChartPointIndex(points, 1, now, latestSix, 1))
        assertEquals(null, adjacentVisibleChartPointIndex(points, 1, now, latestSix, -1))
        assertEquals(null, adjacentVisibleChartPointIndex(points, 2, now, latestSix, 1))
        assertEquals(null, adjacentVisibleChartPointIndex(points, 0, now, latestSix, 1))
        assertEquals(null, adjacentVisibleChartPointIndex(points, 1, now, latestSix, 0))
    }

    @Test fun selectedReadoutClampsToBothChartEdgesAndHandlesNarrowCharts() {
        assertEquals(0f, clampedOverlayStart(0f, 120f, 300f), 0.001f)
        assertEquals(180f, clampedOverlayStart(300f, 120f, 300f), 0.001f)
        assertEquals(90f, clampedOverlayStart(150f, 120f, 300f), 0.001f)
        assertEquals(0f, clampedOverlayStart(20f, 120f, 80f), 0.001f)
    }

    @Test fun presetTicksAreOrderedAcrossDaylightSavingChange() {
        val end = Instant.parse("2026-11-01T08:47:00Z")
        val ticks = chartTimeTicks(end, ZoneId.of("America/Chicago"), ChartViewport.latest(3).span)
        assertEquals(true, ticks.zipWithNext().all { (a, b) -> a < b })
        assertEquals(true, ticks.all { it in end.minusSeconds(86400)..end })
        assertEquals(true, ticks.all { it.atZone(ZoneId.of("America/Chicago")).minute == 0 })
    }

    private fun reading(person: String, hoursAgo: Long, value: Double) = Reading(
        person, now.minusSeconds(hoursAgo * 3600), value, value, GlucoseUnit.MG_DL,
        null, Trend.STEADY, null, now,
    )
}
