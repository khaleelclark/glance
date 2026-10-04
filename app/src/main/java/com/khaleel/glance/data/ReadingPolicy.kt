package com.khaleel.glance.data

import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import java.time.Duration
import java.time.Instant

object ReadingPolicy {
    val FRESH_WINDOW: Duration = Duration.ofMinutes(5)
    fun freshness(reading: Reading?, now: Instant): ReadingFreshness {
        if (reading == null) return ReadingFreshness.ABSENT
        val age = Duration.between(reading.measuredAt, now)
        if (age < Duration.ofMinutes(-2)) return ReadingFreshness.INVALID_TIME
        if (age < FRESH_WINDOW) return ReadingFreshness.FRESH
        if (age < Duration.ofMinutes(15)) return ReadingFreshness.DELAYED
        return ReadingFreshness.STALE
    }

    /** A new connection cannot inherit the previous person's current reading. */
    fun selectCurrent(current: Reading?, candidate: Reading, selectedConnectionId: String): Reading? {
        if (candidate.connectionId != selectedConnectionId) return current?.takeIf { it.connectionId == selectedConnectionId }
        if (current == null || current.connectionId != selectedConnectionId) return candidate
        val order = candidate.measuredAt.compareTo(current.measuredAt)
        return when {
            order > 0 -> candidate
            order < 0 -> current
            candidate.valueMgDl == current.valueMgDl &&
                candidate.sourceValue == current.sourceValue &&
                candidate.specialValue == current.specialValue &&
                candidate.trend == current.trend &&
                candidate.sourceRecordId == current.sourceRecordId -> current // repeated source sample
            candidate.receivedAt >= current.receivedAt -> candidate // same-time source correction
            else -> current
        }
    }
}
