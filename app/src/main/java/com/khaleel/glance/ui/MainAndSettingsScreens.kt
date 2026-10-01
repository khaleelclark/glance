package com.khaleel.glance.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.ui.GlanceButton as Button
import com.khaleel.glance.ui.GlanceOutlinedButton as OutlinedButton
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

@Composable
internal fun MainReadingScreen(
    displayState: DisplayState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    text: GlanceUiText,
    now: Instant,
    zoneId: ZoneId,
    locale: Locale,
    onOpenSettings: () -> Unit,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedChartTime by rememberSaveable(displayState.person?.id) { mutableStateOf<Long?>(null) }
    if (landscape) {
        Row(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(
                Modifier.weight(0.44f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MainReadingSummary(displayState, settings, callbacks, text, now, onOpenSettings)
            }
            Column(
                Modifier.weight(0.56f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ReadingHistoryChart(
                    displayState.history,
                    displayState.person?.id,
                    settings.unit,
                    now,
                    zoneId,
                    locale,
                    selectedChartTime,
                    { selectedChartTime = it },
                    alerts = settings.alerts,
                )
            }
        }
    } else {
        ScreenColumn {
            MainReadingSummary(displayState, settings, callbacks, text, now, onOpenSettings)
            ReadingHistoryChart(
                displayState.history,
                displayState.person?.id,
                settings.unit,
                now,
                zoneId,
                locale,
                selectedChartTime,
                { selectedChartTime = it },
                alerts = settings.alerts,
            )
        }
    }
}

@Composable
private fun MainReadingSummary(
    displayState: DisplayState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    text: GlanceUiText,
    now: Instant,
    onOpenSettings: () -> Unit,
) {
    val reading = text.reading(displayState, settings.unit, now)
    val refreshEnabled = displayState.monitoringEnabled && displayState.runtime == RuntimeState.RUNNING &&
        displayState.connection !in setOf(
            ConnectionState.SIGN_IN_REQUIRED,
            ConnectionState.TERMS_REQUIRED,
            ConnectionState.CONNECTION_REMOVED,
            ConnectionState.PROTOCOL_ERROR,
            ConnectionState.LOCAL_ERROR,
            ConnectionState.FETCHING,
        )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "GLANCE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = displayState.person?.displayName ?: "Shared reading",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Settings")
            }
        }

        GlanceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            if (reading == null) {
                Text("Waiting for first reading", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Glance is waiting for LibreLinkUp.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    reading.freshness,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        reading.value,
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        reading.unit,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (reading.trendSymbol.isNotBlank()) {
                        Text(
                            text = reading.trendSymbol,
                            modifier = Modifier.alignByBaseline().semantics {
                                contentDescription = "Trend ${reading.trendDescription}"
                            },
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "Trend · ${reading.trendDescription}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    reading.measured,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        StatusMessage(text.monitoring(displayState))
        text.connection(displayState)?.let { StatusMessage(it, isError = true) }

        when (displayState.connection) {
            ConnectionState.SIGN_IN_REQUIRED,
            ConnectionState.TERMS_REQUIRED,
            ConnectionState.CONNECTION_REMOVED,
            ConnectionState.PROTOCOL_ERROR -> OutlinedButton(
                onClick = callbacks.onReconnect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reconnect LibreLinkUp")
            }
            ConnectionState.LOCAL_ERROR -> OutlinedButton(
                onClick = callbacks.onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Review diagnostics")
            }
            else -> Unit
        }

        if (displayState.runtime == RuntimeState.RUNNING) {
            Button(
                onClick = callbacks.onRefresh,
                modifier = Modifier.fillMaxWidth(),
                enabled = refreshEnabled,
            ) {
                Text(if (displayState.connection == ConnectionState.FETCHING) "Refreshing…" else "Refresh")
            }
        } else {
            Button(onClick = callbacks.onResumeMonitoring, modifier = Modifier.fillMaxWidth()) {
                Text(if (displayState.monitoringEnabled) "Try again" else "Start monitoring")
            }
        }
    }
}

@Composable
internal fun SettingsScreen(
    displayState: DisplayState,
    setupState: SetupUiState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.EVERYDAY) }
    var confirmAction by remember { mutableStateOf<ConfirmAction?>(null) }
    var notificationHelpVisible by rememberSaveable { mutableStateOf(false) }
    val everydayScroll = rememberScrollState()
    val alertsScroll = rememberScrollState()
    val moreScroll = rememberScrollState()
    val pageScroll = when (page) {
        SettingsPage.EVERYDAY -> everydayScroll
        SettingsPage.ALERTS -> alertsScroll
        SettingsPage.MORE -> moreScroll
    }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsToolbar(onBack)
            SettingsTabs(selected = page, onSelect = { page = it })
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(pageScroll)
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                SettingsPage.EVERYDAY -> EverydaySettings(
                    displayState = displayState,
                    setupState = setupState,
                    settings = settings,
                    callbacks = callbacks,
                    onShowNotificationHelp = { notificationHelpVisible = true },
                )
                SettingsPage.ALERTS -> AlertsSettingsPage(settings, callbacks)
                SettingsPage.MORE -> MoreSettings(
                    settings = settings,
                    callbacks = callbacks,
                    onConfirm = { confirmAction = it },
                )
            }
        }
    }

    if (notificationHelpVisible) {
        AlertDialog(
            onDismissRequest = { notificationHelpVisible = false },
            title = { Text("How notifications work") },
            text = {
                Text(
                    "Routine updates are silent. Lock-screen privacy also hides the number icon and Live Update value. " +
                        "Live Update is requested only on supported devices and always falls back to the standard notification. " +
                        "Keeping the notification visible can restore the standard notification after it is cleared while monitoring continues.",
                )
            },
            confirmButton = {
                TextButton(onClick = { notificationHelpVisible = false }) { Text("Done") }
            },
        )
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
                        if (action == ConfirmAction.SIGN_OUT) callbacks.onSignOut()
                        else callbacks.onDeleteLocalData()
                    },
                ) {
                    Text(if (action == ConfirmAction.SIGN_OUT) "Sign out" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsToolbar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
        Text(
            "Settings",
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun SettingsTabs(selected: SettingsPage, onSelect: (SettingsPage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsPage.entries.forEach { page ->
            val active = page == selected
            val shape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape,
                    )
                    .border(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape,
                    )
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(page) })
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(page.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EverydaySettings(
    displayState: DisplayState,
    setupState: SetupUiState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    onShowNotificationHelp: () -> Unit,
) {
    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Reading & refresh", "Set display units and check frequency.")
        Text("Units", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceOption("mg/dL", settings.unit == GlucoseUnit.MG_DL, Modifier.weight(1f)) {
                callbacks.onUnitChanged(GlucoseUnit.MG_DL)
            }
            ChoiceOption("mmol/L", settings.unit == GlucoseUnit.MMOL_L, Modifier.weight(1f)) {
                callbacks.onUnitChanged(GlucoseUnit.MMOL_L)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Refresh cadence", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceOption("1 minute", settings.intervalMinutes == 1, Modifier.weight(1f)) {
                callbacks.onIntervalChanged(1)
            }
            ChoiceOption("5 minutes", settings.intervalMinutes == 5, Modifier.weight(1f)) {
                callbacks.onIntervalChanged(5)
            }
        }
        Text(
            if (settings.intervalMinutes == 1) "Faster checks with more battery and network use."
            else "Lower traffic; readings may briefly appear delayed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Notification", "Choose its privacy and presentation.")
        SettingSwitch(
            title = "Lock-screen values",
            summary = "Show the name, reading and time while locked.",
            checked = settings.showOnLockScreen,
            onCheckedChange = callbacks.onLockScreenVisibilityChanged,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Notification style", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceOption("Graph", !settings.enhancedNotification, Modifier.weight(1f)) {
                callbacks.onEnhancedNotificationChanged(false)
            }
            ChoiceOption("Live Update", settings.enhancedNotification, Modifier.weight(1f)) {
                callbacks.onEnhancedNotificationChanged(true)
            }
        }
        Text(
            if (settings.enhancedNotification) {
                "Requests Android's compact Live Update; Graph is the fallback."
            } else {
                "Shows the regular notification with its small history graph."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.enhancedNotification) {
            Text(
                settings.liveUpdateStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = callbacks.onOpenLiveUpdateSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Live Update settings")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingSwitch(
            title = "Glucose number icon",
            summary = "Requires lock-screen values. Older readings use the app icon.",
            checked = settings.numericStatusIcon,
            onCheckedChange = callbacks.onNumericStatusIconChanged,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingSwitch(
            title = "Keep notification visible",
            summary = "Restores it after clearing while monitoring.",
            checked = settings.keepNotificationVisible,
            onCheckedChange = callbacks.onKeepNotificationVisibleChanged,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = callbacks.onRestoreNotification, modifier = Modifier.weight(1f),
                enabled = displayState.monitoringEnabled && setupState.notificationPermission == PermissionStatus.GRANTED) {
                Text("Show notification")
            }
            TextButton(onClick = onShowNotificationHelp, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("How it works")
            }
        }
        PermissionSettingsRow(setupState.notificationPermission, callbacks.onOpenNotificationSettings)
    }

    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Background monitoring", "Control restart and battery behavior.")
        SettingSwitch(
            title = "Resume after restart",
            summary = "Restarts after first unlock when allowed; force-stop requires reopening.",
            checked = settings.resumeAfterBoot,
            onCheckedChange = callbacks.onResumeAfterBootChanged,
        )
        OutlinedButton(onClick = callbacks.onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
            Text("Battery settings · ${batteryStatusShort(setupState.batteryStatus)}")
        }
        if (displayState.monitoringEnabled) {
            TextButton(onClick = callbacks.onStopMonitoring, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Stop monitoring")
            }
        }
    }
}

@Composable
private fun AlertsSettingsPage(settings: UserSettingsUi, callbacks: GlanceCallbacks) {
    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Glucose alerts", "Alerts are separate from silent reading updates.")
        AlertSettings(
            settings.alerts,
            settings.unit,
            callbacks.onAlertRuleChanged,
            callbacks.onAlertSoundChanged,
            callbacks.onOpenNotificationSettings,
        )
    }
}

@Composable
private fun MoreSettings(
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    onConfirm: (ConfirmAction) -> Unit,
) {
    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Appearance", "Use one calm accent throughout Glance.")
        AccentChoice(settings.accentHex, callbacks.onAccentHexChanged)
    }

    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Widgets", "Phone and car surfaces have their own privacy choice.")
        SettingSwitch(
            title = "Show widget values",
            summary = "Shows readings on widgets, including while the phone is locked.",
            checked = settings.widgetValuesVisible,
            onCheckedChange = callbacks.onWidgetValuesChanged,
        )
        Text(
            "Add Glance glucose from the phone's widget picker. Android Auto availability depends on its version and rollout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Galaxy Watch", "The phone remains the only device that signs in and fetches readings.")
        SettingSwitch(
            title = "Share watch readings",
            summary = "Shows readings in the app and complication; anyone nearby may see them.",
            checked = settings.watchValuesVisible,
            onCheckedChange = callbacks.onWatchValuesChanged,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingSwitch(
            title = "Watch notification",
            summary = "Allows one silent notification when enabled on the watch too.",
            checked = settings.watchNotificationsEnabled,
            onCheckedChange = callbacks.onWatchNotificationsChanged,
        )
        Text(
            "Readings hide after 5 minutes without phone sync. Add Glance glucose to a text complication slot and tap it to open the watch app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(settings.watchSyncStatus, style = MaterialTheme.typography.bodyMedium)
    }

    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Account & support")
        OutlinedButton(onClick = callbacks.onChangePerson, modifier = Modifier.fillMaxWidth()) {
            Text("Change person")
        }
        OutlinedButton(onClick = callbacks.onReconnect, modifier = Modifier.fillMaxWidth()) {
            Text("Reconnect LibreLinkUp")
        }
        OutlinedButton(onClick = callbacks.onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text("Diagnostics")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        OutlinedButton(onClick = { onConfirm(ConfirmAction.DELETE_DATA) }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete local data")
        }
        TextButton(
            onClick = { onConfirm(ConfirmAction.SIGN_OUT) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Sign out", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AccentChoice(selectedHex: String, onSelect: (String) -> Unit) {
    var draft by rememberSaveable(selectedHex) { mutableStateOf(selectedHex) }
    var customVisible by rememberSaveable { mutableStateOf(false) }
    val normalized = ThemeAccent.normalize(draft)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ThemeAccent.presets.forEach { preset ->
            FilterChip(
                selected = selectedHex.equals(preset.hex, ignoreCase = true),
                onClick = { onSelect(preset.hex) },
                label = { Text(preset.name) },
                leadingIcon = { Text("●", color = ThemeAccent.color(preset.hex)) },
            )
        }
    }
    OutlinedButton(onClick = { customVisible = !customVisible }, modifier = Modifier.fillMaxWidth()) {
        Text(if (customVisible) "Close custom color" else "Custom color")
    }
    if (customVisible) {
        ColorWheelPicker(normalized ?: selectedHex) { draft = it }
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 7) draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Hex color") },
            placeholder = { Text("#087E82") },
            singleLine = true,
            isError = draft.isNotBlank() && normalized == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            supportingText = {
                Text(
                    if (draft.isNotBlank() && normalized == null) "Enter six hex digits, such as #087E82."
                    else "Very light or dark colors are adjusted to keep text readable.",
                )
            },
        )
        Button(
            onClick = { normalized?.let(onSelect) },
            modifier = Modifier.fillMaxWidth(),
            enabled = normalized != null && !normalized.equals(selectedHex, ignoreCase = true),
        ) {
            Text("Apply color")
        }
    }
}

@Composable
private fun ChoiceOption(label: String, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
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

private enum class SettingsPage(val label: String) {
    EVERYDAY("Everyday"),
    ALERTS("Alerts"),
    MORE("More"),
}

private enum class ConfirmAction { SIGN_OUT, DELETE_DATA }
