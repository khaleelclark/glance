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

    @Test fun historyRetainsAFullDayAtOneMinuteCadence() {
        val samples = (0..1440).map { minute ->
            reading("patrick", now.minusSeconds(minute * 60L), 100.0 + minute % 20)
        }

        val merged = ReadingHistory.merge(emptyList(), samples, "patrick", now)

        assertEquals(1441, merged.size)
        assertEquals(now.minusSeconds(24 * 3600L), merged.first().measuredAt)
        assertEquals(now, merged.last().measuredAt)
    }

    @Test fun minimalBackfillPreservesCapturedDisplayValueAtTheSameMeasurementTime() {
        val captured = reading("patrick", now.minusSeconds(60), 69.0)
            .copy(sourceUnit = GlucoseUnit.MMOL_L, sourceValue = 3.9)
        val minimal = captured.copy(sourceUnit = GlucoseUnit.MG_DL, sourceValue = null, trend = Trend.UNKNOWN)
        val merged = ReadingHistory.merge(listOf(captured), listOf(minimal), "patrick", now).single()
        assertEquals(69.0, merged.valueMgDl!!, 0.0)
        assertEquals(GlucoseUnit.MMOL_L, merged.sourceUnit)
        assertEquals(3.9, merged.sourceValue!!, 0.0)
        assertEquals(Trend.UNKNOWN, merged.trend)
    }

    @Test fun correctedCanonicalHistoryDoesNotReuseAnOlderReportedDisplayValue() {
        val captured = reading("patrick", now.minusSeconds(60), 69.0)
            .copy(sourceUnit = GlucoseUnit.MMOL_L, sourceValue = 3.9)
        val corrected = captured.copy(valueMgDl = 80.0, sourceUnit = GlucoseUnit.MG_DL, sourceValue = null)
        val merged = ReadingHistory.merge(listOf(captured), listOf(corrected), "patrick", now).single()
        assertEquals(80.0, merged.valueMgDl!!, 0.0)
        assertEquals(GlucoseUnit.MG_DL, merged.sourceUnit)
        assertEquals(null, merged.sourceValue)
    }

    private fun reading(person: String, measured: Instant, value: Double) = Reading(
        person, measured, value, value, GlucoseUnit.MG_DL, null,
        Trend.STEADY, null, now,
    )
}
