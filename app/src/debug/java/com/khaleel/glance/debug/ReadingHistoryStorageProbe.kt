package com.khaleel.glance.debug

import android.content.Context
import android.content.ContextWrapper
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.Trend
import com.khaleel.glance.data.LocalStateStore
import com.khaleel.glance.data.ReadingHistory
import com.khaleel.glance.data.StoredPatientState
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.json.JSONObject

/** Real encrypted Android persistence in an isolated synthetic namespace. */
internal fun runReadingHistoryStorageProbe(context: Context): JSONObject {
    val isolated = object : ContextWrapper(context) {
        override fun getNoBackupFilesDir() = File(super.getNoBackupFilesDir(), "synthetic-reading-history").apply { mkdirs() }
    }
    val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    val person = Connection("synthetic-history", "DEMO history")
    val readings = (1439 downTo 0).map { minute ->
        Reading(person.id, now.minusSeconds(minute * 60L), 112.0, 112.0,
            GlucoseUnit.MG_DL, null, Trend.STEADY, null, now)
    }
    val store = LocalStateStore(SecureFileStore(isolated))
    store.clear()
    try {
        store.save(StoredPatientState(person, readings.last(), readings))
        val restored = LocalStateStore(SecureFileStore(isolated)).load()
        check(restored.history == readings && restored.lastReading == readings.last())
        val sparse = readings.filterIndexed { index, _ -> index % 15 == 0 }
        val merged = ReadingHistory.merge(restored.history, sparse + readings.last(), person.id, now)
        check(merged == readings)
        val disk = File(isolated.noBackupFilesDir, "patient.bin").readBytes()
        check(!disk.toString(Charsets.ISO_8859_1).contains(person.id))
        store.save(restored.copy(history = merged))
        check(LocalStateStore(SecureFileStore(isolated)).load().history == readings)
        return JSONObject().apply {
            put("synthetic", true)
            put("minuteSamplesPersisted", readings.size)
            put("sameValuesAtDifferentTimesRetained", true)
            put("encryptedRoundTripThroughNewStore", true)
            put("sparseBackfillDoesNotEraseCapturedMinuteSamples", true)
            put("duplicateSourceTimesDoNotAddSamples", true)
            put("plaintextSyntheticIdentityAbsent", true)
        }
    } finally {
        store.clear()
    }
}
