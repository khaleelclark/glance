package com.khaleel.glance.ui

import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.AlertRule
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.SourceResponseInfo

enum class AccountStatus { SIGNED_OUT, SIGNING_IN, SIGNED_IN }

enum class AccountProblem {
    INVALID_CREDENTIALS,
    NETWORK,
    TERMS_REQUIRED,
    STORAGE,
    PROTOCOL,
}

data class AccountUiState(
    val status: AccountStatus,
    val problem: AccountProblem? = null,
    val responseInfo: SourceResponseInfo? = null,
)

enum class ConnectionProblem { NONE_FOUND, NETWORK, REMOVED, STORAGE, PROTOCOL }

data class ConnectionSetupUiState(
    val connections: List<Connection> = emptyList(),
    val selectedConnectionId: String? = null,
    val loading: Boolean = false,
    val problem: ConnectionProblem? = null,
    val responseInfo: SourceResponseInfo? = null,
)

enum class SetupStep { SIGN_IN, CONNECTION, NOTIFICATION_PRIVACY, COMPLETE }

enum class PermissionStatus { UNKNOWN, GRANTED, DENIED }

enum class BatteryStatus { UNKNOWN, STANDARD, UNRESTRICTED }

data class SetupUiState(
    val step: SetupStep,
    val notificationPermission: PermissionStatus = PermissionStatus.UNKNOWN,
    val batteryStatus: BatteryStatus = BatteryStatus.UNKNOWN,
    val startingMonitoring: Boolean = false,
    val startFailed: Boolean = false,
)

/** A UI snapshot of persisted settings. The persistence implementation remains owned by the caller. */
data class UserSettingsUi(
    val unit: GlucoseUnit,
    val showOnLockScreen: Boolean,
    val intervalMinutes: Int,
    val enhancedNotification: Boolean,
    val resumeAfterBoot: Boolean,
    val accentHex: String = ThemeAccent.DEFAULT,
    val alerts: AlertConfig = AlertConfig.defaults(),
    val numericStatusIcon: Boolean = true,
    val liveUpdateStatus: String = "Android decides whether to promote the monitoring notification.",
    val widgetValuesVisible: Boolean = false,
    val keepNotificationVisible: Boolean = true,
    val watchValuesVisible: Boolean = false,
    val watchNotificationsEnabled: Boolean = false,
    val watchSyncStatus: String = "Waiting for watch sync",
    val nickname: String = "",
    val sourcePersonName: String? = null,
) {
    init {
        require(intervalMinutes == 1 || intervalMinutes == 5)
    }
}

/** All effects leave Compose through this bundle so the UI does not own services or persistence. */
data class GlanceCallbacks(
    val onSignIn: (Credentials) -> Unit,
    val onSelectConnection: (Connection) -> Unit,
    val onConfirmConnection: () -> Unit,
    val onRetryConnections: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
    val onOpenNotificationSettings: () -> Unit,
    val onOpenBatterySettings: () -> Unit,
    val onStartMonitoring: () -> Unit,
    val onStopMonitoring: () -> Unit,
    val onResumeMonitoring: () -> Unit,
    val onRefresh: () -> Unit,
    val onRestoreNotification: () -> Unit,
    val onUnitChanged: (GlucoseUnit) -> Unit,
    val onLockScreenVisibilityChanged: (Boolean) -> Unit,
    val onIntervalChanged: (Int) -> Unit,
    val onEnhancedNotificationChanged: (Boolean) -> Unit,
    val onNumericStatusIconChanged: (Boolean) -> Unit,
    val onOpenLiveUpdateSettings: () -> Unit,
    val onAccentHexChanged: (String) -> Unit,
    val onAlertRuleChanged: (AlertKind, AlertRule) -> Unit,
    val onAlertSoundChanged: (AlertKind) -> Unit,
    val onResumeAfterBootChanged: (Boolean) -> Unit,
    val onChangePerson: () -> Unit,
    val onReconnect: () -> Unit,
    val onOpenDiagnostics: () -> Unit,
    val onDeleteLocalData: () -> Unit,
    val onSignOut: () -> Unit,
    val onWidgetValuesChanged: (Boolean) -> Unit = {},
    val onKeepNotificationVisibleChanged: (Boolean) -> Unit = {},
    val onWatchValuesChanged: (Boolean) -> Unit = {},
    val onWatchNotificationsChanged: (Boolean) -> Unit = {},
    val onClearAlertLog: () -> Unit = {},
    val onNicknameChanged: (String) -> Unit = {},
)
