package com.khaleel.glance.data

import android.content.Context
import com.khaleel.glance.core.SourceErrorKind
import com.khaleel.glance.core.SourceError
import com.khaleel.glance.core.SourceResponseFormat
import com.khaleel.glance.core.SourceResponseInfo
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class DiagnosticsSnapshot(
    val lastAttempt: Instant?,
    val lastSuccess: Instant?,
    val lastError: SourceErrorKind?,
    val largestObservedGapMillis: Long?,
    val eventCount: Int,
    val lastAlertIssue: Instant? = null,
    val lastResponseInfo: SourceResponseInfo? = null,
)

/** Only timing, numeric HTTP status and fixed categories are stored; no bodies or identifying data. */
interface DiagnosticsRecorder {
    fun pollAttempt(at: Instant)
    fun pollSuccess(at: Instant)
    fun pollFailure(at: Instant, kind: SourceErrorKind)
    fun sourceFailure(at: Instant, error: SourceError) = pollFailure(at, error.kind)
    fun runtime(at: Instant, state: String)
    fun alertIssue(at: Instant) = Unit
    fun snapshot(): DiagnosticsSnapshot
    fun clear()
}

object NoDiagnostics : DiagnosticsRecorder {
    override fun pollAttempt(at: Instant) = Unit
    override fun pollSuccess(at: Instant) = Unit
    override fun pollFailure(at: Instant, kind: SourceErrorKind) = Unit
    override fun runtime(at: Instant, state: String) = Unit
    override fun snapshot() = DiagnosticsSnapshot(null, null, null, null, 0)
    override fun clear() = Unit
}

class DiagnosticsStore(context: Context) : DiagnosticsRecorder {
    private val prefs = context.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)

    @Synchronized
    override fun pollAttempt(at: Instant) {
        val previous = prefs.getLong("last_attempt", 0L)
        val gap = (at.toEpochMilli() - previous).takeIf { previous > 0L && it >= 0L }
        val priorMax = prefs.getLong("max_gap", 0L)
        prefs.edit()
            .putLong("last_attempt", at.toEpochMilli())
            .putLong("max_gap", maxOf(priorMax, gap ?: 0L))
            .apply()
        append(at, "POLL_START")
    }

    @Synchronized
    override fun pollSuccess(at: Instant) {
        prefs.edit().putLong("last_success", at.toEpochMilli()).remove("last_error")
            .remove("last_http_status").remove("last_response_format").apply()
        append(at, "POLL_SUCCESS")
    }

    @Synchronized
    override fun pollFailure(at: Instant, kind: SourceErrorKind) {
        sourceFailure(at, SourceError(kind))
    }

    @Synchronized
    override fun sourceFailure(at: Instant, error: SourceError) {
        val edit = prefs.edit().putString("last_error", error.kind.name)
        if (error.responseInfo != null) {
            edit.putInt("last_http_status", error.responseInfo.httpStatus)
                .putString("last_response_format", error.responseInfo.format.name)
        } else {
            edit.remove("last_http_status").remove("last_response_format")
        }
        edit.apply()
        append(at, "POLL_" + error.kind.name)
    }

    @Synchronized
    override fun runtime(at: Instant, state: String) {
        require(state in setOf("STOPPED", "STARTING", "RUNNING", "UNABLE_TO_START"))
        append(at, "RUNTIME_" + state)
    }

    @Synchronized
    override fun alertIssue(at: Instant) {
        prefs.edit().putLong("last_alert_issue", at.toEpochMilli()).apply()
        append(at, "ALERT_DELIVERY_ISSUE")
    }

    @Synchronized
    override fun snapshot(): DiagnosticsSnapshot = DiagnosticsSnapshot(
        lastAttempt = prefs.getLong("last_attempt", 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli),
        lastSuccess = prefs.getLong("last_success", 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli),
        lastError = prefs.getString("last_error", null)?.let { runCatching { SourceErrorKind.valueOf(it) }.getOrNull() },
        largestObservedGapMillis = prefs.getLong("max_gap", 0L).takeIf { it > 0L },
        eventCount = events().length(),
        lastAlertIssue = prefs.getLong("last_alert_issue", 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli),
        lastResponseInfo = prefs.getInt("last_http_status", 0).takeIf { it > 0 }?.let { status ->
            prefs.getString("last_response_format", null)?.let { value ->
                runCatching { SourceResponseInfo(status, SourceResponseFormat.valueOf(value)) }.getOrNull()
            }
        },
    )

    @Synchronized
    override fun clear() {
        prefs.edit().clear().commit()
    }

    private fun append(at: Instant, event: String) {
        val cutoff = at.minusSeconds(48L * 3600L).toEpochMilli()
        val previous = events()
        val next = JSONArray()
        val first = (previous.length() - 5_999).coerceAtLeast(0)
        for (index in first until previous.length()) {
            val item = previous.optJSONObject(index) ?: continue
            if (item.optLong("at") >= cutoff) next.put(item)
        }
        next.put(JSONObject().put("at", at.toEpochMilli()).put("event", event))
        prefs.edit().putString("events", next.toString()).apply()
    }

    private fun events() = runCatching { JSONArray(prefs.getString("events", "[]")) }.getOrDefault(JSONArray())
}
