package com.khaleel.glance.debug

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.khaleel.glance.core.Connection
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.GlucoseAlertNotifier
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.Trend
import com.khaleel.glance.notification.GlucoseNotificationRenderer
import com.khaleel.glance.monitoring.MonitoringService
import com.khaleel.glance.SourceFactory
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.SourceResult
import kotlinx.coroutines.runBlocking
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
        if (mode == "service-demo") {
            context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", true).putInt("poll_count", 0).commit()
            val controller = (context.applicationContext as com.khaleel.glance.GlanceApplication).controller
            val signedIn = runBlocking {
                controller.signIn(Credentials(SourceFactory.DEMO_EMAIL, SourceFactory.DEMO_PASSWORD))
            }
            if (signedIn is SourceResult.Success) {
                controller.selectPerson(signedIn.value.single())
                MonitoringService.start(context)
            }
            return
        }
        if (mode == "demo-clear") {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            app.controller.setMonitoringEnabled(false)
            context.stopService(Intent(context, MonitoringService::class.java))
            app.hideCurrentNotification()
            app.controller.signOut()
            context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE).edit().clear().commit()
            return
        }
        if (mode == "alert-demo") {
            val kind = runCatching {
                AlertKind.valueOf(intent.getStringExtra("kind") ?: "URGENT_HIGH")
            }.getOrNull() ?: return
            val value = intent.getIntExtra("value", 305).coerceIn(40, 400).toDouble()
            val now = Instant.now()
            val reading = Reading(
                connectionId = "synthetic-alert-person",
                measuredAt = now,
                valueMgDl = value,
                sourceValue = value,
                sourceUnit = GlucoseUnit.MG_DL,
                specialValue = null,
                trend = Trend.STEADY,
                sourceRecordId = "synthetic-alert",
                receivedAt = now,
            )
            val state = DisplayState(
                monitoringEnabled = true,
                runtime = RuntimeState.RUNNING,
                connection = ConnectionState.READY,
                person = Connection("synthetic-alert-person", "DEMO · Patrick Example"),
                reading = reading,
                freshness = ReadingFreshness.FRESH,
                lastSuccessfulFetch = now,
                nextAllowedFetch = null,
            )
            GlucoseAlertNotifier(context).apply {
                ensureChannels()
                post(kind, state, GlucoseUnit.MG_DL, intent.getBooleanExtra("lock_visible", false))
            }
            return
        }
        if (mode == "alert-clear") {
            GlucoseAlertNotifier(context).clear()
            return
        }
        if (mode == "service-start") {
            GlucoseNotificationRenderer(context, emptyState(), GlucoseUnit.MG_DL, false).hide()
            MonitoringService.start(context)
            return
        }
        if (mode == "service-pause") {
            (context.applicationContext as com.khaleel.glance.GlanceApplication).controller
                .setMonitoringEnabled(false)
            context.stopService(Intent(context, MonitoringService::class.java))
            GlucoseNotificationRenderer(context, emptyState(), GlucoseUnit.MG_DL, false).hide()
            return
        }
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
