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

    @Test fun zoomKeepsTimeUnderFingersAndPansInVisibleTimeUnits() {
        val original = ChartViewport(0.5, 0.25)
        val zoomed = original.transform(0.0, 2.0, 0.3)
        assertEquals(original.start + original.span * 0.3, zoomed.start + zoomed.span * 0.3, 0.000001)
        val panned = zoomed.transform(0.5, 1.0, 0.5)
        assertEquals(zoomed.start - zoomed.span / 2, panned.start, 0.000001)
    }

    @Test fun viewportStopsAtRetainedDayAndOneHourZoomLimit() {
        assertEquals(ChartViewport(0.0, 1.0), ChartViewport().transform(0.0, 0.0001, 0.5))
        val minimum = ChartViewport().transform(0.0, 1000.0, 0.5)
        assertEquals(1.0 / 24, minimum.span, 0.000001)
        assertEquals(0.0, minimum.transform(1000.0, 1.0, 0.5).start, 0.000001)
        assertEquals(1.0, minimum.transform(-1000.0, 1.0, 0.5).end, 0.000001)
    }

    @Test fun selectionAtZoomOnlyUsesVisibleRealSamples() {
        val points = listOf(reading("patrick", 18, 100.0), reading("patrick", 12, 120.0), reading("patrick", 6, 90.0))
        assertEquals(1, nearestChartPointIndex(points, now, 240f, 0f, ChartViewport(0.4, 0.2)))
        assertEquals(null, nearestChartPointIndex(points, now, 240f, 120f, ChartViewport()))
    }

    @Test fun malformedGestureDoesNotMoveViewport() {
        val viewport = ChartViewport()
        assertEquals(viewport, viewport.transform(Double.NaN, 1.0, 0.5))
        assertEquals(viewport, viewport.transform(0.0, 0.0, 0.5))
        assertEquals(viewport, viewport.transform(0.0, Double.POSITIVE_INFINITY, 0.5))
    }

    @Test fun zoomedTicksAreOrderedAcrossDaylightSavingChange() {
        val end = Instant.parse("2026-11-01T08:47:00Z")
        val ticks = chartTimeTicks(end, ZoneId.of("America/Chicago"), 1.0 / 24)
        assertEquals(true, ticks.zipWithNext().all { (a, b) -> a < b })
        assertEquals(true, ticks.all { it in end.minusSeconds(86400)..end })
        assertEquals(true, ticks.all { it.atZone(ZoneId.of("America/Chicago")).minute % 30 == 0 })
    }

    private fun reading(person: String, hoursAgo: Long, value: Double) = Reading(
        person, now.minusSeconds(hoursAgo * 3600), value, value, GlucoseUnit.MG_DL,
        null, Trend.STEADY, null, now,
    )
}
