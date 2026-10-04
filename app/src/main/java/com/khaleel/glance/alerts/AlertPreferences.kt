package com.khaleel.glance.alerts

import android.content.Context
import java.security.MessageDigest
import java.time.Instant

/** Alert choices and deduplication state survive process recreation and reboot. */
class AlertPreferences(context: Context) : AlertStore {
    private val prefs = context.getSharedPreferences("glucose_alerts", Context.MODE_PRIVATE)

    override fun config(): AlertConfig = readConfig("")

    /** Defaults remain disabled until this person explicitly enables a rule. */
    fun configForPerson(personId: String): AlertConfig = readConfig(scopePrefix(personId))

    private fun readConfig(scope: String): AlertConfig {
        val defaults = AlertConfig.defaults()
        val loaded = AlertConfig(AlertKind.entries.associateWith { kind ->
            val fallback = defaults[kind]
            val prefix = "$scope${kind.name.lowercase()}"
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
        setRule("", config(), kind, rule)
    }

    fun setRuleForPerson(personId: String, kind: AlertKind, rule: AlertRule) {
        val scope = scopePrefix(personId)
        setRule(scope, configForPerson(personId), kind, rule)
    }

    private fun setRule(scope: String, current: AlertConfig, kind: AlertKind, rule: AlertRule) {
        require(AlertConfig(current.rules + (kind to rule)).isOrdered()) {
            "Urgent and ordinary alert thresholds must stay ordered"
        }
        val prefix = "$scope${kind.name.lowercase()}"
        check(prefs.edit()
            .putBoolean("${prefix}_enabled", rule.enabled)
            .putInt("${prefix}_threshold", rule.thresholdMgDl)
            .putInt("${prefix}_cooldown", rule.cooldownMinutes)
            .commit()) { "Could not save alert settings" }
    }

    override fun snapshot(): AlertSnapshot = readSnapshot("")

    private fun readSnapshot(scope: String): AlertSnapshot = AlertSnapshot(
        personFingerprint = prefs.getString("${scope}person_fingerprint", null),
        lastReadingFingerprint = prefs.getString("${scope}last_reading_fingerprint", null),
        lastFiredAt = AlertKind.entries.mapNotNull { kind ->
            prefs.getLong("${scope}last_fired_${kind.name.lowercase()}", 0L)
                .takeIf { it > 0L }?.let { kind to Instant.ofEpochMilli(it) }
        }.toMap(),
    )

    override fun saveSnapshot(snapshot: AlertSnapshot) {
        saveSnapshot("", snapshot)
    }

    private fun saveSnapshot(scope: String, snapshot: AlertSnapshot) {
        val editor = prefs.edit()
            .putString("${scope}person_fingerprint", snapshot.personFingerprint)
            .putString("${scope}last_reading_fingerprint", snapshot.lastReadingFingerprint)
        AlertKind.entries.forEach { kind ->
            editor.putLong(
                "${scope}last_fired_${kind.name.lowercase()}",
                snapshot.lastFiredAt[kind]?.toEpochMilli() ?: 0L,
            )
        }
        check(editor.commit()) { "Could not save alert state" }
    }

    fun forPerson(personId: String): AlertStore {
        val scope = scopePrefix(personId)
        return object : AlertStore {
            override fun config() = readConfig(scope)
            override fun snapshot() = readSnapshot(scope)
            override fun saveSnapshot(snapshot: AlertSnapshot) = saveSnapshot(scope, snapshot)
        }
    }

    /**
     * Claims the one legacy settings/state bundle for the existing primary person.
     * Subsequent people always begin with the disabled defaults.
     */
    @Synchronized
    fun migrateLegacyTo(personId: String) {
        if (prefs.getBoolean(LEGACY_MIGRATED, false)) return
        val scope = scopePrefix(personId)
        val config = config()
        val snapshot = snapshot()
        val editor = prefs.edit()
        config.rules.forEach { (kind, rule) ->
            val prefix = "$scope${kind.name.lowercase()}"
            editor.putBoolean("${prefix}_enabled", rule.enabled)
                .putInt("${prefix}_threshold", rule.thresholdMgDl)
                .putInt("${prefix}_cooldown", rule.cooldownMinutes)
        }
        editor.putString("${scope}person_fingerprint", snapshot.personFingerprint)
            .putString("${scope}last_reading_fingerprint", snapshot.lastReadingFingerprint)
        AlertKind.entries.forEach { kind ->
            editor.putLong(
                "${scope}last_fired_${kind.name.lowercase()}",
                snapshot.lastFiredAt[kind]?.toEpochMilli() ?: 0L,
            )
        }
        check(editor.putBoolean(LEGACY_MIGRATED, true).commit()) {
            "Could not migrate alert settings"
        }
    }

    /** Clears deduplication/cooldown state for the legacy and every person scope. */
    fun clearSnapshot() {
        val editor = prefs.edit()
        prefs.all.keys.filter(::isSnapshotKey).forEach(editor::remove)
        check(editor.commit()) { "Could not clear alert state" }
    }

    fun clear() {
        check(prefs.edit().clear().commit()) { "Could not clear alert settings" }
    }

    private fun scopePrefix(personId: String): String {
        require(personId.isNotBlank())
        return "person_${fingerprint(personId)}_"
    }

    private fun isSnapshotKey(key: String): Boolean =
        key == "person_fingerprint" || key == "last_reading_fingerprint" ||
            key.startsWith("last_fired_") ||
            (key.startsWith("person_") && (
                key.endsWith("_person_fingerprint") ||
                    key.endsWith("_last_reading_fingerprint") ||
                    AlertKind.entries.any { key.endsWith("_last_fired_${it.name.lowercase()}") }
                ))

    companion object {
        private const val LEGACY_MIGRATED = "legacy_migrated_to_person_v1"

        internal fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
