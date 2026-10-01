package com.khaleel.glance.widget

import com.khaleel.glance.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class WidgetContentTest {
    private val now = Instant.parse("2026-10-01T11:09:00Z")
    private val reading = Reading("selected", now.minusSeconds(60), 130.0, 130.0,
        GlucoseUnit.MG_DL, null, Trend.STEADY, "current", now)
    private val previous = reading.copy(measuredAt = reading.measuredAt.minusSeconds(420),
        valueMgDl = 137.0, sourceValue = 137.0, sourceRecordId = "previous")
    private val state = DisplayState(true, RuntimeState.RUNNING, ConnectionState.READY,
        Connection("selected", "DEMO · Example"), reading, ReadingFreshness.FRESH, now, null, listOf(previous))
    private fun format(s: DisplayState = state, visible: Boolean = true, account: Boolean = true,
        unit: GlucoseUnit = GlucoseUnit.MG_DL, at: Instant = now) =
        WidgetContentFormatter.format(s, unit, visible, account, at, ZoneOffset.UTC, Locale.US)

    @Test fun `source reading and actual delta interval appear with absolute measurement time`() {
        val content = format()
        assertEquals("130", content.value)
        assertEquals("mg/dL", content.unit)
        assertEquals("→", content.arrow)
        assertEquals("-7 / 7 min", content.delta)
        assertEquals("Reading 11:08 AM", content.measured)
        assertTrue(content.accessibility.contains("steady"))
    }

    @Test fun `hidden widgets never send name reading time or history`() {
        val content = format(visible = false)
        assertEquals("Hidden", content.value)
        assertTrue(content.samples.isEmpty())
        assertFalse(content.accessibility.contains("Example"))
        assertFalse(content.accessibility.contains("130"))
        assertFalse(content.accessibility.contains("11:08"))
    }

    @Test fun `signed out stopped and starting widgets never resemble a current reading`() {
        assertEquals("Sign in", format(account = false).value)
        for (s in listOf(state.copy(monitoringEnabled = false), state.copy(runtime = RuntimeState.STOPPED),
            state.copy(runtime = RuntimeState.STARTING), state.copy(runtime = RuntimeState.UNABLE_TO_START))) {
            val content = format(s)
            assertNotEquals("130", content.value)
            assertTrue(content.samples.isEmpty())
        }
    }

    @Test fun `wrong-person reading and history are excluded`() {
        assertEquals("Waiting", format(state.copy(reading = reading.copy(connectionId = "other"))).value)
        val content = format(state.copy(history = listOf(previous.copy(connectionId = "other"))))
        assertEquals(listOf(reading), content.samples)
        assertNull(content.delta)
    }

    @Test fun `freshness is recomputed from source even when cached state claims fresh`() {
        assertEquals("Delayed", format(at = reading.measuredAt.plusSeconds(5 * 60)).value)
        val stale = format(at = reading.measuredAt.plusSeconds(15 * 60))
        assertEquals("Stale", stale.value)
        assertTrue(stale.measured.startsWith("Last: 130 mg/dL"))
        assertTrue(stale.samples.isEmpty())
        assertEquals("", stale.arrow)
    }

    @Test fun `future timestamps are not shown as fresh`() {
        val content = format(state.copy(reading = reading.copy(measuredAt = now.plusSeconds(121))))
        assertEquals("Time unknown", content.value)
        assertTrue(content.samples.isEmpty())
    }

    @Test fun `graph respects source corrections time window and numeric validity`() {
        val incorrect = previous.copy(sourceValue = 200.0, receivedAt = now.minusSeconds(30))
        val samples = WidgetContentFormatter.history(state.copy(history = listOf(incorrect, previous,
            reading.copy(sourceValue = 150.0), previous.copy(connectionId = "other"),
            previous.copy(measuredAt = now.plusSeconds(30)),
            previous.copy(measuredAt = reading.measuredAt.minusSeconds(4 * 3600 + 1)),
            previous.copy(measuredAt = now.minusSeconds(600), specialValue = SpecialValue.LOW),
            previous.copy(measuredAt = now.minusSeconds(900), sourceValue = Double.NaN))))
        assertEquals(listOf(previous, reading), samples)
    }

    @Test fun `graph leaves long source gaps unjoined and delta disappears`() {
        val old = previous.copy(measuredAt = reading.measuredAt.minusSeconds(901))
        assertFalse(WidgetHistoryGraph.joined(old, reading))
        assertTrue(WidgetHistoryGraph.joined(previous, reading))
        assertNull(format(state.copy(history = listOf(old))).delta)
    }

    @Test fun `special and unknown source trends do not invent numbers or trend`() {
        val special = format(state.copy(reading = reading.copy(specialValue = SpecialValue.HIGH)))
        assertEquals("HIGH", special.value)
        assertEquals("", special.arrow)
        assertTrue(special.samples.isEmpty())
        assertNull(special.delta)
        assertEquals("", format(state.copy(reading = reading.copy(trend = Trend.UNKNOWN))).arrow)
    }

    @Test fun `mmol uses authoritative source value and shows unit`() {
        val mmol = reading.copy(sourceUnit = GlucoseUnit.MMOL_L, sourceValue = 7.3)
        val content = format(state.copy(reading = mmol, history = emptyList()), unit = GlucoseUnit.MMOL_L)
        assertEquals("7.3", content.value)
        assertEquals("mmol/L", content.unit)
    }

    @Test fun `connection state remains separate from fresh value`() {
        val offline = format(state.copy(connection = ConnectionState.OFFLINE))
        assertEquals("130", offline.value)
        assertEquals("Offline", offline.note)
        assertEquals("Sign in", format(state.copy(connection = ConnectionState.SIGN_IN_REQUIRED)).value)
        assertEquals("No share", format(state.copy(connection = ConnectionState.CONNECTION_REMOVED)).value)
    }

    @Test fun `delta preserves intervals shorter than a minute`() {
        val content = format(state.copy(history = listOf(previous.copy(measuredAt = reading.measuredAt.minusSeconds(45)))))
        assertEquals("-7 / 45 sec", content.delta)
    }
}
