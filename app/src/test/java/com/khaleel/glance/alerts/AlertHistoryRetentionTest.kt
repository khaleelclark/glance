package com.khaleel.glance.alerts

import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlertHistoryRetentionTest {
    private val now = Instant.parse("2026-10-04T12:00:00Z")
    private val person = Connection("private-connection-id", "Example")

    @Test fun keepsNewestFirstDeduplicatedBoundedAndWithinThirtyDays() {
        val recent = (0..1_001).map { offset ->
            entry("event-$offset", now.minusSeconds(offset.toLong()), person)
        }
        val expired = entry("expired", now.minusSeconds(31L * 24L * 60L * 60L), person)
        val future = entry("future", now.plusSeconds(1), person)
        val duplicate = recent.last().copy(firedAt = now)

        val retained = AlertHistoryRetention.retain(recent.reversed() + expired + future + duplicate, now)

        assertEquals(AlertHistoryRetention.MAX_EVENTS, retained.size)
        assertEquals(now, retained.first().firedAt)
        assertFalse(retained.any { it.id == "expired" || it.id == "future" })
        assertEquals(retained.size, retained.map { it.id }.distinct().size)
        assertTrue(retained.zipWithNext().all { (a, b) -> !a.firedAt.isBefore(b.firedAt) })
    }

    @Test fun rejectsCrossPersonReadingIdentity() {
        val invalid = entry("event", now, person).let {
            it.copy(reading = it.reading.copy(connectionId = "someone-else"))
        }
        assertFalse(AlertHistoryRetention.valid(invalid))
        assertEquals(emptyList<AlertHistoryEntry>(), AlertHistoryRetention.retain(listOf(invalid), now))
        assertFalse(AlertHistoryRetention.valid(entry("missing", now, person).let {
            it.copy(reading = it.reading.copy(valueMgDl = null))
        }))
        assertFalse(AlertHistoryRetention.valid(entry("future-source", now, person).let {
            it.copy(reading = it.reading.copy(measuredAt = now.plusSeconds(1)))
        }))
    }

    @Test fun rejectsEntireAuthenticatedPayloadWhenAnyRowIsMalformed() {
        val rows = listOf(entry("valid", now, person), null)

        assertThrows(IllegalArgumentException::class.java) {
            AlertHistoryLoad.decode(rows.size) { rows[it] }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertHistoryLoad.decode(AlertHistoryLoad.MAX_PARSE_COUNT + 1) { entry("unused", now, person) }
        }
    }

    private fun entry(id: String, firedAt: Instant, owner: Connection) = AlertHistoryEntry(
        id = id,
        person = owner,
        kind = AlertKind.HIGH,
        reading = Reading(
            connectionId = owner.id,
            measuredAt = firedAt.minusSeconds(30),
            valueMgDl = 250.0,
            sourceValue = 250.0,
            sourceUnit = GlucoseUnit.MG_DL,
            specialValue = null,
            trend = Trend.STEADY,
            sourceRecordId = "source-$id",
            receivedAt = firedAt,
        ),
        firedAt = firedAt,
    )
}
