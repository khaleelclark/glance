package com.khaleel.glance.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
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
import com.khaleel.glance.alerts.AlertHistoryEntry
import com.khaleel.glance.alerts.AlertKind

/** Production screens with local synthetic state only; never starts monitoring or contacts a source. */
class DebugUiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        val mode = intent.getStringExtra("mode") ?: "fresh"
        val accentStore = getSharedPreferences("synthetic-ui-accent", MODE_PRIVATE)
        val initialAccent = if (mode == "accentStored") accentStore.getString("hex", AccentPresets.DEFAULT)!! else AccentPresets.DEFAULT
        if (mode == "accentStorage") {
            java.io.File(filesDir, "accent-storage-check.json").writeText(runAccentStorageProbe(this).toString())
        }
        val now = Instant.parse("2026-10-01T12:00:00Z")
        if (mode == "readingHistoryStorage") {
            java.io.File(filesDir, "reading-history-storage-check.json")
                .writeText(runReadingHistoryStorageProbe(this).toString())
        }
        if (mode == "nicknameStorage") {
            java.io.File(filesDir, "nickname-storage-check.json").writeText(runNicknameStorageProbe(this).toString())
        }
        if (mode == "alarmStorage") {
            java.io.File(filesDir, "alarm-log-storage-check.json")
                .writeText(runAlarmHistoryProbe(this).toString())
        }
        if (mode == "sourceDiagnostics") {
            // Exercise actual Android preferences in a namespace separate from app diagnostics.
            val isolated = object : android.content.ContextWrapper(this) {
                override fun getSharedPreferences(name: String, mode: Int) =
                    super.getSharedPreferences("synthetic-vpn-test-$name", mode)
            }
            val store = com.khaleel.glance.data.DiagnosticsStore(isolated)
            val info = SourceResponseInfo(403, SourceResponseFormat.WEB_PAGE)
            store.clear()
            store.sourceFailure(now, SourceError(SourceErrorKind.PROTOCOL, responseInfo = info))
            check(com.khaleel.glance.data.DiagnosticsStore(isolated).snapshot().lastResponseInfo == info)
            store.pollFailure(now, SourceErrorKind.NETWORK)
            check(store.snapshot().lastResponseInfo == null)
            store.sourceFailure(now, SourceError(SourceErrorKind.PROTOCOL, responseInfo = info))
            store.pollSuccess(now)
            check(store.snapshot().lastResponseInfo == null && store.snapshot().lastError == null)
            store.sourceFailure(now, SourceError(SourceErrorKind.PROTOCOL, responseInfo = info))
            store.clear()
            check(store.snapshot().lastResponseInfo == null && store.snapshot().eventCount == 0)
            java.io.File(filesDir, "vpn-diagnostics-check.json").writeText(
                """{"persistedMetadata":true,"clearedOnNetworkError":true,"clearedOnSuccess":true,"clearedOnDelete":true}""",
            )
        }
        val events = JSONObject()
        fun event(name: String) {
            events.put(name, events.optInt(name) + 1)
            java.io.File(filesDir, "ui-events.json").writeText(events.toString())
        }
        java.io.File(filesDir, "ui-events.json").writeText("{}")
        val lastStep = if (mode == "minuteHistory") 1439 else 288
        val stepSeconds = if (mode == "minuteHistory") 60L else 300L
        val history = (0..lastStep).map { step ->
            val value = if (step == lastStep) when (mode) {
                "low" -> 65.0
                "high" -> 250.0
                else -> 113.0
            } else 150 + 105 * kotlin.math.sin(step / 9.0)
            val canonicalValue = if (mode == "minuteHistory") Math.rint(value) else value
            val ageSeconds = when (mode) { "stale" -> 1200L; "delayed" -> 420L; "invalidTime" -> -600L; "justNow" -> 15L; else -> 60L }
            Reading("synthetic", now.minusSeconds((lastStep - step) * stepSeconds + ageSeconds), canonicalValue,
                canonicalValue.takeUnless { mode == "minuteHistory" },
                GlucoseUnit.MG_DL, null, if (mode == "minuteHistory") Trend.UNKNOWN else Trend.STEADY,
                "synthetic-$step", now)
        }
        val reading = history.last().let {
            when (mode) {
                "minuteHistory" -> it.copy(sourceValue = it.valueMgDl, trend = Trend.STEADY)
                "sourceLow" -> it.copy(valueMgDl = null, sourceValue = null, specialValue = SpecialValue.LOW)
                "sourceHigh" -> it.copy(valueMgDl = null, sourceValue = null, specialValue = SpecialValue.HIGH)
                else -> it
            }
        }
        val initialDisplay = DisplayState(true, RuntimeState.RUNNING,
            if (mode == "offline") ConnectionState.OFFLINE else ConnectionState.READY,
            Connection("synthetic", "DEMO · Alex"), reading.takeUnless { mode == "noReading" },
            when (mode) {
                "stale" -> ReadingFreshness.STALE
                "delayed" -> ReadingFreshness.DELAYED
                "invalidTime" -> ReadingFreshness.INVALID_TIME
                "noReading" -> ReadingFreshness.ABSENT
                else -> ReadingFreshness.FRESH
            },
            now, null, history)
        setContent {
            var display by remember { mutableStateOf(initialDisplay) }
            var alarmHistory by remember { mutableStateOf<List<AlertHistoryEntry>>(
                if (mode == "emptyLog") emptyList() else AlertKind.entries.mapIndexed { index, kind ->
                    val value = when (kind) {
                        AlertKind.LOW -> if (mode == "historicalLow") 113.0 else 65.0
                        AlertKind.URGENT_LOW -> 50.0
                        AlertKind.HIGH -> 250.0
                        AlertKind.URGENT_HIGH -> 310.0
                    }
                    AlertHistoryEntry("demo-alert-$index", display.person!!, kind,
                        reading.copy(valueMgDl = value, sourceValue = value,
                            measuredAt = now.minusSeconds((index + 1) * 3600L)),
                        now.minusSeconds((index + 1) * 3600L - 30L))
                }
            ) }
            var settings by remember { mutableStateOf(UserSettingsUi(
                GlucoseUnit.MG_DL, false, 1, false, true, accentHex = initialAccent,
                liveUpdateStatus = "DEMO · regular card", watchSyncStatus = "DEMO · watch disconnected", sourcePersonName = "DEMO · Alex")) }
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
                onAccentHexChanged = {
                    check(accentStore.edit().putString("hex", it).commit())
                    settings = settings.copy(accentHex = it); event("accent")
                },
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
                onNicknameChanged = { nickname ->
                    settings = settings.copy(nickname = nickname.trim())
                    display = display.copy(person = display.person?.copy(displayName = nickname.trim().takeIf { it.isNotBlank() }?.let { "DEMO · $it" } ?: "DEMO · Alex"))
                    event("nickname")
                },
                onClearAlertLog = { alarmHistory = emptyList(); event("clearLog") },
            )
            Box(Modifier.fillMaxSize()) {
            val blockedResponse = SourceResponseInfo(403, SourceResponseFormat.WEB_PAGE)
            GlanceRoot(display,
                AccountUiState(AccountStatus.SIGNED_OUT,
                    if (mode == "blockedSignIn") AccountProblem.PROTOCOL else null,
                    if (mode == "blockedSignIn") blockedResponse else null),
                ConnectionSetupUiState(
                    problem = if (mode == "blockedConnections") ConnectionProblem.PROTOCOL else null,
                    responseInfo = if (mode == "blockedConnections") blockedResponse else null),
                SetupUiState(when (mode) {
                    "signIn", "blockedSignIn" -> SetupStep.SIGN_IN
                    "blockedConnections" -> SetupStep.CONNECTION
                    else -> SetupStep.COMPLETE
                },
                    PermissionStatus.GRANTED, BatteryStatus.UNRESTRICTED),
                settings, callbacks, modifier = Modifier,
                now = now, zoneId = ZoneId.of("UTC"), locale = Locale.US,
                alertHistory = alarmHistory)

            }
        }
    }
}
