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
        if (mode.startsWith("retry-")) {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            val prefs = context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE)
            check(prefs.getBoolean("enabled", false) && app.controller.display.value.person?.id == SourceFactory.DEMO_PERSON_ID)
            when (mode) {
                "retry-source" -> {
                    val failure = intent.getStringExtra("failure") ?: "NONE"
                    check(failure in listOf("NONE", "NETWORK", "SERVER", "RATE_LIMITED"))
                    check(prefs.edit().putString("source_failure", failure).commit())
                }
                "retry-service-refresh" -> MonitoringService.currentActionGeneration?.let {
                    MonitoringService.refreshPendingIntent(context, it).send()
                }
                "retry-legacy-seed" -> check(context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                    .putLong("next_allowed_fetch", Instant.now().plusSeconds(900).toEpochMilli())
                    .remove("retry_reason").commit())
                "retry-probe" -> {
                    val state = app.controller.display.value
                    val window = app.controller.settings.retryWindow
                    val result = org.json.JSONObject()
                        .put("synthetic", true)
                        .put("attempts", prefs.getInt("poll_count", 0))
                        .put("connection", state.connection.name)
                        .put("retryReason", window?.reason?.name ?: "NONE")
                        .put("deadlineInFuture", window?.until?.isAfter(Instant.now()) == true)
                        .put("hasReading", state.reading != null)
                    java.io.File(context.filesDir, "retry-probe.json").writeText(result.toString())
                }
                else -> error("Unknown synthetic retry fixture")
            }
            return
        }
        if (mode == "watch-share") {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            app.watchPublisher.setValuesVisible(intent.getBooleanExtra("visible", false))
            app.watchPublisher.setNotificationsEnabled(intent.getBooleanExtra("notifications", false))
            return
        }
        if (mode == "watch-probe") {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            Thread {
                runCatching {
                    val items = com.google.android.gms.tasks.Tasks.await(
                        com.google.android.gms.wearable.Wearable.getDataClient(context).dataItems,
                        10, java.util.concurrent.TimeUnit.SECONDS)
                    try {
                        val snapshot = items.firstOrNull { it.uri.path == com.khaleel.glance.wear.shared.WatchSnapshot.PATH }
                            ?.let { com.khaleel.glance.wear.shared.WatchDataMap.decode(
                                com.google.android.gms.wearable.DataMapItem.fromDataItem(it).dataMap) }
                        val result = org.json.JSONObject()
                            .put("sharing", app.watchPreferences.valuesVisible)
                            .put("notifications", app.watchPreferences.notificationsEnabled)
                            .put("dataItemPresent", snapshot != null)
                            .put("status", snapshot?.status?.name ?: "ABSENT")
                            .put("hasValue", !snapshot?.value.isNullOrEmpty())
                            .put("hasMeasurement", (snapshot?.measuredAtMillis ?: 0) > 0)
                            .put("payloadValid", snapshot?.valid() ?: false)
                        java.io.File(context.filesDir, "watch-probe.json").writeText(result.toString())
                    } finally { items.release() }
                }.onFailure {
                    java.io.File(context.filesDir, "watch-probe.json").writeText(
                        org.json.JSONObject().put("transportAvailable", false).toString())
                }
            }.start()
            return
        }
        if (mode == "privacy-legacy-seed") {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            app.controller.setMonitoringEnabled(false)
            context.stopService(Intent(context, MonitoringService::class.java))
            app.hideCurrentNotification()
            check(context.deleteSharedPreferences(com.khaleel.glance.notification.LockScreenPrivacy.PREFS))
            check(context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putBoolean("lock_visible", true).putString("unit", GlucoseUnit.MMOL_L.name)
                .putInt("interval_minutes", 5).commit())
            return
        }
        if (mode == "privacy-storage-probe") {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            val result = org.json.JSONObject()
                .put("lockScreenHidden", !app.controller.settings.showOnLockScreen)
                .put("legacyGrantPresent", context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    .getBoolean("lock_visible", false))
                .put("unitPreserved", app.controller.settings.unit == GlucoseUnit.MMOL_L)
                .put("intervalPreserved", app.controller.settings.intervalMinutes == 5)
            java.io.File(context.filesDir, "privacy-storage-probe.json").writeText(result.toString())
            return
        }
        if (mode == "notification-probe") {
            java.io.File(context.filesDir, "notification-probe.txt").writeText(
                com.khaleel.glance.notification.LiveUpdateSupport.diagnostics(context)
            )
            val posted = context.getSystemService(android.app.NotificationManager::class.java)
                .activeNotifications.firstOrNull { it.id == GlucoseNotificationRenderer.NOTIFICATION_ID }?.notification
            val public = posted?.publicVersion
            val privacy = org.json.JSONObject()
                .put("posted", posted != null)
                .put("privateVisibility", posted?.visibility == android.app.Notification.VISIBILITY_PRIVATE)
                .put("publicRedacted", public?.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString() == "Glance · Monitoring" &&
                    public.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() == "Reading hidden on lock screen")
                .put("numericIconAbsent", posted?.smallIcon?.type == android.graphics.drawable.Icon.TYPE_RESOURCE &&
                    posted.smallIcon.resId == com.khaleel.glance.R.drawable.ic_glance_status)
                .put("graphAbsent", posted?.getLargeIcon() == null)
                .put("chipTextAbsent", android.os.Build.VERSION.SDK_INT < 36 || posted?.shortCriticalText.isNullOrEmpty())
            java.io.File(context.filesDir, "notification-privacy-probe.json").writeText(privacy.toString())
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
        if (mode == "notification-privacy") {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            app.setLockScreenValuesVisible(intent.getBooleanExtra("visible", false))
            app.showCurrentNotification()
            return
        }
        if (mode == "dismiss-active" || mode == "dismiss-saved") {
            val generation = if (mode == "dismiss-saved") {
                context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE)
                    .getLong("dismiss_generation", Long.MIN_VALUE)
            } else MonitoringService.currentActionGeneration ?: return
            val epoch = if (mode == "dismiss-saved") {
                context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE)
                    .getLong("dismiss_epoch", Long.MIN_VALUE)
            } else com.khaleel.glance.notification.NotificationVisibility.epoch(context)
            context.sendBroadcast(Intent(context, com.khaleel.glance.notification.NotificationDismissReceiver::class.java)
                .setAction(com.khaleel.glance.notification.NotificationDismissReceiver.ACTION_DISMISSED)
                .putExtra(com.khaleel.glance.notification.NotificationDismissReceiver.EXTRA_GENERATION,
                    intent.getLongExtra("generation", generation))
                .putExtra(com.khaleel.glance.notification.NotificationDismissReceiver.EXTRA_PRESENTATION_EPOCH, epoch))
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
                .put("promotableCharacteristics", notification.hasPromotableCharacteristics())
                .put("colorizedPromotableCharacteristics", builder.setColorized(true).build().hasPromotableCharacteristics())
            java.io.File(context.filesDir, "live-api-check.json").writeText(result.toString())
            manager.notify(GlucoseNotificationRenderer.NOTIFICATION_ID, notification)
            return
        }
        if (mode == "service-demo") {
            context.getSharedPreferences("debug_fixture", Context.MODE_PRIVATE).edit()
                .putBoolean("enabled", true).putInt("poll_count", 0).remove("source_failure").commit()
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
        if (mode == "alert-expiry-demo") {
            val app = context.applicationContext as com.khaleel.glance.GlanceApplication
            app.controller.settings.numericStatusIcon = true
            app.alertNotifier.ensureChannels()
            val now = Instant.now()
            val sourceTime = now.minusSeconds(288)
            val reading = Reading("synthetic-expiry-person", sourceTime, 240.0, 240.0,
                GlucoseUnit.MG_DL, null, Trend.STEADY, "synthetic-expiry", now)
            val display = DisplayState(true, RuntimeState.RUNNING, ConnectionState.READY,
                Connection("synthetic-expiry-person", "DEMO · Expiry"), reading,
                ReadingFreshness.FRESH, now, null)
            app.alertNotifier.post(AlertKind.HIGH, display, GlucoseUnit.MG_DL, true)
            java.io.File(context.filesDir, "alert-expiry-fixture.json").writeText(
                org.json.JSONObject().put("synthetic", true).put("sourceTime", sourceTime.toString())
                    .put("freshnessDeadline", sourceTime.plusSeconds(300).toString()).toString())
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
                .putLong("dismiss_generation", MonitoringService.currentActionGeneration ?: Long.MIN_VALUE)
                .putLong("dismiss_epoch", com.khaleel.glance.notification.NotificationVisibility.epoch(context)).commit()
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
