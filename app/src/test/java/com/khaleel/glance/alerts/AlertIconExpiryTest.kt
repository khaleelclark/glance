package com.khaleel.glance.alerts

import com.khaleel.glance.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AlertIconExpiryTest {
    private val now = Instant.parse("2026-10-04T18:00:00Z")
    private fun reading(time: Instant) = Reading(
        "synthetic", time, 240.0, 240.0, GlucoseUnit.MG_DL, null, Trend.STEADY, null, time,
    )

    @Test fun remainingTimeoutUsesMeasurementTimeRatherThanPostOrFetchTime() {
        val sourceTime = now.minusSeconds(294)
        val expiry = AlertIconExpiry.forReading(reading(sourceTime).copy(receivedAt = now), now)!!
        assertEquals(sourceTime.plusSeconds(300), expiry.deadline)
        assertEquals(6_000L, expiry.remainingMillis)
        assertEquals(1L, AlertIconExpiry.forReading(reading(now.minusMillis(299_999)), now)!!.remainingMillis)
    }

    @Test fun expiredAbsentOrInvalidTimesHaveNoNumericLifetime() {
        assertNull(AlertIconExpiry.forReading(null, now))
        assertNull(AlertIconExpiry.forReading(reading(now.minusSeconds(300)), now))
        assertNull(AlertIconExpiry.forReading(reading(now.plusSeconds(121)), now))
    }

    @Test fun newSampleWithSameValueRenewsDeadlineButDuplicateDoesNot() {
        val first = reading(now.minusSeconds(60))
        val duplicate = first.copy(receivedAt = now)
        assertEquals(AlertIconExpiry.forReading(first, now), AlertIconExpiry.forReading(duplicate, now))
        val next = reading(now)
        assertEquals(60_000L, AlertIconExpiry.forReading(next, now)!!.remainingMillis -
            AlertIconExpiry.forReading(first, now)!!.remainingMillis)
    }
}
