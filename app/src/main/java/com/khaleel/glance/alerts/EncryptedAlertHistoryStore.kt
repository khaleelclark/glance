package com.khaleel.glance.alerts

import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SpecialValue
import com.khaleel.glance.core.Trend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/** Encrypted, atomic, no-backup alarm history. It is never included in diagnostics. */
class EncryptedAlertHistoryStore(
    private val encrypted: SecureFileStore,
    private val clock: Clock,
) : AlertHistoryPersistence {
    private var loadFailure: Exception? = null
    private val mutableEvents = MutableStateFlow(try {
        load()
    } catch (error: Exception) {
        loadFailure = error
        emptyList()
    })
    override val events: StateFlow<List<AlertHistoryEntry>> = mutableEvents.asStateFlow()

    @Synchronized
    override fun append(entry: AlertHistoryEntry) {
        loadFailure?.let { throw IllegalStateException("Could not read alarm history", it) }
        require(AlertHistoryRetention.valid(entry)) { "Alert history identity does not match" }
        val retained = AlertHistoryRetention.retain(listOf(entry) + mutableEvents.value, clock.now())
        write(retained)
        mutableEvents.value = retained
    }

    @Synchronized
    override fun clear() {
        // A checked atomic replacement avoids treating a failed file deletion as success.
        // It also repairs unreadable history only when the user explicitly clears it.
        write(emptyList())
        loadFailure = null
        mutableEvents.value = emptyList()
    }

    private fun write(entries: List<AlertHistoryEntry>) {
        encrypted.write(FILE_NAME, JSONObject().apply {
            put("version", CURRENT_VERSION)
            put("events", JSONArray().apply { entries.forEach { put(toJson(it)) } })
        })
    }

    private fun load(): List<AlertHistoryEntry> {
        val root = encrypted.read(FILE_NAME) ?: return emptyList()
        require(root.optInt("version", -1) == CURRENT_VERSION) {
            "Unsupported alarm history"
        }
        val items = root.getJSONArray("events")
        val parsed = (0 until minOf(items.length(), MAX_PARSE_COUNT)).mapNotNull { index ->
            items.optJSONObject(index)?.let { runCatching { fromJson(it) }.getOrNull() }
        }
        return AlertHistoryRetention.retain(parsed, clock.now())
    }

    private fun toJson(entry: AlertHistoryEntry) = JSONObject().apply {
        put("id", entry.id)
        put("firedAt", entry.firedAt.toString())
        put("kind", entry.kind.name)
        put("person", JSONObject().apply {
            put("id", entry.person.id)
            put("name", entry.person.displayName)
        })
        put("reading", JSONObject().apply {
            put("connectionId", entry.reading.connectionId)
            put("measuredAt", entry.reading.measuredAt.toString())
            put("valueMgDl", entry.reading.valueMgDl)
            put("sourceValue", entry.reading.sourceValue)
            put("sourceUnit", entry.reading.sourceUnit.name)
            put("specialValue", entry.reading.specialValue?.name)
            put("trend", entry.reading.trend.name)
            put("sourceRecordId", entry.reading.sourceRecordId)
            put("receivedAt", entry.reading.receivedAt.toString())
        })
    }

    private fun fromJson(value: JSONObject): AlertHistoryEntry {
        val personValue = value.getJSONObject("person")
        val person = Connection(personValue.getString("id"), personValue.getString("name"))
        val readingValue = value.getJSONObject("reading")
        val reading = Reading(
            connectionId = readingValue.getString("connectionId"),
            measuredAt = Instant.parse(readingValue.getString("measuredAt")),
            valueMgDl = readingValue.nullableDouble("valueMgDl"),
            sourceValue = readingValue.nullableDouble("sourceValue"),
            sourceUnit = GlucoseUnit.valueOf(readingValue.getString("sourceUnit")),
            specialValue = readingValue.nullableString("specialValue")?.let(SpecialValue::valueOf),
            trend = Trend.valueOf(readingValue.getString("trend")),
            sourceRecordId = readingValue.nullableString("sourceRecordId"),
            receivedAt = Instant.parse(readingValue.getString("receivedAt")),
        )
        return AlertHistoryEntry(
            id = value.getString("id"),
            person = person,
            kind = AlertKind.valueOf(value.getString("kind")),
            reading = reading,
            firedAt = Instant.parse(value.getString("firedAt")),
        ).also { require(AlertHistoryRetention.valid(it)) }
    }

    private fun JSONObject.nullableDouble(name: String): Double? =
        takeIf { has(name) && !isNull(name) }?.getDouble(name)?.takeIf(Double::isFinite)

    private fun JSONObject.nullableString(name: String): String? =
        takeIf { has(name) && !isNull(name) }?.getString(name)?.takeIf(String::isNotBlank)

    private companion object {
        const val FILE_NAME = "alert_history"
        const val CURRENT_VERSION = 1
        const val MAX_PARSE_COUNT = 2_000
    }
}

/** Pure retention and identity policy, shared by loading and appending. */
internal object AlertHistoryRetention {
    const val MAX_EVENTS = 1_000
    val MAX_AGE: Duration = Duration.ofDays(30)

    fun retain(events: List<AlertHistoryEntry>, now: Instant): List<AlertHistoryEntry> {
        val oldest = now.minus(MAX_AGE)
        return events.asSequence()
            .filter(::valid)
            .filter { !it.firedAt.isBefore(oldest) && !it.firedAt.isAfter(now) }
            .sortedByDescending { it.firedAt }
            .distinctBy { it.id }
            .take(MAX_EVENTS)
            .toList()
    }

    fun valid(entry: AlertHistoryEntry): Boolean =
        entry.id.isNotBlank() && entry.person.id.isNotBlank() && entry.person.displayName.isNotBlank() &&
            entry.reading.connectionId == entry.person.id &&
            entry.reading.valueMgDl?.let { it.isFinite() && it > 0.0 } == true &&
            entry.reading.specialValue == null &&
            !entry.reading.measuredAt.isAfter(entry.firedAt) &&
            entry.reading.sourceValue?.isFinite() != false
}
