package com.khaleel.glance.data

import com.khaleel.glance.core.Reading
import java.time.Duration
import java.time.Instant

/** A bounded, per-person record of validated source measurements. */
object ReadingHistory {
    private val retention = Duration.ofHours(24)
    private val futureTolerance = Duration.ofMinutes(2)
    // Covers a full day even when the source supplies a distinct reading every minute.
    internal const val MAX_POINTS = 1500

    fun merge(
        existing: List<Reading>,
        incoming: List<Reading>,
        connectionId: String,
        now: Instant,
    ): List<Reading> {
        val byTime = linkedMapOf<Instant, Reading>()
        for (reading in existing + incoming) {
            if (reading.connectionId != connectionId ||
                reading.measuredAt.isBefore(now.minus(retention)) ||
                reading.measuredAt.isAfter(now.plus(futureTolerance)) ||
                reading.valueMgDl?.let { !it.isFinite() || it <= 0.0 } != false
            ) continue
            val previous = byTime[reading.measuredAt]
            // Minimal backfill must not erase a captured provider display value at the
            // same timestamp. A corrected canonical value invalidates that old metadata.
            byTime[reading.measuredAt] = if (reading.sourceValue == null &&
                previous?.sourceValue != null && previous.valueMgDl == reading.valueMgDl
            ) reading.copy(sourceValue = previous.sourceValue, sourceUnit = previous.sourceUnit) else reading
        }
        return byTime.values.sortedBy { it.measuredAt }.takeLast(MAX_POINTS)
    }
}
