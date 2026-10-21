package com.khaleel.glance.debug

import android.app.Activity
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.khaleel.glance.R
import com.khaleel.glance.GlanceApplication
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.core.*
import java.time.Instant
import com.khaleel.glance.notification.GlucoseNotificationRenderer
import com.khaleel.glance.notification.GlucoseStatusIcon
import com.khaleel.glance.notification.LiveUpdateSupport
import com.khaleel.glance.monitoring.MonitoringService
import org.json.JSONObject
import java.io.File

/** Real framework grouping/eligibility checks on a dedicated synthetic emulator, never release. */
class NotificationCompatibilityInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    @android.annotation.TargetApi(36) // This fixture explicitly rejects older runtime images below.
    override fun onStart() {
        val context = targetContext
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        check(Build.VERSION.SDK_INT >= 36)
        val manager = context.getSystemService(NotificationManager::class.java)
        val checks = JSONObject()
        val facts = JSONObject().put("sdkFull", Build.VERSION.SDK_INT_FULL)
        val ids = (6100..6103)
        val app = context.applicationContext as GlanceApplication
        val oldNumeric = app.controller.settings.numericStatusIcon
        val notifier = app.alertNotifier
        fun state(value: Double): DisplayState {
            val now = Instant.now()
            return DisplayState(true, RuntimeState.RUNNING, ConnectionState.READY,
                Connection("synthetic-compatibility", "DEMO · Compatibility"),
                Reading("synthetic-compatibility", now, value, value, GlucoseUnit.MG_DL, null,
                    Trend.STEADY, "synthetic", now), ReadingFreshness.FRESH, now, null)
        }
        fun builder() = Notification.Builder(context, GlucoseNotificationRenderer.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_glance_status).setContentTitle("DEMO · Compatibility")
            .setContentText("Synthetic framework check").setOngoing(true).setOnlyAlertOnce(true)
            .setStyle(Notification.BigTextStyle().bigText("Synthetic framework check"))
        fun matches(icon: Icon, text: String): Boolean {
            val actual = (icon.loadDrawable(context) as? BitmapDrawable)?.bitmap ?: return false
            val expected = (GlucoseStatusIcon.create(text).loadDrawable(context) as BitmapDrawable).bitmap
            return actual.sameAs(expected)
        }
        fun record(name: String, predicate: () -> Boolean) {
            check(predicate()) { name }; checks.put(name, true)
        }
        fun await(predicate: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 12_000
            while (!predicate()) { check(SystemClock.uptimeMillis() < end); SystemClock.sleep(50) }
        }
        fun summary() = manager.activeNotifications.firstOrNull {
            it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        }?.notification
        var resultCode = Activity.RESULT_CANCELED
        var resultExtras = Bundle()
        try {
            app.controller.setMonitoringEnabled(false)
            context.stopService(Intent(context, MonitoringService::class.java))
            app.hideCurrentNotification()
            manager.cancelAll()
            await { manager.activeNotifications.isEmpty() }
            manager.createNotificationChannel(android.app.NotificationChannel(
                GlucoseNotificationRenderer.CHANNEL_ID, "Glucose monitoring", NotificationManager.IMPORTANCE_LOW))
            val oldLayout = builder().build()
            val colorizedLayout = builder().setColorized(true).build()
            facts.put("plainLayoutEligible", oldLayout.hasPromotableCharacteristics())
                .put("colorizedLayoutEligible", colorizedLayout.hasPromotableCharacteristics())
                .put("promotionAllowed", manager.canPostPromotedNotifications())
            val liveBuilder = builder()
            LiveUpdateSupport.request(liveBuilder, "112")
            val live = liveBuilder.build()
            record("requestedLayoutMeetsActualFrameworkEligibility") { live.hasPromotableCharacteristics() }
            record("colorizationOnlyUsedWhenPlainLayoutIneligible") {
                live.extras.getBoolean(Notification.EXTRA_COLORIZED) == !oldLayout.hasPromotableCharacteristics()
            }
            record("promotionRequestAndCriticalTextPreserved") {
                live.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING) && live.shortCriticalText == "112"
            }
            record("promotedRequestHasNoCustomViewsOrGroupSummary") {
                live.contentView == null && live.bigContentView == null &&
                    live.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
            val invalidBuilder = builder().setContentTitle(null)
            LiveUpdateSupport.request(invalidBuilder, "")
            record("ineligibleLayoutDoesNotKeepUnhelpfulColorization") {
                !invalidBuilder.build().hasPromotableCharacteristics() &&
                    !invalidBuilder.build().extras.getBoolean(Notification.EXTRA_COLORIZED)
            }
            // Deliberately cross the system's grouping threshold. Production has at most one
            // alarm plus one monitoring card; this tests GroupHelper's icon decision separately.
            ids.forEach { id ->
                val pixels = (GlucoseStatusIcon.create("112").loadDrawable(context) as BitmapDrawable).bitmap
                manager.notify(id, builder().setSmallIcon(Icon.createWithBitmap(pixels)).build())
            }
            await { summary() != null }
            record("oldSeparateBitmapIconsProduceNonNumericAutoSummary") { !matches(summary()!!.smallIcon, "112") }
            manager.cancelAll()
            await { manager.activeNotifications.isEmpty() }
            ids.forEach { id -> manager.notify(id,
                builder().setSmallIcon(GlucoseStatusIcon.create("112", encoded = true)).build()) }
            await { summary()?.smallIcon?.let { matches(it, "112") } == true }
            record("contentEncodedIconsProduceNumericInitialAutoSummary") { matches(summary()!!.smallIcon, "112") }
            record("encodedIconPixelsAndTypeSurviveSystemPosting") {
                manager.activeNotifications.filter { it.id in ids }.all {
                    it.notification.smallIcon.type == Icon.TYPE_DATA && matches(it.notification.smallIcon, "112")
                }
            }
            record("productionEncodingIsEnabledOnlyOnAndroid17OrNewer") {
                GlucoseStatusIcon.create("112").type ==
                    if (Build.VERSION.SDK_INT >= 37) Icon.TYPE_DATA else Icon.TYPE_BITMAP
            }
            if (Build.VERSION.SDK_INT >= 37) {
                ids.forEach { id -> manager.notify(id,
                    builder().setSmallIcon(GlucoseStatusIcon.create("113")).build()) }
                await { summary()?.smallIcon?.let { matches(it, "113") } == true }
                checks.put("android17AutoSummaryTracksUpdatedCommonValue", true)
                ids.forEach { id -> manager.notify(id, builder().build()) }
                await { summary()?.smallIcon?.type == Icon.TYPE_RESOURCE }
                checks.put("android17AutoSummaryDropsNumberWhenChildrenDropIt", true)
            }
            manager.cancelAll()
            await { manager.activeNotifications.isEmpty() }
            context.startActivity(Intent(context, DebugNotificationActivity::class.java)
                .putExtra("mode", "service-demo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await { manager.activeNotifications.any {
                it.id == GlucoseNotificationRenderer.NOTIFICATION_ID &&
                    it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
            } }
            await { app.controller.display.value.runtime == RuntimeState.RUNNING }
            app.controller.settings.numericStatusIcon = true
            notifier.ensureChannels()
            val initial = state(240.0)
            GlucoseNotificationRenderer(context, initial, GlucoseUnit.MG_DL, true).post()
            notifier.post(AlertKind.HIGH, initial, GlucoseUnit.MG_DL, true)
            fun monitoring() = manager.activeNotifications.firstOrNull {
                it.id == GlucoseNotificationRenderer.NOTIFICATION_ID
            }
            fun alarm() = manager.activeNotifications.firstOrNull {
                it.notification.channelId.startsWith("glucose_alert_")
            }
            await { alarm() != null && monitoring() != null }
            record("twoSeparateNumericCardsWithoutAppProvidedGroupOrSummary") {
                val active = manager.activeNotifications
                active.size == 2 && active.all {
                    it.notification.group == null && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
                } && matches(alarm()!!.notification.smallIcon, "240") &&
                    matches(monitoring()!!.notification.smallIcon, "240")
            }
            val trigger = alarm()!!.notification
            val latest = state(248.0)
            GlucoseNotificationRenderer(context, latest, GlucoseUnit.MG_DL, true).post()
            notifier.refreshIcon(latest, GlucoseUnit.MG_DL, true)
            await { alarm()?.notification?.smallIcon?.let { matches(it, "248") } == true }
            record("alertTracksLatestNumberSilentlyAndKeepsTrigger") {
                val current = alarm()!!.notification
                current.extras.getCharSequence(Notification.EXTRA_TEXT) == trigger.extras.getCharSequence(Notification.EXTRA_TEXT) &&
                    current.`when` == trigger.`when` && current.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
            }
            record("monitoringChildRemainsOngoingAndNotSummary") {
                val current = monitoring()!!.notification
                current.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
                    current.flags and Notification.FLAG_GROUP_SUMMARY == 0 && matches(current.smallIcon, "248")
            }
            alarm()!!.notification.deleteIntent.send()
            await { alarm() == null }
            record("alertDeleteIntentCallbackKeepsNumericMonitoringCard") {
                monitoring()?.notification?.smallIcon?.let { matches(it, "248") } == true
            }
            val expiring = state(55.0).let { it.copy(reading = it.reading!!.copy(
                measuredAt = Instant.now().minusSeconds(298))) }
            notifier.post(AlertKind.LOW, expiring, GlucoseUnit.MG_DL, true)
            await { alarm() != null }
            await { alarm() == null }
            record("systemExpiryOfAlertKeepsForegroundMonitoringCard") {
                val current = monitoring()?.notification
                current != null && current.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 &&
                    matches(current.smallIcon, "248")
            }
            val diagnostics = LiveUpdateSupport.diagnostics(context)
            record("notificationDiagnosticsContainNoFixtureValueOrName") {
                !diagnostics.contains("240") && !diagnostics.contains("248") && !diagnostics.contains("DEMO")
            }
            val result = JSONObject().put("synthetic", true).put("allPassed", true)
                .put("checks", checks).put("framework", facts)
                .put("scope", "API36 notification grouping/eligibility; no Samsung, actual promotion, sound, or live source qualification")
            File(context.filesDir, "notification-compatibility-probe.json").writeText(result.toString(2))
            resultCode = Activity.RESULT_OK
            resultExtras = Bundle().apply { putString("checks", checks.toString()) }
        } catch (failure: Throwable) {
            resultExtras = Bundle().apply {
                putString("failure", failure.stackTraceToString())
                putString("completedChecks", checks.toString())
            }
        } finally {
            app.controller.setMonitoringEnabled(false)
            context.stopService(Intent(context, MonitoringService::class.java))
            app.hideCurrentNotification()
            notifier.clear()
            ids.forEach { manager.cancel(it) }
            app.controller.settings.numericStatusIcon = oldNumeric
        }
        finish(resultCode, resultExtras)
    }
}
