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
        if (mode == "notification-probe") {
            java.io.File(context.filesDir, "notification-probe.txt").writeText(
                com.khaleel.glance.notification.LiveUpdateSupport.diagnostics(context)
            )
            return
        }
        if (mode == "notification-retention") {
            com.khaleel.glance.notification.NotificationVisibility.setKeepVisible(
                context, intent.getBooleanExtra("keep", true)
            )
            return
        }
        if (mode == "notification-restore") {
            (context.applicationContext as com.khaleel.glance.GlanceApplication)
                .showCurrentNotification(restore = true)
            return
        }
        if (mode == "dismiss-active" || mode == "dismiss-saved") {
            val generation = if (mode == "dismiss-saved") {
                context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE)
                    .getLong("dismiss_generation", Long.MIN_VALUE)
            } else MonitoringService.currentActionGeneration ?: return
            context.sendBroadcast(Intent(context, com.khaleel.glance.notification.NotificationDismissReceiver::class.java)
                .setAction(com.khaleel.glance.notification.NotificationDismissReceiver.ACTION_DISMISSED)
                .putExtra(com.khaleel.glance.notification.NotificationDismissReceiver.EXTRA_GENERATION,
                    intent.getLongExtra("generation", generation)))
            return
        }
        if (mode == "live-api-check" && android.os.Build.VERSION.SDK_INT >= 36) {
            val manager = context.getSystemService(android.app.NotificationManager::class.java)
            GlucoseNotificationRenderer(context, emptyState(), GlucoseUnit.MG_DL, false).createChannel()
            val builder = android.app.Notification.Builder(context, GlucoseNotificationRenderer.CHANNEL_ID)
                .setSmallIcon(com.khaleel.glance.R.drawable.ic_glance_status)
                .setContentTitle("DEMO · Live Update API check")
                .setContentText("DEMO · 112 mg/dL")
                .setOngoing(true)
                .setStyle(android.app.Notification.BigTextStyle().bigText("DEMO · 112 mg/dL"))
            com.khaleel.glance.notification.LiveUpdateSupport.request(builder, "112")
            val notification = builder.build()
            val result = org.json.JSONObject()
                .put("sdkFull", android.os.Build.VERSION.SDK_INT_FULL)
                .put("promotionAllowed", manager.canPostPromotedNotifications())
                .put("requestExtra", notification.extras.getBoolean("android.requestPromotedOngoing"))
                .put("criticalText", notification.extras.getString("android.shortCriticalText"))
                .put("customViewAbsent", notification.contentView == null && notification.bigContentView == null)
            java.io.File(context.filesDir, "live-api-check.json").writeText(result.toString())
            manager.notify(GlucoseNotificationRenderer.NOTIFICATION_ID, notification)
            return
        }
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
        if (mode in listOf("service-pause", "service-stop-person", "service-stop-account")) {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE).edit()
                .putLong("dismiss_generation", MonitoringService.currentActionGeneration ?: Long.MIN_VALUE).commit()
            app.controller.setMonitoringEnabled(false)
            context.stopService(Intent(context, MonitoringService::class.java))
            app.hideCurrentNotification()
            if (mode == "service-stop-person") app.controller.clearSelectedPerson()
            if (mode == "service-stop-account") app.controller.signOut()
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
        val unit = if (intent.getStringExtra("unit") == "mmol") GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL
        val value = intent.getStringExtra("value")?.toDoubleOrNull()?.coerceIn(20.0, 600.0) ?: 112.0
        val reading = Reading(
            connectionId = "synthetic-person",
            measuredAt = now.minusSeconds(ageMinutes * 60),
            valueMgDl = value,
            sourceValue = value,
            sourceUnit = GlucoseUnit.MG_DL,
            specialValue = null,
            trend = runCatching { Trend.valueOf(intent.getStringExtra("trend") ?: "STEADY") }.getOrDefault(Trend.STEADY),
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
            history = if (mode == "reference") (0 until 36).map { index ->
                val demoValue = 120.0 + 30.0 * kotlin.math.sin(index / 5.0) + index
                reading.copy(
                    measuredAt = reading.measuredAt.minusSeconds((36L - index) * 300),
                    valueMgDl = demoValue, sourceValue = demoValue,
                    sourceRecordId = "synthetic-history-$index",
                )
            } else emptyList(),
        )
        GlucoseNotificationRenderer(
            context,
            state,
            unit,
            lockScreenVisible = intent.getBooleanExtra("lock_visible", false),
            enhanced = intent.getBooleanExtra("enhanced", false),
            numericStatusIcon = intent.getBooleanExtra("numeric_icon", true),
        ).post()
    }

    private fun emptyState() = DisplayState(
        false, RuntimeState.STOPPED, ConnectionState.READY, null, null,
        ReadingFreshness.ABSENT, null, null,
    )
}
