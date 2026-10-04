package com.khaleel.glance.alerts

import android.content.Context
import java.time.Instant

/** Alert choices and deduplication state survive process recreation and reboot. */
class AlertPreferences(context: Context) : AlertStore {
    private val prefs = context.getSharedPreferences("glucose_alerts", Context.MODE_PRIVATE)

    override fun config(): AlertConfig {
        val defaults = AlertConfig.defaults()
        val loaded = AlertConfig(AlertKind.entries.associateWith { kind ->
            val fallback = defaults[kind]
            val prefix = kind.name.lowercase()
            AlertRule(
                enabled = prefs.getBoolean("${prefix}_enabled", fallback.enabled),
                thresholdMgDl = prefs.getInt("${prefix}_threshold", fallback.thresholdMgDl)
                    .takeIf { it in 40..400 } ?: fallback.thresholdMgDl,
                cooldownMinutes = prefs.getInt("${prefix}_cooldown", fallback.cooldownMinutes)
                    .takeIf { it in 1..60 } ?: fallback.cooldownMinutes,
            )
        })
        return if (loaded.isOrdered()) loaded else defaults
    }

    fun setRule(kind: AlertKind, rule: AlertRule) {
        require(AlertConfig(config().rules + (kind to rule)).isOrdered()) {
            "Urgent and ordinary alert thresholds must stay ordered"
        }
        val prefix = kind.name.lowercase()
        check(prefs.edit()
            .putBoolean("${prefix}_enabled", rule.enabled)
            .putInt("${prefix}_threshold", rule.thresholdMgDl)
            .putInt("${prefix}_cooldown", rule.cooldownMinutes)
            .commit()) { "Could not save alert settings" }
    }

    override fun snapshot(): AlertSnapshot = AlertSnapshot(
        personFingerprint = prefs.getString("person_fingerprint", null),
        lastReadingFingerprint = prefs.getString("last_reading_fingerprint", null),
        lastFiredAt = AlertKind.entries.mapNotNull { kind ->
            prefs.getLong("last_fired_${kind.name.lowercase()}", 0L)
                .takeIf { it > 0L }?.let { kind to Instant.ofEpochMilli(it) }
        }.toMap(),
    )

    override fun saveSnapshot(snapshot: AlertSnapshot) {
        val editor = prefs.edit()
            .putString("person_fingerprint", snapshot.personFingerprint)
            .putString("last_reading_fingerprint", snapshot.lastReadingFingerprint)
        AlertKind.entries.forEach { kind ->
            editor.putLong("last_fired_${kind.name.lowercase()}", snapshot.lastFiredAt[kind]?.toEpochMilli() ?: 0L)
        }
        check(editor.commit()) { "Could not save alert state" }
    }

    fun clearSnapshot() = saveSnapshot(AlertSnapshot())

    fun clear() {
        check(prefs.edit().clear().commit()) { "Could not clear alert settings" }
    }
}
