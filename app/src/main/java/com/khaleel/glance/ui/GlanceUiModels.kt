package com.khaleel.glance.ui

import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.GlucoseUnit

enum class AccountStatus { SIGNED_OUT, SIGNING_IN, SIGNED_IN }

enum class AccountProblem {
    INVALID_CREDENTIALS,
    NETWORK,
    TERMS_REQUIRED,
    PROTOCOL,
}

data class AccountUiState(
    val status: AccountStatus,
    val problem: AccountProblem? = null,
)

enum class ConnectionProblem { NONE_FOUND, NETWORK, REMOVED, PROTOCOL }

data class ConnectionSetupUiState(
    val connections: List<Connection> = emptyList(),
    val selectedConnectionId: String? = null,
    val loading: Boolean = false,
    val problem: ConnectionProblem? = null,
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
    val onPauseMonitoring: () -> Unit,
    val onResumeMonitoring: () -> Unit,
    val onRefresh: () -> Unit,
    val onRestoreNotification: () -> Unit,
    val onUnitChanged: (GlucoseUnit) -> Unit,
    val onLockScreenVisibilityChanged: (Boolean) -> Unit,
    val onIntervalChanged: (Int) -> Unit,
    val onEnhancedNotificationChanged: (Boolean) -> Unit,
    val onResumeAfterBootChanged: (Boolean) -> Unit,
    val onChangePerson: () -> Unit,
    val onReconnect: () -> Unit,
    val onOpenDiagnostics: () -> Unit,
    val onDeleteLocalData: () -> Unit,
    val onSignOut: () -> Unit,
)
