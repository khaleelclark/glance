package com.khaleel.glance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.GlucoseUnit
import java.time.Instant

@Composable
internal fun MainReadingScreen(
    displayState: DisplayState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    text: GlanceUiText,
    now: Instant,
    onOpenSettings: () -> Unit,
) {
    val reading = text.reading(displayState, settings.unit, now)
    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayState.person?.displayName ?: "Shared reading",
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reading == null) {
                    Text("Waiting for first reading", style = MaterialTheme.typography.headlineSmall)
                    Text("A value will appear after the first successful fetch.")
                } else {
                    Text(reading.freshness, style = MaterialTheme.typography.titleMedium)
                    Text(reading.value, style = MaterialTheme.typography.displayLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(reading.unit, style = MaterialTheme.typography.titleLarge)
                        if (reading.trendSymbol.isNotBlank()) {
                            Text(
                                text = reading.trendSymbol,
                                modifier = Modifier.semantics {
                                    contentDescription = "Trend ${reading.trendDescription}"
                                },
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        } else {
                            Text(reading.trendDescription, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(reading.measured, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        StatusMessage(text.monitoring(displayState))
        text.connection(displayState)?.let { StatusMessage(it, isError = true) }

        if (displayState.connection == ConnectionState.SIGN_IN_REQUIRED ||
            displayState.connection == ConnectionState.TERMS_REQUIRED ||
            displayState.connection == ConnectionState.CONNECTION_REMOVED ||
            displayState.connection == ConnectionState.PROTOCOL_ERROR
        ) {
            OutlinedButton(onClick = callbacks.onReconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Reconnect LibreLinkUp")
            }
        }
        if (displayState.connection == ConnectionState.LOCAL_ERROR) {
            OutlinedButton(onClick = callbacks.onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Review diagnostics")
            }
        }

        Button(
            onClick = if (displayState.monitoringEnabled && displayState.runtime == RuntimeState.RUNNING) {
                callbacks.onPauseMonitoring
            } else {
                callbacks.onResumeMonitoring
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (displayState.monitoringEnabled && displayState.runtime == RuntimeState.RUNNING) {
                "Pause monitoring"
            } else {
                "Resume monitoring"
            })
        }
        OutlinedButton(
            onClick = callbacks.onRefresh,
            modifier = Modifier.fillMaxWidth(),
            enabled = displayState.monitoringEnabled && displayState.runtime == RuntimeState.RUNNING &&
                displayState.connection != ConnectionState.SIGN_IN_REQUIRED &&
                displayState.connection != ConnectionState.TERMS_REQUIRED &&
                displayState.connection != ConnectionState.CONNECTION_REMOVED &&
                displayState.connection != ConnectionState.PROTOCOL_ERROR &&
                displayState.connection != ConnectionState.LOCAL_ERROR,
        ) {
            Text("Refresh now")
        }
        TextButton(onClick = callbacks.onRestoreNotification, modifier = Modifier.fillMaxWidth()) {
            Text("Show notification again")
        }
    }
}

@Composable
internal fun SettingsScreen(
    setupState: SetupUiState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    onBack: () -> Unit,
) {
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    ScreenColumn {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                "Settings",
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        SettingsSection("Display") {
            Text("Units", style = MaterialTheme.typography.titleMedium)
            UnitChoice(GlucoseUnit.MG_DL, settings.unit, callbacks.onUnitChanged)
            UnitChoice(GlucoseUnit.MMOL_L, settings.unit, callbacks.onUnitChanged)
            HorizontalDivider()
            SettingSwitch(
                title = "Lock-screen values",
                detail = "Show the person's name, glucose value, and reading time on the lock screen.",
                checked = settings.showOnLockScreen,
                onCheckedChange = callbacks.onLockScreenVisibilityChanged,
            )
            SettingSwitch(
                title = "Enhanced Live Update",
                detail = "Ask Android for an enhanced presentation. Unsupported or denied devices keep the standard notification.",
                checked = settings.enhancedNotification,
                onCheckedChange = callbacks.onEnhancedNotificationChanged,
            )
        }

        SettingsSection("Monitoring") {
            Text("Refresh cadence", style = MaterialTheme.typography.titleMedium)
            IntervalChoice(1, settings.intervalMinutes, callbacks.onIntervalChanged)
            IntervalChoice(5, settings.intervalMinutes, callbacks.onIntervalChanged)
            Text(
                if (settings.intervalMinutes == 1) {
                    "One minute gives faster updates and uses more network and battery."
                } else {
                    "Five minutes uses less network and may show readings as delayed between checks."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            SettingSwitch(
                title = "Resume after restart",
                detail = "Resume after the first unlock when Android allows it. A force-stopped app still needs to be opened.",
                checked = settings.resumeAfterBoot,
                onCheckedChange = callbacks.onResumeAfterBootChanged,
            )
            PermissionSettingsRow(setupState.notificationPermission, callbacks.onOpenNotificationSettings)
            OutlinedButton(onClick = callbacks.onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Battery settings · ${batteryStatusShort(setupState.batteryStatus)}")
            }
        }

        SettingsSection("Account") {
            OutlinedButton(onClick = callbacks.onChangePerson, modifier = Modifier.fillMaxWidth()) {
                Text("Change selected person")
            }
            OutlinedButton(onClick = callbacks.onReconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Reconnect LibreLinkUp")
            }
            OutlinedButton(onClick = callbacks.onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
                Text("Diagnostics")
            }
            OutlinedButton(onClick = { confirmAction = ConfirmAction.DELETE_DATA }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete local data")
            }
            TextButton(onClick = { confirmAction = ConfirmAction.SIGN_OUT }, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (action == ConfirmAction.SIGN_OUT) "Sign out?" else "Delete local data?") },
            text = {
                Text(
                    if (action == ConfirmAction.SIGN_OUT) {
                        "Monitoring will stop and saved account and reading data will be removed."
                    } else {
                        "Saved readings and local diagnostics will be removed. This cannot be undone."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAction = null
                        if (action == ConfirmAction.SIGN_OUT) callbacks.onSignOut() else callbacks.onDeleteLocalData()
                    },
                ) { Text(if (action == ConfirmAction.SIGN_OUT) "Sign out" else "Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            content()
        }
    }
}

@Composable
private fun UnitChoice(value: GlucoseUnit, selected: GlucoseUnit, onSelect: (GlucoseUnit) -> Unit) {
    val label = if (value == GlucoseUnit.MG_DL) "mg/dL" else "mmol/L"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Text(label)
    }
}

@Composable
private fun IntervalChoice(value: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(value) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Text(if (value == 1) "Every minute" else "Every 5 minutes")
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionSettingsRow(status: PermissionStatus, onOpen: () -> Unit) {
    val label = when (status) {
        PermissionStatus.UNKNOWN -> "not checked"
        PermissionStatus.GRANTED -> "allowed"
        PermissionStatus.DENIED -> "not allowed"
    }
    OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Text("Notification settings · $label")
    }
}

private fun batteryStatusShort(status: BatteryStatus) = when (status) {
    BatteryStatus.UNKNOWN -> "not checked"
    BatteryStatus.STANDARD -> "standard"
    BatteryStatus.UNRESTRICTED -> "unrestricted"
}

private enum class ConfirmAction { SIGN_OUT, DELETE_DATA }
