package com.khaleel.glance.data

import android.content.Context
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SpecialValue
import com.khaleel.glance.core.Trend
import org.json.JSONObject
import java.time.Instant

data class StoredPatientState(val selected: Connection?, val lastReading: Reading?)

class LocalStateStore(private val encrypted: SecureFileStore) {
    fun load(): StoredPatientState {
        val value = encrypted.read("patient") ?: return StoredPatientState(null, null)
        val selected = value.optJSONObject("selected")?.let { Connection(it.getString("id"), it.getString("name")) }
        val reading = value.optJSONObject("reading")?.let {
            Reading(
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
        }?.takeIf { it.connectionId == selected?.id }
        return StoredPatientState(selected, reading)
    }

    fun save(state: StoredPatientState) {
        encrypted.write("patient", JSONObject().apply {
            state.selected?.let { put("selected", JSONObject().apply { put("id", it.id); put("name", it.displayName) }) }
            state.lastReading?.takeIf { it.connectionId == state.selected?.id }?.let { reading ->
                put("reading", JSONObject().apply {
                    put("connectionId", reading.connectionId)
                    put("measuredAt", reading.measuredAt.toString())
                    put("valueMgDl", reading.valueMgDl)
                    put("sourceValue", reading.sourceValue)
                    put("sourceUnit", reading.sourceUnit.name)
                    put("specialValue", reading.specialValue?.name)
                    put("trend", reading.trend.name)
                    put("sourceRecordId", reading.sourceRecordId)
                    put("receivedAt", reading.receivedAt.toString())
                })
            }
        })
    }

    fun clear() = encrypted.delete("patient")
}

class UserSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var unit: GlucoseUnit
        get() = runCatching { GlucoseUnit.valueOf(prefs.getString("unit", GlucoseUnit.MG_DL.name)!!) }.getOrDefault(GlucoseUnit.MG_DL)
        set(value) = prefs.edit().putString("unit", value.name).apply()
    var showOnLockScreen: Boolean
        get() = prefs.getBoolean("lock_visible", false)
        set(value) = prefs.edit().putBoolean("lock_visible", value).apply()
    var enhancedNotification: Boolean
        get() = prefs.getBoolean("enhanced", true)
        set(value) = prefs.edit().putBoolean("enhanced", value).apply()
    var intervalMinutes: Int
        get() = prefs.getInt("interval_minutes", 1).takeIf { it == 1 || it == 5 } ?: 1
        set(value) { require(value == 1 || value == 5); prefs.edit().putInt("interval_minutes", value).apply() }
    var resumeAfterBoot: Boolean
        get() = prefs.getBoolean("resume_after_boot", true)
        set(value) = prefs.edit().putBoolean("resume_after_boot", value).apply()
    var monitoringEnabled: Boolean
        get() = prefs.getBoolean("monitoring_enabled", false)
        set(value) = prefs.edit().putBoolean("monitoring_enabled", value).commit().let { require(it) }
    var nextAllowedFetch: Instant?
        get() = prefs.getLong("next_allowed_fetch", 0L).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(value) = prefs.edit().putLong("next_allowed_fetch", value?.toEpochMilli() ?: 0L).commit().let { require(it) }

    fun clear() { prefs.edit().clear().commit() }
}
