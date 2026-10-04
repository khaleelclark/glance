package com.khaleel.glance.debug

import android.content.Context
import android.content.ContextWrapper
import com.khaleel.glance.alerts.AlertHistoryEntry
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.EncryptedAlertHistoryStore
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Exercises real Keystore-backed storage in a separate, synthetic-only directory. */
internal fun runAlarmHistoryProbe(context: Context): JSONObject {
    check(android.os.Build.FINGERPRINT.contains("generic") ||
        android.os.Build.MODEL.contains("sdk")) { "Synthetic emulator required" }
    val directory = File(context.noBackupFilesDir, "synthetic_alarm_history").apply { mkdirs() }
    val isolated = object : ContextWrapper(context) {
        override fun getNoBackupFilesDir(): File = directory
    }
    val now = Instant.now()
    val clock = object : Clock {
        override fun now(): Instant = now
        override fun elapsedRealtimeMillis(): Long = 0L
    }
    val encrypted = SecureFileStore(isolated)
    val store = EncryptedAlertHistoryStore(encrypted, clock)
    store.clear()
    val person = Connection("synthetic-alarm-person", "DEMO · Alex")
    AlertKind.entries.forEachIndexed { index, kind ->
        val value = when (kind) {
            AlertKind.LOW -> 65.0
            AlertKind.URGENT_LOW -> 50.0
            AlertKind.HIGH -> 250.0
            AlertKind.URGENT_HIGH -> 310.0
        }
        val reading = Reading(person.id, now.minusSeconds(60L + index), value, value,
            GlucoseUnit.MG_DL, null, Trend.STEADY, "synthetic-record-$index", now)
        store.append(AlertHistoryEntry("synthetic-alert-$index", person, kind, reading, now.minusSeconds(index.toLong())))
    }
    check(store.events.value.map { it.kind }.toSet() == AlertKind.entries.toSet())
    val restored = EncryptedAlertHistoryStore(SecureFileStore(isolated), clock)
    check(restored.events.value == store.events.value)
    check(restored.events.value.all { it.reading.measuredAt != it.firedAt })
    val files = directory.listFiles().orEmpty().filter { it.extension == "bin" }
    check(files.isNotEmpty())
    check(files.none { it.readBytes().toString(Charsets.UTF_8).contains("DEMO · Alex") })
    restored.clear()
    check(restored.events.value.isEmpty())
    check(EncryptedAlertHistoryStore(SecureFileStore(isolated), clock).events.value.isEmpty())
    // Reject damaged authenticated ciphertext without exposing partially decoded records.
    val encryptedFile = directory.listFiles().orEmpty().single { it.extension == "bin" }
    val damaged = encryptedFile.readBytes()
    damaged[damaged.lastIndex] = (damaged.last().toInt() xor 1).toByte()
    encryptedFile.writeBytes(damaged)
    val unreadable = EncryptedAlertHistoryStore(SecureFileStore(isolated), clock)
    check(unreadable.events.value.isEmpty())
    val sample = AlertHistoryEntry("synthetic-recovery", person, AlertKind.HIGH,
        Reading(person.id, now.minusSeconds(60), 250.0, 250.0, GlucoseUnit.MG_DL,
            null, Trend.STEADY, "synthetic-recovery-record", now), now)
    check(runCatching { unreadable.append(sample) }.isFailure)
    unreadable.clear()
    unreadable.append(sample)
    check(EncryptedAlertHistoryStore(SecureFileStore(isolated), clock).events.value == listOf(sample))
    unreadable.clear()
    return JSONObject().apply {
        put("allFourExactTypesPersisted", true)
        put("recreatedStorePreservesRecords", true)
        put("sourceAndPostingTimesDistinct", true)
        put("onDiskPatientNameNotPlaintext", true)
        put("clearPersistsAcrossRecreation", true)
        put("isolatedSyntheticDirectory", true)
        put("damagedCiphertextFailsClosed", true)
        put("explicitClearRepairsUnreadableHistory", true)
    }
}
