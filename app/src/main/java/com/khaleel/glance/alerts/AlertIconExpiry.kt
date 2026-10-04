package com.khaleel.glance.alerts

import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.data.ReadingPolicy
import java.time.Duration
import java.time.Instant

/** The system owns the timeout even when this application's process is absent. */
data class AlertIconExpiry(val deadline: Instant, val remainingMillis: Long) {
    companion object {
        fun forReading(reading: Reading?, now: Instant): AlertIconExpiry? {
            if (ReadingPolicy.freshness(reading, now) != ReadingFreshness.FRESH) return null
            val deadline = reading!!.measuredAt.plus(ReadingPolicy.FRESH_WINDOW)
            return AlertIconExpiry(deadline, Duration.between(now, deadline).toMillis().coerceAtLeast(1))
        }
    }
}
