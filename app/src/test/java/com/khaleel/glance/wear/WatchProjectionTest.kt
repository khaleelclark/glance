package com.khaleel.glance.wear

import com.khaleel.glance.core.*
import com.khaleel.glance.wear.shared.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WatchProjectionTest {
    private val now = Instant.parse("2026-10-01T08:00:00Z")
    private val reading = Reading("selected", now.minusSeconds(60), 112.0, 112.0,
        GlucoseUnit.MG_DL, null, Trend.RISING, "sample", now)
    private val state = DisplayState(true, RuntimeState.RUNNING, ConnectionState.READY,
        Connection("selected", "Private name"), reading, ReadingFreshness.FRESH, now, null)
    private fun snapshot(s: DisplayState = state, visible: Boolean = true, account: Boolean = true,
                         unit: GlucoseUnit = GlucoseUnit.MG_DL) =
        WatchSnapshotFactory.create(s, account, visible, true, unit, 1L, now)

    @Test fun selectedReadingKeepsSourceValueArrowAndTime() {
        val s = snapshot()
        assertTrue(s.valid()); assertEquals("112", s.value); assertEquals("↗", s.arrow)
        assertEquals(reading.measuredAt.toEpochMilli(), s.measuredAtMillis)
        assertEquals("112↗", WatchPresentation.from(s, now.toEpochMilli()).shortText)
    }
    @Test fun mmolUsesSharedConversion() {
        assertEquals("6.2", snapshot(unit = GlucoseUnit.MMOL_L).value)
    }
    @Test fun noIdentityOrValueSurvivesPrivacyStopOrWrongPerson() {
        val variants = listOf(snapshot(visible = false), snapshot(account = false),
            snapshot(state.copy(monitoringEnabled = false)),
            snapshot(state.copy(reading = reading.copy(connectionId = "other"))),
            snapshot(state.copy(connection = ConnectionState.SIGN_IN_REQUIRED)))
        variants.forEach { assertTrue(it.valid()); assertEquals("", it.value); assertEquals(0L, it.measuredAtMillis) }
    }
    @Test fun duplicateReceiptDoesNotMakeMeasurementFresh() {
        val s = snapshot(state.copy(reading = reading.copy(measuredAt = now.minusSeconds(360), receivedAt = now)))
        val p = WatchPresentation.from(s, now.toEpochMilli())
        assertFalse(p.fresh); assertEquals("Delayed", p.shortText); assertEquals("", p.arrow)
    }
    @Test fun transportLeaseRemovesEvenFreshNumericData() {
        val s = snapshot().copy(measuredAtMillis = now.plusSeconds(60).toEpochMilli())
        val p = WatchPresentation.from(s, now.plusSeconds(300).toEpochMilli())
        assertEquals("Open phone", p.heading); assertEquals("", p.value)
    }
    @Test fun futureClockAndUnknownArrowFailHonestly() {
        val s = snapshot().copy(measuredAtMillis = now.plusSeconds(121).toEpochMilli())
        assertEquals("Time unknown", WatchPresentation.from(s, now.toEpochMilli()).heading)
        assertEquals("", snapshot(state.copy(reading = reading.copy(trend = Trend.UNKNOWN))).arrow)
    }
    @Test fun specialSourceValueHasNoInventedTrendOrUnit() {
        val s = snapshot(state.copy(reading = reading.copy(specialValue = SpecialValue.LOW)))
        assertEquals("LOW", s.value); assertEquals("", s.arrow); assertEquals("", s.unit)
    }
    @Test fun malformedPayloadCannotShowHealthContent() {
        assertFalse(snapshot().copy(value = "email@example.com").valid())
        assertFalse(snapshot(visible = false).copy(value = "112").valid())
        assertFalse(snapshot().copy(arrow = "?").valid())
        assertEquals("", WatchPresentation.from(snapshot().copy(revision = 0), now.toEpochMilli()).value)
    }
}
