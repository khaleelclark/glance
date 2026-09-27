package com.khaleel.glance.alerts

import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import java.security.MessageDigest
import java.time.Instant

enum class AlertKind(val label: String, val urgent: Boolean) {
    LOW("Low", false),
    URGENT_LOW("Urgent Low", true),
    HIGH("High", false),
    URGENT_HIGH("Urgent High", true),
}

data class AlertRule(
    val enabled: Boolean,
    val thresholdMgDl: Int,
    val cooldownMinutes: Int = 5,
) {
    init {
        require(thresholdMgDl in 40..400)
        require(cooldownMinutes in 1..60)
    }
}

data class AlertConfig(val rules: Map<AlertKind, AlertRule>) {
    operator fun get(kind: AlertKind): AlertRule = rules.getValue(kind)

    fun isOrdered(): Boolean =
        get(AlertKind.URGENT_LOW).thresholdMgDl <= get(AlertKind.LOW).thresholdMgDl &&
            get(AlertKind.LOW).thresholdMgDl < get(AlertKind.HIGH).thresholdMgDl &&
            get(AlertKind.HIGH).thresholdMgDl <= get(AlertKind.URGENT_HIGH).thresholdMgDl

    companion object {
        /** Starting values are editable examples; every alert is off until explicitly enabled. */
        fun defaults() = AlertConfig(
            mapOf(
                AlertKind.LOW to AlertRule(false, 70),
                AlertKind.URGENT_LOW to AlertRule(false, 55),
                AlertKind.HIGH to AlertRule(false, 240),
                AlertKind.URGENT_HIGH to AlertRule(false, 300),
            ),
        )
    }
}

data class AlertSnapshot(
    val personFingerprint: String? = null,
    val lastReadingFingerprint: String? = null,
    val lastFiredAt: Map<AlertKind, Instant> = emptyMap(),
)

data class AlertEvaluation(
    val kind: AlertKind?,
    val snapshot: AlertSnapshot,
    val clearExisting: Boolean = false,
)

/** Makes one decision per distinct, fresh source sample; timestamps never come from fetch time. */
object AlertPolicy {
    fun evaluate(
        config: AlertConfig,
        prior: AlertSnapshot,
        display: DisplayState,
        now: Instant,
    ): AlertEvaluation {
        val person = display.person ?: return AlertEvaluation(null, prior)
        val reading = display.reading ?: return AlertEvaluation(null, prior)
        if (!display.monitoringEnabled || display.runtime != RuntimeState.RUNNING ||
            display.freshness != ReadingFreshness.FRESH ||
            reading.connectionId != person.id || reading.measuredAt.isAfter(now)
        ) return AlertEvaluation(null, prior)

        val personKey = fingerprint(person.id)
        val scoped = if (prior.personFingerprint == personKey) prior else AlertSnapshot(personFingerprint = personKey)
        val readingKey = fingerprint(
            person.id, reading.measuredAt.toString(), reading.valueMgDl.toString(),
            reading.specialValue.toString(), reading.sourceRecordId.orEmpty(),
        )
        if (scoped.lastReadingFingerprint == readingKey) return AlertEvaluation(null, scoped)
        val seen = scoped.copy(lastReadingFingerprint = readingKey)
        val value = reading.valueMgDl?.takeIf { it.isFinite() && it > 0.0 }
            ?.takeIf { reading.specialValue == null } ?: return AlertEvaluation(null, seen)

        val matched = when {
            config[AlertKind.URGENT_LOW].enabled && value <= config[AlertKind.URGENT_LOW].thresholdMgDl -> AlertKind.URGENT_LOW
            config[AlertKind.LOW].enabled && value <= config[AlertKind.LOW].thresholdMgDl -> AlertKind.LOW
            config[AlertKind.URGENT_HIGH].enabled && value >= config[AlertKind.URGENT_HIGH].thresholdMgDl -> AlertKind.URGENT_HIGH
            config[AlertKind.HIGH].enabled && value >= config[AlertKind.HIGH].thresholdMgDl -> AlertKind.HIGH
            else -> null
        } ?: return AlertEvaluation(null, seen, clearExisting = true)

        val lastFired = scoped.lastFiredAt[matched]
        val cooldown = config[matched].cooldownMinutes.toLong() * 60L
        if (lastFired != null && now.isBefore(lastFired.plusSeconds(cooldown))) {
            return AlertEvaluation(null, seen)
        }
        return AlertEvaluation(matched, seen.copy(lastFiredAt = seen.lastFiredAt + (matched to now)))
    }

    private fun fingerprint(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach {
            digest.update(it.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
