package com.khaleel.glance.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.khaleel.glance.core.*
import com.khaleel.glance.ui.*
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject

/** Production screens with local synthetic state only; never starts monitoring or contacts a source. */
class DebugUiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val light = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK !=
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.decorView.windowInsetsController?.setSystemBarsAppearance(
            if (light) android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        )
        val mode = intent.getStringExtra("mode") ?: "fresh"
        val now = Instant.parse("2026-10-01T12:00:00Z")
        val events = JSONObject()
        fun event(name: String) {
            events.put(name, events.optInt(name) + 1)
            java.io.File(filesDir, "ui-events.json").writeText(events.toString())
        }
        java.io.File(filesDir, "ui-events.json").writeText("{}")
        val history = (0..288).map { step ->
            val value = if (step == 288) 113.0 else 150 + 105 * kotlin.math.sin(step / 9.0)
            val ageSeconds = if (mode == "stale") 1200L else 60L
            Reading("synthetic", now.minusSeconds((288 - step) * 300L + ageSeconds), value, value,
                GlucoseUnit.MG_DL, null, Trend.STEADY, "synthetic-$step", now)
        }
        val reading = history.last()
        val display = DisplayState(true, RuntimeState.RUNNING,
            if (mode == "offline") ConnectionState.OFFLINE else ConnectionState.READY,
            Connection("synthetic", "DEMO · Alex"), reading,
            if (mode == "stale") ReadingFreshness.STALE else ReadingFreshness.FRESH,
            now, null, history)
        setContent {
            var settings by remember { mutableStateOf(UserSettingsUi(
                GlucoseUnit.MG_DL, false, 1, false, true,
                liveUpdateStatus = "DEMO · regular card", watchSyncStatus = "DEMO · watch disconnected")) }
            val callbacks = GlanceCallbacks(
                onSignIn = { event("signIn") }, onSelectConnection = { event("selectPerson") },
                onConfirmConnection = { event("confirmPerson") }, onRetryConnections = { event("retryPeople") },
                onRequestNotificationPermission = { event("permission") },
                onOpenNotificationSettings = { event("notificationSettings") },
                onOpenBatterySettings = { event("batterySettings") }, onStartMonitoring = { event("start") },
                onStopMonitoring = { event("stop") }, onResumeMonitoring = { event("resume") },
                onRefresh = { event("refresh") }, onRestoreNotification = { event("restore") },
                onUnitChanged = { settings = settings.copy(unit = it); event("unit") },
                onLockScreenVisibilityChanged = { settings = settings.copy(showOnLockScreen = it); event("privacy") },
                onIntervalChanged = { settings = settings.copy(intervalMinutes = it); event("interval") },
                onEnhancedNotificationChanged = { settings = settings.copy(enhancedNotification = it); event("liveUpdate") },
                onNumericStatusIconChanged = { settings = settings.copy(numericStatusIcon = it); event("numeric") },
                onOpenLiveUpdateSettings = { event("liveUpdateSettings") },
                onAccentHexChanged = { settings = settings.copy(accentHex = it); event("accent") },
                onAlertRuleChanged = { kind, rule ->
                    if (mode == "alertSaveFailure") {
                        // Simulate the production host rejecting a save and keeping
                        // the authoritative config unchanged. Debug/synthetic only.
                        event("alertRejected")
                    } else {
                        settings = settings.copy(alerts = com.khaleel.glance.alerts.AlertConfig(settings.alerts.rules + (kind to rule)))
                        java.io.File(filesDir, "ui-alert.json").writeText(JSONObject()
                            .put("kind", kind.name).put("thresholdMgDl", rule.thresholdMgDl)
                            .put("cooldownMinutes", rule.cooldownMinutes).put("enabled", rule.enabled).toString())
                        event("alert")
                    }
                }, onAlertSoundChanged = { event("sound") },
                onResumeAfterBootChanged = { settings = settings.copy(resumeAfterBoot = it); event("boot") },
                onChangePerson = { event("changePerson") }, onReconnect = { event("reconnect") },
                onOpenDiagnostics = { event("diagnostics") }, onDeleteLocalData = { event("delete") },
                onSignOut = { event("signOut") },
                onWidgetValuesChanged = { settings = settings.copy(widgetValuesVisible = it); event("widget") },
                onKeepNotificationVisibleChanged = { settings = settings.copy(keepNotificationVisible = it); event("keep") },
                onWatchValuesChanged = { settings = settings.copy(watchValuesVisible = it); event("watch") },
                onWatchNotificationsChanged = { settings = settings.copy(watchNotificationsEnabled = it); event("watchNotification") },
            )
            Box(Modifier.fillMaxSize()) {
            GlanceRoot(display, AccountUiState(AccountStatus.SIGNED_OUT), ConnectionSetupUiState(),
                SetupUiState(if (mode == "signIn") SetupStep.SIGN_IN else SetupStep.COMPLETE,
                    PermissionStatus.GRANTED, BatteryStatus.UNRESTRICTED),
                settings, callbacks, modifier = Modifier.padding(bottom = 40.dp),
                now = now, zoneId = ZoneId.of("UTC"), locale = Locale.US)
            Text("DEMO · UI preview", Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .background(Color(0xFFE4E8E8)).padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color(0xFF172122))
            }
        }
    }
}
