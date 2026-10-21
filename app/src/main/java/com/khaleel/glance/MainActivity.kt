package com.khaleel.glance

import com.khaleel.glance.core.ConnectionState

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.SourceErrorKind
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.data.LocalHealthDataCleanup
import com.khaleel.glance.monitoring.MonitoringService
import com.khaleel.glance.notification.NotificationVisibility
import com.khaleel.glance.notification.LiveUpdateSupport
import com.khaleel.glance.ui.AccountProblem
import com.khaleel.glance.ui.AccountStatus
import com.khaleel.glance.ui.AccountUiState
import com.khaleel.glance.ui.BatteryStatus
import com.khaleel.glance.ui.ConnectionProblem
import com.khaleel.glance.ui.ConnectionSetupUiState
import com.khaleel.glance.ui.GlanceCallbacks
import com.khaleel.glance.ui.GlanceRoot
import com.khaleel.glance.ui.PermissionStatus
import com.khaleel.glance.ui.SetupStep
import com.khaleel.glance.ui.SetupUiState
import com.khaleel.glance.ui.UserSettingsUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant

/** Android effects and persistence live here; Compose receives snapshots and callbacks. */
class MainActivity : ComponentActivity() {
    private val app get() = application as GlanceApplication
    private val controller get() = app.controller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val setupPrefs by lazy { getSharedPreferences("onboarding", MODE_PRIVATE) }

    private var accountState by mutableStateOf(AccountUiState(AccountStatus.SIGNED_OUT))
    private var connectionState by mutableStateOf(ConnectionSetupUiState())
    private var setupState by mutableStateOf(SetupUiState(SetupStep.SIGN_IN))
    private var settingsUi by mutableStateOf(UserSettingsUi(GlucoseUnit.MG_DL, false, 1, true, true))
    private var showDiagnostics by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshSystemState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.honorExplicitSystemStop()
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        accountState = AccountUiState(if (controller.hasAccount) AccountStatus.SIGNED_IN else AccountStatus.SIGNED_OUT)
        setupState = SetupUiState(
            step = when {
                !controller.hasAccount -> SetupStep.SIGN_IN
                controller.display.value.person == null -> SetupStep.CONNECTION
                setupPrefs.getBoolean("complete", false) -> SetupStep.COMPLETE
                else -> SetupStep.NOTIFICATION_PRIVACY
            },
        )
        refreshSettings()
        refreshSystemState()
        if (setupState.step == SetupStep.CONNECTION) loadConnections()
        if (controller.display.value.monitoringEnabled &&
            controller.display.value.runtime != RuntimeState.RUNNING &&
            NotificationVisibility.isDismissed(this)
        ) {
            controller.setMonitoringEnabled(false)
        }
        if (controller.display.value.monitoringEnabled &&
            controller.display.value.person != null &&
            controller.display.value.runtime != RuntimeState.RUNNING
        ) {
            if (setupState.notificationPermission == PermissionStatus.GRANTED) {
                startMonitoring(restoreNotification = false)
            } else {
                controller.setRuntime(RuntimeState.UNABLE_TO_START)
                setupState = setupState.copy(step = SetupStep.NOTIFICATION_PRIVACY)
            }
        }

