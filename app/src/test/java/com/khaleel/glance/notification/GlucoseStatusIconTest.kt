package com.khaleel.glance.notification

import com.khaleel.glance.core.*
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlucoseStatusIconTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z")
    private val reading = Reading(
        "synthetic", now, 112.0, 112.0, GlucoseUnit.MG_DL,
        null, Trend.STEADY, "fixture", now,
    )
    private val state = DisplayState(
        true, RuntimeState.RUNNING, ConnectionState.READY, Connection("synthetic", "DEMO"),
        reading, ReadingFreshness.FRESH, now, null,
    )

    @Test fun `fresh value uses chosen unit including decimals and three digits`() {
        assertEquals("112", GlucoseStatusIcon.text(state, GlucoseUnit.MG_DL, true))
        assertEquals("6.2", GlucoseStatusIcon.text(state, GlucoseUnit.MMOL_L, true))
        assertEquals("305", GlucoseStatusIcon.text(state.copy(reading = reading.copy(
            valueMgDl = 305.0, sourceValue = 305.0,
        )), GlucoseUnit.MG_DL, true))
    }

    @Test fun `old absent or invalid timestamp never exposes a current number`() {
        for (freshness in ReadingFreshness.entries.filter { it != ReadingFreshness.FRESH }) {
            assertNull(GlucoseStatusIcon.text(state.copy(freshness = freshness), GlucoseUnit.MG_DL, true))
        }
        assertNull(GlucoseStatusIcon.text(state.copy(reading = null), GlucoseUnit.MG_DL, true))
    }

    @Test fun `disabled paused or nonrunning monitoring uses ordinary icon`() {
        assertNull(GlucoseStatusIcon.text(state, GlucoseUnit.MG_DL, false))
        assertNull(GlucoseStatusIcon.text(state.copy(monitoringEnabled = false), GlucoseUnit.MG_DL, true))
        for (runtime in RuntimeState.entries.filter { it != RuntimeState.RUNNING }) {
            assertNull(GlucoseStatusIcon.text(state.copy(runtime = runtime), GlucoseUnit.MG_DL, true))
        }
    }

    @Test fun `hidden lock screen values suppress the original number icon in both units`() {
        for (unit in GlucoseUnit.entries) {
            assertNull(GlucoseStatusIcon.text(state, unit, true, lockScreenVisible = false))
        }
        assertEquals("112", GlucoseStatusIcon.text(state, GlucoseUnit.MG_DL, true, lockScreenVisible = true))
    }

    @Test fun `special nonfinite and excessively long numbers fall back`() {
        for (special in SpecialValue.entries) {
            assertNull(GlucoseStatusIcon.text(state.copy(reading = reading.copy(specialValue = special)), GlucoseUnit.MG_DL, true))
        }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 12345.0)) {
            assertNull(GlucoseStatusIcon.text(state.copy(reading = reading.copy(
                valueMgDl = value, sourceValue = value,
            )), GlucoseUnit.MG_DL, true))
        }
    }
}
