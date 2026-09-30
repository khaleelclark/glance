package com.khaleel.glance

import com.khaleel.glance.core.ConnectionState

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowInsetsController
import android.widget.Toast
import androidx.activity.ComponentActivity
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
        val lightTheme = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
            Configuration.UI_MODE_NIGHT_YES
        window.decorView.windowInsetsController?.setSystemBarsAppearance(
            if (lightTheme) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
        )
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
                    "Last alert delivery issue: " + (diagnostic.lastAlertIssue ?: "none") + "\n" +
                    "Largest poll gap: " +
                    (diagnostic.largestObservedGapMillis?.let { it / 1000L } ?: "none") + " seconds\n" +
                    "Events retained: " + diagnostic.eventCount + "\n" +
                    "Next allowed fetch: " + (display.nextAllowedFetch ?: "now") + "\n" +
                    "Notification permission: " + setupState.notificationPermission + "\n" +
                    "Live Update: " + LiveUpdateSupport.status(this)
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
        connectionState = connectionState.copy(loading = true, problem = null)
        scope.launch {
            val result = runCatching { controller.connections() }.getOrNull()
            connectionState = when (result) {
                is SourceResult.Success -> ConnectionSetupUiState(
                    connections = result.value,
                    problem = if (result.value.isEmpty()) ConnectionProblem.NONE_FOUND else null,
                )
                is SourceResult.Failure -> ConnectionSetupUiState(problem = result.error.kind.connectionProblem())
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
        stopService(Intent(this, MonitoringService::class.java))
        app.hideCurrentNotification()
        app.alertEngine.clearNotifications()
        setupState = setupState.copy(startingMonitoring = false)
    }

    private fun renderSettingsChange() {
        if (controller.display.value.monitoringEnabled) app.showCurrentNotification()
        refreshSettings()
    }

    private val callbacks = GlanceCallbacks(
        onSignIn = { credentials ->
            stopMonitoring()
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
                        accountState = AccountUiState(AccountStatus.SIGNED_OUT, result.error.kind.accountProblem())
                    null -> accountState = AccountUiState(AccountStatus.SIGNED_OUT, AccountProblem.PROTOCOL)
                }
            }
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
            controller.settings.showOnLockScreen = it
            if (!it) app.alertEngine.clearNotifications()
            renderSettingsChange()
        },
        onIntervalChanged = { controller.settings.intervalMinutes = it; renderSettingsChange() },
        onEnhancedNotificationChanged = { controller.settings.enhancedNotification = it; renderSettingsChange() },
        onNumericStatusIconChanged = { controller.settings.numericStatusIcon = it; renderSettingsChange() },
        onOpenLiveUpdateSettings = {
            val liveSettings = LiveUpdateSupport.settingsIntent(this)
            try {
                startActivity(liveSettings)
            } catch (_: android.content.ActivityNotFoundException) {
                startActivity(LiveUpdateSupport.appSettingsIntent(this))
            }
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
            controller.clearLocalData()
            app.alertPreferences.clearSnapshot()
            app.alertEngine.clearNotifications()
            if (controller.display.value.monitoringEnabled) app.showCurrentNotification()
        },
        onSignOut = { signOut() },
    )

    private fun signOut() {
        stopMonitoring()
        controller.signOut()
        app.alertPreferences.clear()
        refreshSettings()
        setupPrefs.edit().clear().apply()
        accountState = AccountUiState(AccountStatus.SIGNED_OUT)
        setupState = setupState.copy(step = SetupStep.SIGN_IN)
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
