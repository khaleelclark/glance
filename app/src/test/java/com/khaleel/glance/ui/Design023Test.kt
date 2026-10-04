package com.khaleel.glance.ui

import com.khaleel.glance.alerts.AlertHistoryEntry
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.PersonDisplayState
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.Trend
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Design023Test {
    private val now = Instant.parse("2026-10-04T18:00:00Z")

    @Test fun rosterKeepsPrimaryFirstAndRemovesDuplicateProviderEntries() {
        val primary = Connection("a", "Alex")
        val other = Connection("b", "Bailey")
        val state = state(primary).copy(people = listOf(
            PersonDisplayState(primary, reading("a", 110.0, now), ReadingFreshness.FRESH),
            PersonDisplayState(other, reading("b", 130.0, now), ReadingFreshness.FRESH),
        ))

        assertEquals(listOf("a", "b"), rosterStates(state).map { it.person.id })
    }

    @Test fun deltaUsesOnlyLatestEarlierSourceObservationAndActualInterval() {
        val current = reading("a", 118.0, now)
        val history = listOf(
            reading("a", 80.0, now.minusSeconds(12 * 60)),
            reading("a", 112.0, now.minusSeconds(5 * 60)),
            reading("other", 150.0, now.minusSeconds(60)),
        )

        assertEquals("+6 mg/dL in 5 min", readingDeltaText(current, history, GlucoseUnit.MG_DL, Locale.US))
        assertEquals("+0.3 mmol/L in 5 min", readingDeltaText(current, history, GlucoseUnit.MMOL_L, Locale.US))
    }

    @Test fun deltaIsHiddenAcrossLongGapOrMissingNumericReading() {
        val current = reading("a", 118.0, now)
        assertNull(readingDeltaText(current, listOf(reading("a", 110.0, now.minusSeconds(31 * 60))), GlucoseUnit.MG_DL, Locale.US))
        assertNull(readingDeltaText(current.copy(valueMgDl = null), emptyList(), GlucoseUnit.MG_DL, Locale.US))
    }

    @Test fun logbookGroupsNewestFirstByLocalFireDay() {
        val person = Connection("a", "Alex")
        val first = event("1", person, now.minusSeconds(60))
        val prior = event("2", person, now.minusSeconds(25 * 60 * 60))
        val groups = groupAlertHistory(listOf(prior, first), ZoneId.of("UTC"))

        assertEquals(2, groups.size)
        assertEquals("1", groups.first().second.single().id)
    }

    private fun state(person: Connection) = DisplayState(
        monitoringEnabled = true,
        runtime = RuntimeState.RUNNING,
        connection = ConnectionState.READY,
        person = person,
        reading = reading(person.id, 118.0, now),
        freshness = ReadingFreshness.FRESH,
        lastSuccessfulFetch = now,
        nextAllowedFetch = null,
    )

    private fun reading(connectionId: String, value: Double, at: Instant) = Reading(
        connectionId = connectionId,
        measuredAt = at,
        valueMgDl = value,
        sourceValue = value,
        sourceUnit = GlucoseUnit.MG_DL,
        specialValue = null,
        trend = Trend.STEADY,
        sourceRecordId = at.toString(),
        receivedAt = at,
    )

    private fun event(id: String, person: Connection, firedAt: Instant) = AlertHistoryEntry(
        id = id,
        person = person,
        kind = AlertKind.LOW,
        reading = reading(person.id, 68.0, firedAt.minusSeconds(15)),
        firedAt = firedAt,
    )
}
