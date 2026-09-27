package com.khaleel.glance.data

import android.content.Context
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SpecialValue
import com.khaleel.glance.core.Trend
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.Duration

data class StoredPatientState(
    val selected: Connection?,
    val lastReading: Reading?,
    val history: List<Reading> = emptyList(),
)

interface PatientPersistence {
    fun load(): StoredPatientState
    fun save(state: StoredPatientState)
    fun clear()
}

class LocalStateStore(private val encrypted: SecureFileStore) : PatientPersistence {
    override fun load(): StoredPatientState {
        val value = encrypted.read("patient") ?: return StoredPatientState(null, null)
        val selected = value.optJSONObject("selected")?.let { Connection(it.getString("id"), it.getString("name")) }
        val reading = value.optJSONObject("reading")?.let(::readingFromJson)?.takeIf {
            it.connectionId == selected?.id &&
                Duration.between(it.measuredAt, Instant.now()) < Duration.ofHours(24)
        }
        val storedHistory = value.optJSONArray("history")?.let { items ->
            (0 until minOf(items.length(), 1500)).mapNotNull { index ->
                items.optJSONObject(index)?.let { runCatching { readingFromJson(it) }.getOrNull() }
            }
        }.orEmpty()
        val history = selected?.let {
            ReadingHistory.merge(emptyList(), storedHistory + listOfNotNull(reading), it.id, Instant.now())
        }.orEmpty()
        if (reading == null && value.has("reading")) {
            save(StoredPatientState(selected, null, history))
        }
        return StoredPatientState(selected, reading, history)
    }

    override fun save(state: StoredPatientState) {
        encrypted.write("patient", JSONObject().apply {
            state.selected?.let { put("selected", JSONObject().apply { put("id", it.id); put("name", it.displayName) }) }
            state.lastReading?.takeIf { it.connectionId == state.selected?.id }?.let { put("reading", readingToJson(it)) }
            state.selected?.let { selected ->
                put("history", JSONArray().apply {
                    ReadingHistory.merge(emptyList(), state.history, selected.id, Instant.now())
                        .forEach { put(readingToJson(it)) }
                })
            }
        })
    }

    private fun readingFromJson(it: JSONObject) = Reading(
        connectionId = it.getString("connectionId"),
        measuredAt = Instant.parse(it.getString("measuredAt")),
        valueMgDl = it.optDouble("valueMgDl").takeUnless(Double::isNaN),
        sourceValue = it.optDouble("sourceValue").takeUnless(Double::isNaN),
        sourceUnit = GlucoseUnit.valueOf(it.getString("sourceUnit")),
        specialValue = it.optString("specialValue").takeIf(String::isNotBlank)?.let(SpecialValue::valueOf),
        trend = Trend.valueOf(it.getString("trend")),
        sourceRecordId = it.optString("sourceRecordId").takeIf(String::isNotBlank),
        receivedAt = Instant.parse(it.getString("receivedAt")),
    )

    private fun readingToJson(reading: Reading) = JSONObject().apply {
        put("connectionId", reading.connectionId)
        put("measuredAt", reading.measuredAt.toString())
        put("valueMgDl", reading.valueMgDl)
        put("sourceValue", reading.sourceValue)
        put("sourceUnit", reading.sourceUnit.name)
        put("specialValue", reading.specialValue?.name)
        put("trend", reading.trend.name)
        put("sourceRecordId", reading.sourceRecordId)
        put("receivedAt", reading.receivedAt.toString())
    }

    override fun clear() { encrypted.delete("patient") }
}

interface SettingsPersistence {
    var unit: GlucoseUnit
    var showOnLockScreen: Boolean
    var enhancedNotification: Boolean
    var intervalMinutes: Int
    var resumeAfterBoot: Boolean
    var monitoringEnabled: Boolean
    var nextAllowedFetch: Instant?
    fun clear()
}

class UserSettings(context: Context) : SettingsPersistence {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    override var unit: GlucoseUnit
        get() = runCatching { GlucoseUnit.valueOf(prefs.getString("unit", GlucoseUnit.MG_DL.name)!!) }.getOrDefault(GlucoseUnit.MG_DL)
        set(value) = prefs.edit().putString("unit", value.name).apply()
    override var showOnLockScreen: Boolean
        get() = prefs.getBoolean("lock_visible", false)
        set(value) = prefs.edit().putBoolean("lock_visible", value).apply()
    override var enhancedNotification: Boolean
        // A stored choice still wins; new installs and users who never changed this switch
        // request the status chip when Android supports promotion.
        get() = prefs.getBoolean("enhanced_explicit", true)
        set(value) = prefs.edit().putBoolean("enhanced_explicit", value).apply()
    override var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 1).takeIf { it == 1 || it == 5 } ?: 1
        set(value) { require(value == 1 || value == 5); prefs.edit().putInt("interval_minutes", value).apply() }
    override var resumeAfterBoot: Boolean
        get() = prefs.getBoolean("resume_after_boot", true)
        set(value) = prefs.edit().putBoolean("resume_after_boot", value).apply()
    override var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoring_enabled", false)
        set(value) = prefs.edit().putBoolean("monitoring_enabled", value).commit().let { require(it) }
    override var nextAllowedFetch: Instant?
        get() = prefs.getLong("next_allowed_fetch", 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit().putLong("next_allowed_fetch", value?.toEpochMilli() ?: 0L).commit().let { require(it) }

    override fun clear() { prefs.edit().clear().commit() }
}
