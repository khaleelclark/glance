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
    private data class Loaded(
        val accountScopeKey: String?,
        val events: List<AlertHistoryEntry>,
        val failure: Exception? = null,
    )

    private val loaded = try {
        load()
    } catch (error: Exception) {
        Loaded(null, emptyList(), error)
    }
    private var storedAccountScopeKey: String? = loaded.accountScopeKey
    private var pendingEvents: List<AlertHistoryEntry> = loaded.events
    private var boundAccountScopeKey: String? = null
    private var loadFailure: Exception? = loaded.failure
    private val mutableEvents = MutableStateFlow<List<AlertHistoryEntry>>(emptyList())
    override val events: StateFlow<List<AlertHistoryEntry>> = mutableEvents.asStateFlow()

    /**
     * Makes persisted health data visible only after its account identity is confirmed.
     * Legacy unbound history is discarded on first binding; the same bound account is retained.
    */
    @Synchronized
    fun bindAccount(accountScopeKey: String) {
        AlertAccountScope.requireValid(accountScopeKey)
        loadFailure?.let { throw IllegalStateException("Could not read alarm history binding", it) }
        if (boundAccountScopeKey == accountScopeKey) return

        val retained = if (storedAccountScopeKey == accountScopeKey) {
            AlertHistoryRetention.retain(pendingEvents, clock.now())
        } else {
            emptyList()
        }
        write(accountScopeKey, retained)
        storedAccountScopeKey = accountScopeKey
        boundAccountScopeKey = accountScopeKey
        pendingEvents = retained
        mutableEvents.value = retained
    }

    @Synchronized
    override fun append(entry: AlertHistoryEntry) {
        val accountScopeKey = checkNotNull(boundAccountScopeKey) {
            "Alarm history must be bound to an account before append"
        }
        require(AlertHistoryRetention.valid(entry)) { "Alert history identity does not match" }
        val retained = AlertHistoryRetention.retain(listOf(entry) + mutableEvents.value, clock.now())
        write(accountScopeKey, retained)
        pendingEvents = retained
        mutableEvents.value = retained
    }

    @Synchronized
    override fun clear() {
        if (loadFailure != null) {
            encrypted.delete(FILE_NAME)
            loadFailure = null
            storedAccountScopeKey = null
            pendingEvents = emptyList()
            mutableEvents.value = emptyList()
            return
        }
        val accountScopeKey = boundAccountScopeKey ?: storedAccountScopeKey
        if (accountScopeKey == null) encrypted.delete(FILE_NAME) else write(accountScopeKey, emptyList())
        pendingEvents = emptyList()
        mutableEvents.value = emptyList()
    }

    private fun write(accountScopeKey: String, entries: List<AlertHistoryEntry>) {
        encrypted.write(FILE_NAME, JSONObject().apply {
            put("version", CURRENT_VERSION)
            put("accountScopeKey", accountScopeKey)
            put("events", JSONArray().apply { entries.forEach { put(toJson(it)) } })
        })
    }

    private fun load(): Loaded {
        val root = encrypted.read(FILE_NAME) ?: return Loaded(null, emptyList())
        val version = root.optInt("version", -1)
        require(version == LEGACY_VERSION || version == CURRENT_VERSION) {
            "Unsupported alarm history"
        }
        val accountScopeKey = if (version == CURRENT_VERSION) {
            AlertAccountScope.requireValid(root.getString("accountScopeKey"))
        } else {
            null
        }
        val items = root.optJSONArray("events") ?: return Loaded(accountScopeKey, emptyList())
        val parsed = (0 until minOf(items.length(), MAX_PARSE_COUNT)).mapNotNull { index ->
            items.optJSONObject(index)?.let { runCatching { fromJson(it) }.getOrNull() }
        }
        return Loaded(accountScopeKey, AlertHistoryRetention.retain(parsed, clock.now()))
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
        const val LEGACY_VERSION = 1
        const val CURRENT_VERSION = 2
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