        setContent {
            val display by controller.display.collectAsState()
            val alarmHistory by app.alertHistory.events.collectAsState()
            LaunchedEffect(display.person?.id) { refreshSettings() }
            var now by androidx.compose.runtime.remember { mutableStateOf(Instant.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(30_000L)
                    now = Instant.now()
                    controller.updateFreshness()
                }
            }
            LaunchedEffect(display.runtime) {
                when (display.runtime) {
                    RuntimeState.RUNNING -> {
                        setupPrefs.edit().putBoolean("complete", true).apply()
                        setupState = setupState.copy(step = SetupStep.COMPLETE, startingMonitoring = false, startFailed = false)
                    }
                    RuntimeState.UNABLE_TO_START ->
                        setupState = setupState.copy(startingMonitoring = false, startFailed = true)
                    else -> Unit
                }
            }
            GlanceRoot(
                displayState = display,
                accountState = accountState,
                connectionState = connectionState,
                setupState = setupState,
                settings = settingsUi,
                callbacks = callbacks,
                now = now,
                alertHistory = alarmHistory,
            )
            if (showDiagnostics) {
                val diagnostic = controller.diagnostics.snapshot()
                val report = "Glance " + BuildConfig.VERSION_NAME + " · Android " + Build.VERSION.RELEASE +
                    " (API " + Build.VERSION.SDK_INT + ")\n" +
                    "Monitoring: " + display.runtime + "\n" +
                    "Connection: " + display.connection + "\n" +
                    "Reading: " + display.freshness + "\n" +
                    "Last attempt: " + (diagnostic.lastAttempt ?: "none") + "\n" +
                    "Last success: " + (diagnostic.lastSuccess ?: "none") + "\n" +
                    "Last error: " + (diagnostic.lastError ?: "none") + "\n" +
                    "Last error response: " + (diagnostic.lastResponseInfo?.summary() ?: "none") + "\n" +
                    "Last alert delivery issue: " + (diagnostic.lastAlertIssue ?: "none") + "\n" +
                    "Largest poll gap: " +
                    (diagnostic.largestObservedGapMillis?.let { it / 1000L } ?: "none") + " seconds\n" +
                    "Events retained: " + diagnostic.eventCount + "\n" +
                    "Next allowed fetch: " + (display.nextAllowedFetch ?: "now") + "\n" +
                    "Notification permission: " + setupState.notificationPermission + "\n" +
                    "Numeric status icons enabled: " + controller.settings.numericStatusIcon + "\n" +
                    "Lock-screen values enabled: " + controller.settings.showOnLockScreen + "\n" +
                    "Live Update: " + LiveUpdateSupport.status(this) + "\n" +
                    LiveUpdateSupport.diagnostics(this)
                AlertDialog(
                    onDismissRequest = { showDiagnostics = false },
                    title = { Text("Diagnostics") },
                    text = { Text(report) },
                    confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("Close") } },
                    dismissButton = {
                        TextButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            startActivity(Intent.createChooser(send, "Export redacted diagnostics"))
                        }) { Text("Export") }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemState()
        if (controller.display.value.monitoringEnabled && controller.display.value.runtime == RuntimeState.RUNNING) {
            app.showCurrentNotification()
            refreshSettings()
        }
        if (setupState.notificationPermission == PermissionStatus.DENIED &&
            controller.display.value.monitoringEnabled
        ) {
            controller.setRuntime(RuntimeState.UNABLE_TO_START)
            stopService(Intent(this, MonitoringService::class.java))
            app.hideCurrentNotification()
            setupState = setupState.copy(step = SetupStep.NOTIFICATION_PRIVACY)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshSettings() {
        val settings = controller.settings
        LauncherAccent.apply(this, settings.accentHex)
        settingsUi = UserSettingsUi(
            settings.unit,
            settings.showOnLockScreen,
            settings.intervalMinutes,
            settings.enhancedNotification,
            settings.resumeAfterBoot,
            settings.accentHex,
            app.alertPreferences.config(),
            settings.numericStatusIcon,
            LiveUpdateSupport.status(this),
            app.widgetPreferences.valuesVisible,
            NotificationVisibility.keepVisible(this),
            app.watchPreferences.valuesVisible,
            app.watchPreferences.notificationsEnabled,
            app.watchPublisher.syncStatus,
            nickname = controller.nickname,
            sourcePersonName = controller.sourcePersonName,
        )
    }

    private fun refreshSystemState() {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val power = getSystemService(PowerManager::class.java)
        setupState = setupState.copy(
            notificationPermission = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
            batteryStatus = if (power.isIgnoringBatteryOptimizations(packageName)) {
                BatteryStatus.UNRESTRICTED
            } else {
                BatteryStatus.STANDARD
            },
        )
        refreshSettings()
    }

    private fun loadConnections() {
        connectionState = connectionState.copy(loading = true, problem = null, responseInfo = null)
        scope.launch {
            val result = runCatching { controller.connections() }.getOrNull()
            connectionState = when (result) {
                is SourceResult.Success -> ConnectionSetupUiState(
                    connections = result.value,
                    problem = if (result.value.isEmpty()) ConnectionProblem.NONE_FOUND else null,
                )
                is SourceResult.Failure -> ConnectionSetupUiState(
                    problem = result.error.kind.connectionProblem(), responseInfo = result.error.responseInfo,
                )
                null -> ConnectionSetupUiState(problem = ConnectionProblem.PROTOCOL)
            }
        }
    }

    private fun startMonitoring(restoreNotification: Boolean = true) {
        refreshSystemState()
        if (setupState.notificationPermission != PermissionStatus.GRANTED) return
        setupState = setupState.copy(startingMonitoring = true, startFailed = false)
        if (!MonitoringService.start(this, restoreNotification)) {
            setupState = setupState.copy(startingMonitoring = false, startFailed = true)
        }
    }

    private fun stopMonitoring() {
        controller.setMonitoringEnabled(false)
        app.widgetRenderer.redact()
        app.watchPublisher.redact()
        stopService(Intent(this, MonitoringService::class.java))
        app.hideCurrentNotification()
        app.alertEngine.clearNotifications()
        setupState = setupState.copy(startingMonitoring = false)
    }

    private fun renderSettingsChange() {
        if (controller.display.value.monitoringEnabled) app.showCurrentNotification()
        app.updateWidgets()
        app.watchPublisher.refresh()
        refreshSettings()
    }

    private val callbacks = GlanceCallbacks(
        onSignIn = { credentials ->
            stopMonitoring()
            // A new sign-in begins a new account session; no previous account's log follows it.
            if (clearAlarmLog()) {
                accountState = AccountUiState(AccountStatus.SIGNING_IN)
                scope.launch {
                    val result = runCatching { controller.signIn(credentials) }.getOrNull()
                    when (result) {
                        is SourceResult.Success -> {
                            accountState = AccountUiState(AccountStatus.SIGNED_IN)
                            connectionState = ConnectionSetupUiState(
                                connections = result.value,
                                problem = if (result.value.isEmpty()) ConnectionProblem.NONE_FOUND else null,
                            )
                            setupState = setupState.copy(step = SetupStep.CONNECTION)
                        }
                        is SourceResult.Failure ->
                            accountState = AccountUiState(
                                AccountStatus.SIGNED_OUT, result.error.kind.accountProblem(), result.error.responseInfo,
                            )
                        null -> accountState = AccountUiState(AccountStatus.SIGNED_OUT, AccountProblem.PROTOCOL)
                    }
                }
            } else accountState = AccountUiState(AccountStatus.SIGNED_OUT, AccountProblem.STORAGE)
        },
        onSelectConnection = { selected ->
            connectionState = connectionState.copy(selectedConnectionId = selected.id)
        },
        onConfirmConnection = {
            val selected = connectionState.connections.firstOrNull { it.id == connectionState.selectedConnectionId }
            if (selected != null) {
                when (controller.selectPerson(selected)) {
                    is SourceResult.Success -> {
                        setupState = setupState.copy(step = SetupStep.NOTIFICATION_PRIVACY)
                        scope.launch { controller.poll() }
                    }
                    is SourceResult.Failure ->
                        connectionState = connectionState.copy(problem = ConnectionProblem.STORAGE)
                }
            }
        },
        onRetryConnections = { loadConnections() },
        onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else refreshSystemState()
        },
        onOpenNotificationSettings = {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        },
        onOpenBatterySettings = { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
        onStartMonitoring = { startMonitoring() },
        onStopMonitoring = { stopMonitoring() },
        onResumeMonitoring = { startMonitoring() },
        onRefresh = {
            scope.launch {
                val result = controller.poll(manual = true)
                if (result is SourceResult.Failure && result.error.kind == SourceErrorKind.REFRESH_DEBOUNCED) {
                    android.widget.Toast.makeText(this@MainActivity,
                        "Please wait a few seconds before refreshing again.", android.widget.Toast.LENGTH_SHORT).show()
                }
                controller.updateFreshness()
                runCatching { app.onPollResult(result) }
                    .onFailure { controller.diagnostics.alertIssue(Instant.now()) }
                if (controller.display.value.monitoringEnabled) app.showCurrentNotification()
            }
        },
        onRestoreNotification = {
            if (controller.display.value.monitoringEnabled) app.showCurrentNotification(restore = true)
        },
        onUnitChanged = { controller.settings.unit = it; renderSettingsChange() },
        onLockScreenVisibilityChanged = {
            saveLockScreenVisibility(it)
            renderSettingsChange()
        },
        onIntervalChanged = { controller.settings.intervalMinutes = it; renderSettingsChange() },
        onEnhancedNotificationChanged = {
            controller.settings.enhancedNotification = it
            if (it) NotificationVisibility.restore(this)
            renderSettingsChange()
        },
        onKeepNotificationVisibleChanged = { visible ->
            runCatching { NotificationVisibility.setKeepVisible(this, visible) }
                .onSuccess {
                    if (visible && controller.display.value.monitoringEnabled) app.showCurrentNotification()
                }.onFailure {
                    Toast.makeText(this, "Could not save notification setting; previous choice retained.",
                        Toast.LENGTH_LONG).show()
                }
            refreshSettings()
        },
        onNumericStatusIconChanged = { controller.settings.numericStatusIcon = it; renderSettingsChange() },
        onOpenLiveUpdateSettings = {
            val liveSettings = LiveUpdateSupport.settingsIntent(this)
            try {
                startActivity(liveSettings)
            } catch (_: android.content.ActivityNotFoundException) {
                startActivity(LiveUpdateSupport.appSettingsIntent(this))
            }
        },
        onNicknameChanged = { nickname ->
            when (controller.setNickname(nickname)) {
                is SourceResult.Success -> renderSettingsChange()
                is SourceResult.Failure -> Toast.makeText(this,
                    "Could not save nickname; previous name retained.", Toast.LENGTH_LONG).show()
            }
            refreshSettings()
        },
        onAccentHexChanged = { controller.settings.accentHex = it; refreshSettings() },
        onAlertRuleChanged = { kind, rule ->
            runCatching { app.alertPreferences.setRule(kind, rule) }
                .onFailure {
                    Toast.makeText(this, "Could not save alert setting", Toast.LENGTH_LONG).show()
                }
            refreshSettings()
        },
        onAlertSoundChanged = { kind ->
            startActivity(app.alertNotifier.channelSettingsIntent(kind))
        },
        onResumeAfterBootChanged = { controller.settings.resumeAfterBoot = it; refreshSettings() },
        onChangePerson = {
            stopMonitoring()
            controller.clearSelectedPerson()
            connectionState = ConnectionSetupUiState()
            setupState = setupState.copy(step = SetupStep.CONNECTION)
            loadConnections()
        },
        onReconnect = {
            if (controller.display.value.connection == ConnectionState.CONNECTION_REMOVED) {
                stopMonitoring()
                controller.clearSelectedPerson()
                setupState = setupState.copy(step = SetupStep.CONNECTION)
                loadConnections()
            } else if (controller.display.value.connection == ConnectionState.SIGN_IN_REQUIRED ||
                controller.display.value.connection == ConnectionState.TERMS_REQUIRED
            ) {
                stopMonitoring()
                accountState = AccountUiState(AccountStatus.SIGNED_OUT)
                setupState = setupState.copy(step = SetupStep.SIGN_IN)
            } else {
                setupState = setupState.copy(step = SetupStep.CONNECTION)
                loadConnections()
            }
        },
        onOpenDiagnostics = { showDiagnostics = true },
        onDeleteLocalData = {
            val cleared = LocalHealthDataCleanup.run(
                clearReadings = controller::clearLocalData,
                clearHistory = app.alertEngine::clearHistory,
                clearAlertSnapshot = app.alertPreferences::clearSnapshot,
                clearAlertNotifications = app.alertEngine::clearNotifications,
                updateMonitoring = { readingsCleared ->
                    if (readingsCleared && controller.display.value.monitoringEnabled) {
                        app.showCurrentNotification()
                    } else app.hideCurrentNotification()
                },
            )
            if (!cleared) Toast.makeText(this, "Could not clear all local data. Please try again.", Toast.LENGTH_LONG).show()
        },
        onSignOut = { signOut() },
        onWidgetValuesChanged = {
            saveWidgetVisibility(it)
            if (!it) app.widgetRenderer.redact()
            app.updateWidgets()
            refreshSettings()
        },
        onWatchValuesChanged = {
            saveWatchVisibility(it)
            refreshSettings()
        },
        onWatchNotificationsChanged = {
            runCatching { app.watchPublisher.setNotificationsEnabled(it) }.onFailure {
                Toast.makeText(this, "Could not save watch notification setting; previous choice retained.", Toast.LENGTH_LONG).show()
            }
            refreshSettings()
        },
        onClearAlertLog = { clearAlarmLog() },
    )

    /** Clearing the visible log deliberately retains alert deduplication and cooldowns. */
    private fun clearAlarmLog(showError: Boolean = true): Boolean = runCatching { app.alertEngine.clearHistory() }
        .onFailure {
            if (showError) Toast.makeText(this, "Could not clear the alarm log. Please try again.", Toast.LENGTH_LONG).show()
        }.isSuccess

    private fun signOut() {
        saveLockScreenVisibility(false)
        saveWidgetVisibility(false)
        saveWatchVisibility(false)
        stopMonitoring()
        val logCleared = clearAlarmLog(showError = false)
        val nicknamesCleared = controller.signOut()
        app.alertPreferences.clear()
        refreshSettings()
        setupPrefs.edit().clear().apply()
        accountState = AccountUiState(AccountStatus.SIGNED_OUT)
        setupState = setupState.copy(step = SetupStep.SIGN_IN)
        if (!logCleared || !nicknamesCleared) {
            val remaining = listOfNotNull(if (!logCleared) "alarm log" else null,
                if (!nicknamesCleared) "encrypted nicknames" else null).joinToString(" and ")
            Toast.makeText(this, "Signed out, but the $remaining could not be erased. Please try again.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun saveWidgetVisibility(visible: Boolean) {
        runCatching { app.widgetPreferences.valuesVisible = visible }.onFailure {
            app.widgetRenderer.redact()
            Toast.makeText(this, if (visible) "Could not save Widget values; readings remain hidden."
                else "Could not save hidden widget values. Remove the widgets from your phone and car, then try again.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun saveLockScreenVisibility(visible: Boolean) {
        runCatching { app.setLockScreenValuesVisible(visible) }.onFailure {
            Toast.makeText(this, if (visible) "Could not save lock-screen values; readings remain hidden."
                else "Could not save lock-screen privacy. Hide Glance notifications on the lock screen in Android settings before restarting, then try again.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun saveWatchVisibility(visible: Boolean) {
        runCatching { app.watchPublisher.setValuesVisible(visible) }.onFailure {
            Toast.makeText(this, if (visible) "Could not save watch sharing; readings remain hidden."
                else "Could not save watch privacy. Turn off or remove Glance on the watch before restarting, then try again.",
                Toast.LENGTH_LONG).show()
        }
    }
}

private fun SourceErrorKind.accountProblem(): AccountProblem = when (this) {
    SourceErrorKind.INVALID_CREDENTIALS, SourceErrorKind.AUTH_EXPIRED -> AccountProblem.INVALID_CREDENTIALS
    SourceErrorKind.NETWORK -> AccountProblem.NETWORK
    SourceErrorKind.TERMS_REQUIRED -> AccountProblem.TERMS_REQUIRED
    SourceErrorKind.STORAGE -> AccountProblem.STORAGE
    else -> AccountProblem.PROTOCOL
}

private fun SourceErrorKind.connectionProblem(): ConnectionProblem = when (this) {
    SourceErrorKind.NETWORK -> ConnectionProblem.NETWORK
    SourceErrorKind.CONNECTION_REMOVED -> ConnectionProblem.REMOVED
    SourceErrorKind.STORAGE -> ConnectionProblem.STORAGE
    else -> ConnectionProblem.PROTOCOL
}
