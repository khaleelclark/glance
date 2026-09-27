package com.khaleel.glance.data

import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.Trend
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReadingPolicyTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private fun reading(at: String, person: String = "patrick", received: Instant = now) = Reading(
        person, Instant.parse(at), 112.0, 112.0, GlucoseUnit.MG_DL,
        null, Trend.STEADY, null, received,
    )

    @Test fun measurementAgeNotContactAge() {
        assertEquals(ReadingFreshness.FRESH, ReadingPolicy.freshness(reading("2026-09-27T11:55:01Z"), now))
        assertEquals(ReadingFreshness.DELAYED, ReadingPolicy.freshness(reading("2026-09-27T11:55:00Z"), now))
        assertEquals(ReadingFreshness.STALE, ReadingPolicy.freshness(reading("2026-09-27T11:45:00Z"), now))
        assertEquals(ReadingFreshness.INVALID_TIME, ReadingPolicy.freshness(reading("2026-09-27T12:02:01Z"), now))
    }

    @Test fun oldOrWrongPersonNeverReplacesCurrent() {
        val current = reading("2026-09-27T11:59:00Z")
        assertEquals(current, ReadingPolicy.selectCurrent(current, reading("2026-09-27T11:58:00Z"), "patrick"))
        assertEquals(current, ReadingPolicy.selectCurrent(current, reading("2026-09-27T12:00:00Z", "other"), "patrick"))
        assertEquals(null, ReadingPolicy.selectCurrent(current, reading("2026-09-27T12:00:00Z"), "other"))
    }

    @Test fun duplicateKeepsFirstReceiptButSourceCorrectionReplacesIt() {
        val firstReceipt = now.minusSeconds(60)
        val original = reading("2026-09-27T11:55:00Z", received = firstReceipt)
        val duplicate = reading("2026-09-27T11:55:00Z", received = now)
        assertEquals(original, ReadingPolicy.selectCurrent(original, duplicate, "patrick"))
        val corrected = duplicate.copy(valueMgDl = 113.0, sourceValue = 113.0)
        assertEquals(corrected, ReadingPolicy.selectCurrent(original, corrected, "patrick"))
    }
}
