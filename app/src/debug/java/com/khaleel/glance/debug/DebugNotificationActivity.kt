package com.khaleel.glance.debug

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.Trend
import com.khaleel.glance.notification.GlucoseNotificationRenderer
import java.time.Instant

/** Debug-build-only synthetic visual fixture. Release builds cannot receive this action. */
class DebugNotificationActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showFixture(this, intent)
        finish()
    }

    private fun showFixture(context: Context, intent: Intent) {
        val mode = intent.getStringExtra("mode") ?: "fresh"
        if (mode == "hide") {
            GlucoseNotificationRenderer(context, emptyState(), GlucoseUnit.MG_DL, false).hide()
            return
        }
        val now = Instant.now()
        val ageMinutes = when (mode) {
            "delayed" -> 7L
            "stale" -> 18L
            else -> 1L
        }
        val reading = Reading(
            connectionId = "synthetic-person",
            measuredAt = now.minusSeconds(ageMinutes * 60),
            valueMgDl = 112.0,
            sourceValue = 112.0,
            sourceUnit = GlucoseUnit.MG_DL,
            specialValue = null,
            trend = Trend.STEADY,
            sourceRecordId = "synthetic-reading",
            receivedAt = now,
        )
        val state = DisplayState(
            monitoringEnabled = true,
            runtime = RuntimeState.RUNNING,
            connection = if (mode == "offline") ConnectionState.OFFLINE else ConnectionState.READY,
            person = Connection("synthetic-person", "DEMO · Patrick Example"),
            reading = reading,
            freshness = when (mode) {
                "delayed" -> ReadingFreshness.DELAYED
                "stale" -> ReadingFreshness.STALE
                else -> ReadingFreshness.FRESH
            },
            lastSuccessfulFetch = now,
            nextAllowedFetch = null,
        )
        GlucoseNotificationRenderer(
            context,
            state,
            GlucoseUnit.MG_DL,
            lockScreenVisible = intent.getBooleanExtra("lock_visible", false),
            enhanced = false,
        ).post()
    }

    private fun emptyState() = DisplayState(
        false, RuntimeState.STOPPED, ConnectionState.READY, null, null,
        ReadingFreshness.ABSENT, null, null,
    )
}
