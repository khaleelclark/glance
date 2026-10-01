package com.khaleel.glance.notification

import com.khaleel.glance.core.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class NotificationSparklineTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val current = Reading("person", now, 170.0, 170.0, GlucoseUnit.MG_DL,
        null, Trend.RISING, "current", now)
    private val state = DisplayState(true, RuntimeState.RUNNING, ConnectionState.READY,
        Connection("person", "DEMO"), current, ReadingFreshness.FRESH, now, null)

    @Test fun `thumbnail uses only same-person numeric history within three hours of current reading`() {
        val previous = current.copy(measuredAt = now.minusSeconds(300), valueMgDl = 160.0, sourceValue = 160.0)
        val history = listOf(previous,
            previous.copy(connectionId = "other"),
            previous.copy(measuredAt = now.plusSeconds(30)),
            previous.copy(measuredAt = now.minusSeconds(4 * 3600)),
            previous.copy(measuredAt = now.minusSeconds(600), specialValue = SpecialValue.LOW),
            previous.copy(measuredAt = now.minusSeconds(900), valueMgDl = Double.NaN, sourceValue = Double.NaN))
        assertEquals(listOf(previous, current), NotificationSparkline.samples(state.copy(history = history)))
    }

    @Test fun `current correction takes precedence over old history at same timestamp`() {
        val old = current.copy(valueMgDl = 150.0, sourceValue = 150.0)
        assertEquals(listOf(current), NotificationSparkline.samples(state.copy(history = listOf(old, old))))
    }

    @Test fun `nonfresh stopped wrong-person and special current states have no graph`() {
        for (freshness in ReadingFreshness.entries.filter { it != ReadingFreshness.FRESH }) {
            assertTrue(NotificationSparkline.samples(state.copy(freshness = freshness)).isEmpty())
        }
        assertTrue(NotificationSparkline.samples(state.copy(monitoringEnabled = false)).isEmpty())
        assertTrue(NotificationSparkline.samples(state.copy(runtime = RuntimeState.STOPPED)).isEmpty())
        assertTrue(NotificationSparkline.samples(state.copy(person = Connection("other", "OTHER"))).isEmpty())
        assertTrue(NotificationSparkline.samples(state.copy(reading = current.copy(specialValue = SpecialValue.HIGH))).isEmpty())
    }
}
