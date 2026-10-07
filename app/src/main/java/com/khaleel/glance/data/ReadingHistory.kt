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
            byTime[reading.measuredAt] = preserveReportedValue(previous, reading)
        }
        return byTime.values.sortedBy { it.measuredAt }.takeLast(MAX_POINTS)
    }

    // Minimal backfill must not erase genuine provider display metadata, including
    // duplicates within the first response. A canonical correction invalidates it.
    internal fun preserveReportedValue(previous: Reading?, incoming: Reading): Reading =
        if (incoming.sourceValue == null && previous?.sourceValue != null &&
            previous.connectionId == incoming.connectionId &&
            previous.measuredAt == incoming.measuredAt && previous.valueMgDl == incoming.valueMgDl
        ) incoming.copy(sourceValue = previous.sourceValue, sourceUnit = previous.sourceUnit) else incoming
}
