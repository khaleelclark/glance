package com.khaleel.glance.alerts

import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EncryptedAlertHistoryStoreTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    @Test fun `mixed valid and malformed authenticated rows block append until explicit clear`() {
        val storage = MemoryStorage(JSONObject().apply {
            put("version", 1)
            put("events", JSONArray().put(jsonEntry("valid", "HIGH")).put(jsonEntry("bad", "NOT_A_KIND")))
        })
        val store = EncryptedAlertHistoryStore(storage, FixedClock(now))
        val next = entry("next")

        assertTrue(store.events.value.isEmpty())
        assertThrows(IllegalStateException::class.java) { store.append(next) }
        assertEquals(0, storage.writes)

        store.clear()
        store.append(next)
        assertEquals(listOf(next), store.events.value)
        assertEquals(2, storage.writes)
    }

    private fun entry(id: String) = AlertHistoryEntry(
        id = id,
        person = Connection("patrick", "Patrick"),
        kind = AlertKind.HIGH,
        reading = Reading(
            "patrick", now.minusSeconds(30), 250.0, 250.0, GlucoseUnit.MG_DL,
            null, Trend.STEADY, "source-$id", now,
        ),
        firedAt = now,
    )

    private fun jsonEntry(id: String, kind: String) = JSONObject().apply {
        val event = entry(id)
        put("id", event.id)
        put("firedAt", event.firedAt.toString())
        put("kind", kind)
        put("person", JSONObject().put("id", event.person.id).put("name", event.person.displayName))
        put("reading", JSONObject().apply {
            put("connectionId", event.reading.connectionId)
            put("measuredAt", event.reading.measuredAt.toString())
            put("valueMgDl", event.reading.valueMgDl)
            put("sourceValue", event.reading.sourceValue)
            put("sourceUnit", event.reading.sourceUnit.name)
            put("specialValue", JSONObject.NULL)
            put("trend", event.reading.trend.name)
            put("sourceRecordId", event.reading.sourceRecordId)
            put("receivedAt", event.reading.receivedAt.toString())
        })
    }

    private class MemoryStorage(private var payload: JSONObject?) : AlertHistoryStorage {
        var writes = 0
        override fun read() = payload
        override fun write(payload: JSONObject) {
            writes++
            this.payload = payload
        }
    }

    private class FixedClock(private val now: Instant) : Clock {
        override fun now() = now
        override fun elapsedRealtimeMillis() = 0L
    }
}
