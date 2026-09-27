package com.khaleel.glance.data

import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingHistoryTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @Test fun historyRejectsOtherPeopleOldSamplesAndDuplicates() {
        val first = reading("patrick", now.minusSeconds(600), 108.0)
        val corrected = first.copy(valueMgDl = 109.0, sourceValue = 109.0)
        val merged = ReadingHistory.merge(
            existing = listOf(first),
            incoming = listOf(
                reading("other", now.minusSeconds(300), 99.0),
                reading("patrick", now.minusSeconds(25 * 3600), 88.0),
                corrected,
                reading("patrick", now.minusSeconds(60), 112.0),
            ),
            connectionId = "patrick",
            now = now,
        )

        assertEquals(listOf(109.0, 112.0), merged.map { it.valueMgDl })
    }

    private fun reading(person: String, measured: Instant, value: Double) = Reading(
        person, measured, value, value, GlucoseUnit.MG_DL, null,
        Trend.STEADY, null, now,
    )
}
