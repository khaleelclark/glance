package com.khaleel.glance.wear.complication

import com.khaleel.glance.wear.shared.WatchSnapshot
import com.khaleel.glance.wear.shared.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearComplicationFormatterTest {
    @Test fun `fresh short text is bounded and timeline transitions to delayed`() {
        val result = WearComplicationFormatter.format(reading(measuredAgeMinutes = 1), NOW)

        assertEquals("Open phone", result.defaultText)
        assertEquals(2, result.frames.size)
        assertEquals("112→", result.frames[0].shortText)
        assertTrue(result.frames[0].shortText.length <= 7)
        assertEquals("Delayed", result.frames[1].shortText)
        assertTrue(result.frames[1].longText.contains("Last 112 mg/dL"))
        assertEquals(NOW + WatchSnapshot.LEASE_MILLIS, result.frames.last().endMillis)
    }

    @Test fun `already delayed reading is explicitly historical`() {
        val result = WearComplicationFormatter.format(reading(measuredAgeMinutes = 7), NOW)
        assertEquals(1, result.frames.size)
        assertEquals("Delayed", result.frames.single().shortText)
        assertTrue(result.frames.single().longText.contains("Last"))
        assertFalse(result.frames.single().shortText.any(Char::isDigit))
    }

    @Test fun `transport expiry yields status only with zero glucose frames`() {
        val expired = reading(sentAt = NOW - WatchSnapshot.LEASE_MILLIS)
        val result = WearComplicationFormatter.format(expired, NOW)
        assertEquals("Open phone", result.defaultText)
        assertTrue(result.frames.isEmpty())
    }

    @Test fun `future timestamp is fail closed`() {
        val result = WearComplicationFormatter.format(
            reading(measuredAt = NOW + WatchSnapshot.FUTURE_TOLERANCE_MILLIS + 1),
            NOW,
        )
        assertEquals("Time unknown", result.defaultText)
        assertTrue(result.frames.isEmpty())
    }

    @Test fun `stopped tombstone never exposes value`() {
        val result = WearComplicationFormatter.format(
            WatchSnapshot(5, NOW, WatchStatus.STOPPED),
            NOW,
        )
        assertEquals("Stopped", result.defaultText)
        assertTrue(result.frames.isEmpty())
    }

    private fun reading(
        sentAt: Long = NOW,
        measuredAgeMinutes: Int = 1,
        measuredAt: Long = sentAt - measuredAgeMinutes * 60_000L,
    ) = WatchSnapshot(
        revision = 7,
        sentAtMillis = sentAt,
        status = WatchStatus.READING,
        measuredAtMillis = measuredAt,
        value = "112",
        unit = "mg/dL",
        arrow = "→",
        trend = "Steady",
        notificationsEnabled = true,
    )

    companion object { const val NOW = 2_000_000_000_000L }
}
