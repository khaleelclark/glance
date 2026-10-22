package com.khaleel.glance.debug

import android.app.Activity
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import com.khaleel.glance.GlanceApplication
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.GlucoseAlertNotifier
import com.khaleel.glance.core.*
import com.khaleel.glance.notification.GlucoseNotificationRenderer
import com.khaleel.glance.notification.GlucoseStatusIcon
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Synthetic framework checks; never enabled in a release APK. */
class AlertIconInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val context = targetContext
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val app = context.applicationContext as GlanceApplication
        val manager = context.getSystemService(NotificationManager::class.java)
        val notifier = app.alertNotifier
        val checks = JSONObject()
        val oldNumeric = app.controller.settings.numericStatusIcon
        fun state(value: Double) = Instant.now().minusSeconds(30).let { time ->
            DisplayState(true, RuntimeState.RUNNING, ConnectionState.READY,
                Connection("synthetic-icon-person", "DEMO · Alex"),
                Reading("synthetic-icon-person", time, value, value, GlucoseUnit.MG_DL, null,
                    Trend.STEADY, "synthetic-icon", time), ReadingFreshness.FRESH, time, null)
        }
        fun alert(): Notification {
            val deadline = SystemClock.uptimeMillis() + 3000
            do {
                manager.activeNotifications.firstOrNull { it.notification.channelId.startsWith("glucose_alert_") &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
                    ?.let { return it.notification }
                SystemClock.sleep(20)
            } while (SystemClock.uptimeMillis() < deadline)
            error("Synthetic alert was not posted")
        }
        // Real NotificationManager sheds bursts above its per-package enqueue rate.
        // Production polls once a minute; synthetic cases must not flood that limiter.
        fun pace() { SystemClock.sleep(600) }
        fun waitFor(test: () -> Boolean) {
            val deadline = SystemClock.uptimeMillis() + 3000
            while (!test()) { check(SystemClock.uptimeMillis() < deadline); SystemClock.sleep(20) }
        }
        fun iconMatches(notification: Notification, text: String): Boolean {
            if (notification.smallIcon.type !in listOf(Icon.TYPE_BITMAP, Icon.TYPE_DATA)) return false
            val actual = (notification.smallIcon.loadDrawable(context) as BitmapDrawable).bitmap
            val expected = (GlucoseStatusIcon.create(text).loadDrawable(context) as BitmapDrawable).bitmap
            return actual.sameAs(expected)
        }
        fun checkResult(name: String, test: () -> Boolean) { check(test()) { name }; checks.put(name, true) }
        fun noAlert() = manager.activeNotifications.none { it.notification.channelId.startsWith("glucose_alert_") &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        try {
            app.controller.settings.numericStatusIcon = true
            notifier.clear(); notifier.ensureChannels()
            for ((kind, value) in listOf(AlertKind.LOW to 65.0, AlertKind.URGENT_LOW to 50.0,
                    AlertKind.HIGH to 240.0, AlertKind.URGENT_HIGH to 305.0)) {
                pace(); notifier.post(kind, state(value), GlucoseUnit.MG_DL, true)
                waitFor { iconMatches(alert(), value.toInt().toString()) }
                checkResult(kind.name.lowercase()+"AlertUsesValueIconAndNamesCategory") {
                    alert().extras.getCharSequence(Notification.EXTRA_TITLE).toString() == "${kind.label} · DEMO · Alex" &&
                    alert().extras.getCharSequence(Notification.EXTRA_TEXT).toString().startsWith("${value.toInt()} mg/dL at ")
                }
            }
            // Low and urgent-low must follow newer samples just like high alerts. The
            // trigger remains in the body; both small icons must show the current sample.
            for ((kind, triggerValue, latestValue) in listOf(
                Triple(AlertKind.LOW, 68.0, 62.0),
                Triple(AlertKind.URGENT_LOW, 54.0, 48.0),
            )) {
                val triggerState = state(triggerValue)
                pace(); notifier.post(kind, triggerState, GlucoseUnit.MG_DL, true)
                waitFor { iconMatches(alert(), triggerValue.toInt().toString()) }
                val trigger = alert()
                val updatedState = state(latestValue)
                pace(); notifier.refreshIcon(updatedState, GlucoseUnit.MG_DL, true)
                waitFor { iconMatches(alert(), latestValue.toInt().toString()) }
                val monitor = GlucoseNotificationRenderer(context, updatedState, GlucoseUnit.MG_DL, true).build()
                checkResult(kind.name.lowercase() + "RefreshMatchesMonitoringIconAndKeepsTrigger") {
                    iconMatches(monitor, latestValue.toInt().toString()) &&
                        alert().extras.getCharSequence(Notification.EXTRA_TEXT) ==
                            trigger.extras.getCharSequence(Notification.EXTRA_TEXT) &&
                        alert().`when` == trigger.`when` && alert().channelId == trigger.channelId &&
                        alert().flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
                }
                val newerSample = updatedState.copy(reading = updatedState.reading!!.copy(
                    measuredAt = updatedState.reading.measuredAt.plusSeconds(10)))
                val priorExpiry = alert().timeoutAfter
                pace(); notifier.refreshIcon(newerSample, GlucoseUnit.MG_DL, true)
                waitFor { alert().timeoutAfter > priorExpiry + 9_000 }
                checkResult(kind.name.lowercase() + "UnchangedNumberRenewsExpiryWithoutRealerting") {
                    iconMatches(alert(), latestValue.toInt().toString()) &&
                        alert().flags and Notification.FLAG_ONLY_ALERT_ONCE != 0
                }
            }
            val initial = state(240.0)
            pace(); notifier.post(AlertKind.HIGH, initial, GlucoseUnit.MG_DL, true)
            waitFor { iconMatches(alert(), "240") }
            val original = alert()
            checkResult("numericAlarmUsesRemainingSourceFreshnessTimeout") {
                original.timeoutAfter in 265_000L..270_000L
            }
            val oldToken = original.extras.getString("com.khaleel.glance.alerts.ICON_TOKEN")!!
            val latest = state(248.0)
            pace(); notifier.refreshIcon(latest, GlucoseUnit.MG_DL, true)
            waitFor { iconMatches(alert(), "248") }
            checkResult("iconTracksLatestValueWhileAlarmTextKeepsTriggerReading") {
                alert().extras.getCharSequence(Notification.EXTRA_TEXT) == original.extras.getCharSequence(Notification.EXTRA_TEXT) &&
                alert().`when` == original.`when` && alert().channelId == original.channelId
            }
            checkResult("iconRefreshSetsOnlyAlertOnce") { alert().flags and Notification.FLAG_ONLY_ALERT_ONCE != 0 }
            val priorTimeout = alert().timeoutAfter
            val sameValueNewSample = latest.copy(reading = latest.reading!!.copy(
                measuredAt = latest.reading.measuredAt.plusSeconds(10)))
            pace(); notifier.refreshIcon(sameValueNewSample, GlucoseUnit.MG_DL, true)
            waitFor { alert().timeoutAfter > priorTimeout + 9_000 }
            checks.put("sameValueNewSampleRenewsSystemExpiry", true)
            val renewedTimeout = alert().timeoutAfter
            pace(); notifier.refreshIcon(sameValueNewSample, GlucoseUnit.MG_DL, true)
            checkResult("duplicateSampleDoesNotExtendSystemExpiry") { alert().timeoutAfter == renewedTimeout }
            pace(); notifier.refreshIcon(latest, GlucoseUnit.MMOL_L, true)
            waitFor { iconMatches(alert(), "13.8") }
            checks.put("iconRefreshUsesChosenUnit", true)
            pace(); notifier.refreshIcon(latest.copy(freshness = ReadingFreshness.STALE), GlucoseUnit.MG_DL, true)
            waitFor { alert().smallIcon.type == Icon.TYPE_RESOURCE }
            checks.put("staleReadingDoesNotExposeOldStatusNumber", true)
            pace(); notifier.refreshIcon(latest, GlucoseUnit.MG_DL, true)
            waitFor { iconMatches(alert(), "248") }
            pace(); notifier.post(AlertKind.URGENT_HIGH, state(310.0), GlucoseUnit.MG_DL, true)
            waitFor { iconMatches(alert(), "310") }
            notifier.dismiss(oldToken)
            checkResult("oldDismissTokenCannotCancelNewAlarm") { iconMatches(alert(), "310") }
            alert().deleteIntent.send()
            waitFor { noAlert() }
            pace(); notifier.refreshIcon(latest, GlucoseUnit.MG_DL, true)
            checkResult("deleteCallbackPreventsRefreshResurrection") { noAlert() }
            pace(); notifier.post(AlertKind.HIGH, initial, GlucoseUnit.MG_DL, true)
            waitFor { iconMatches(alert(), "240") }
            manager.cancel(5200 + AlertKind.HIGH.ordinal)
            waitFor { noAlert() }
            pace(); notifier.refreshIcon(latest, GlucoseUnit.MG_DL, true)
            checkResult("missingHostNotificationIsNotRepostedByRefresh") { noAlert() }
            pace(); notifier.post(AlertKind.HIGH, initial, GlucoseUnit.MG_DL, true)
            waitFor { iconMatches(alert(), "240") }
            pace(); notifier.refreshIcon(latest.copy(person = Connection("other", "Other")), GlucoseUnit.MG_DL, true)
            waitFor { noAlert() }
            checks.put("personChangeClearsOldNumericAlarm", true)
            pace(); notifier.post(AlertKind.HIGH, initial, GlucoseUnit.MG_DL, false)
            waitFor { alert().visibility == Notification.VISIBILITY_PRIVATE }
            checkResult("privateAlarmAndPublicVersionUseResourceIcons") {
                alert().smallIcon.type == Icon.TYPE_RESOURCE && alert().publicVersion.smallIcon.type == Icon.TYPE_RESOURCE &&
                    !alert().publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("240")
            }
            notifier.clear()
            app.controller.settings.numericStatusIcon = false
            pace(); notifier.post(AlertKind.HIGH, initial, GlucoseUnit.MG_DL, true)
            waitFor { alert().visibility == Notification.VISIBILITY_PUBLIC }
            checkResult("disabledNumberSettingAppliesToAlarms") { alert().smallIcon.type == Icon.TYPE_RESOURCE }
            app.controller.settings.numericStatusIcon = true
            val regular = GlucoseNotificationRenderer(context, latest, GlucoseUnit.MG_DL, true).build()
            val enhanced = GlucoseNotificationRenderer(context, latest, GlucoseUnit.MG_DL, true, enhanced = true).build()
            checkResult("regularKeepsNumberAndLiveRequestUsesLogo") {
                iconMatches(regular, "248") &&
                    if (android.os.Build.VERSION.SDK_INT >= 36 &&
                        enhanced.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING)) {
                        enhanced.smallIcon.type == Icon.TYPE_RESOURCE &&
                            enhanced.smallIcon.resId == com.khaleel.glance.R.drawable.ic_glance_status &&
                            enhanced.shortCriticalText == "248"
                    } else iconMatches(enhanced, "248")
            }
            notifier.clear()
            pace(); notifier.post(AlertKind.HIGH, initial, GlucoseUnit.MG_DL, true)
            waitFor { iconMatches(alert(), "240") }
            GlucoseAlertNotifier(context).refreshIcon(latest, GlucoseUnit.MG_DL, true)
            waitFor { noAlert() }
            checks.put("newNotifierDropsUnscopedPriorProcessNumericAlarm", true)
            val receipt = JSONObject().put("synthetic", true).put("checks", checks).put("allPassed", true)
                .put("scope", "Real NotificationManager/bitmap/delete-intent checks; no live source, Samsung or audible qualification")
            File(context.filesDir, "alert-icon-probe.json").writeText(receipt.toString(2))
            finish(Activity.RESULT_OK, Bundle().apply { putString("checks", checks.toString()) })
        } catch (failure: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("failure", failure.stackTraceToString())
                putString("completedChecks", checks.toString())
            })
        } finally { notifier.clear(); app.controller.settings.numericStatusIcon = oldNumeric }
    }
}
