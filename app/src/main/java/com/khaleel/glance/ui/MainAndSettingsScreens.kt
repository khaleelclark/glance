package com.khaleel.glance.ui

import android.content.res.Configuration
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import com.khaleel.glance.ui.GlanceButton as Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.khaleel.glance.ui.GlanceOutlinedButton as OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.GlucoseUnit
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
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                MainReadingSummary(displayState, settings, callbacks, text, now, onOpenSettings)
            }
            Column(Modifier.weight(0.58f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                ReadingHistoryChart(
                    displayState.history, displayState.person?.id, settings.unit, now, zoneId, locale,
                    selectedChartTime, { selectedChartTime = it }, alerts = settings.alerts,
                )
            }
        }
    } else {
        ScreenColumn {
            MainReadingSummary(displayState, settings, callbacks, text, now, onOpenSettings)
            ReadingHistoryChart(
                displayState.history, displayState.person?.id, settings.unit, now, zoneId, locale,
                selectedChartTime, { selectedChartTime = it }, alerts = settings.alerts,
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }

        Column(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reading == null) {
                    Text("Waiting for first reading", style = MaterialTheme.typography.headlineSmall)
                    Text("Waiting for LibreLinkUp.")
                } else {
                    Text(reading.freshness, style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(reading.value, style = MaterialTheme.typography.displayLarge, modifier = Modifier.alignByBaseline())
                        Text(reading.unit, style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
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
                    Text(
                        "Trend · ${reading.trendDescription}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

        if (displayState.runtime != RuntimeState.RUNNING) {
            Button(onClick = callbacks.onResumeMonitoring, modifier = Modifier.fillMaxWidth()) {
                Text(if (displayState.monitoringEnabled) "Try again" else "Start monitoring")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = callbacks.onRefresh,
                modifier = Modifier.weight(1f),
                enabled = displayState.monitoringEnabled && displayState.runtime == RuntimeState.RUNNING &&
                    displayState.connection != ConnectionState.SIGN_IN_REQUIRED &&
                    displayState.connection != ConnectionState.TERMS_REQUIRED &&
                    displayState.connection != ConnectionState.CONNECTION_REMOVED &&
                    displayState.connection != ConnectionState.PROTOCOL_ERROR &&
                    displayState.connection != ConnectionState.LOCAL_ERROR &&
                    displayState.connection != ConnectionState.FETCHING,
            ) {
                Text(if (displayState.connection == ConnectionState.FETCHING) "Refreshing…" else "Refresh")
            }
            TextButton(onClick = callbacks.onRestoreNotification, modifier = Modifier.weight(1f)) {
                Text("Restore card")
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceOption("mg/dL", settings.unit == GlucoseUnit.MG_DL, Modifier.weight(1f)) { callbacks.onUnitChanged(GlucoseUnit.MG_DL) }
                ChoiceOption("mmol/L", settings.unit == GlucoseUnit.MMOL_L, Modifier.weight(1f)) { callbacks.onUnitChanged(GlucoseUnit.MMOL_L) }
            }
            HorizontalDivider()
            AccentChoice(settings.accentHex, callbacks.onAccentHexChanged)
        }

        SettingsSection("Notifications") {
            SettingSwitch(
                title = "Lock-screen values",
                summary = "Anyone can see the name and reading while locked.",
                detail = "Show the person's name, glucose value, and reading time on the lock screen.",
                checked = settings.showOnLockScreen,
                onCheckedChange = callbacks.onLockScreenVisibilityChanged,
            )
            SettingSwitch(
                title = "Status chip Live Update",
                summary = "Android's plain card. Turn off for the graph card.",
                detail = "Requests a status chip on Android 16 or newer. Samsung decides whether to show it in the Now bar. Turn off for the arrow, reading and unit card with a small history graph. Live Update uses Android's layout.",
                checked = settings.enhancedNotification,
                onCheckedChange = callbacks.onEnhancedNotificationChanged,
            )
            HelpDetails("Live Update status", settings.liveUpdateStatus +
                "\nSamsung: Settings → Lock screen and AOD → Now bar → Live notifications.")
            TextButton(onClick = callbacks.onOpenLiveUpdateSettings) { Text("Live Update settings") }
            SettingSwitch(
                title = "Glucose number icon",
                summary = "Show fresh values in the status bar; requires lock-screen values.",
                detail = "Show the current reading as the regular notification icon. Older readings use the Glance icon. Hidden lock-screen values also hide the number icon and Live Update chip value, including while unlocked. Live Updates use the Glance icon beside the chip value.",
                checked = settings.numericStatusIcon,
                onCheckedChange = callbacks.onNumericStatusIconChanged,
            )
            SettingSwitch(
                title = "Keep notification",
                summary = "Restore after clearing while monitoring.",
                detail = "Restore the regular glucose card after clearing or swiping while monitoring is active. A dismissed Live Update returns as the regular card. Stop monitoring to remove it. Android can still remove the card briefly or block notifications.",
                checked = settings.keepNotificationVisible,
                onCheckedChange = callbacks.onKeepNotificationVisibleChanged,
            )
        }

        SettingsSection("Widgets") {
            SettingSwitch(
                title = "Widget values",
                summary = "Readings are visible on phone/car widgets, even while locked.",
                detail = "Show readings and history on phone and car widgets, including while the phone is locked. Anyone who can see those screens can see the readings. This is separate from notification privacy.",
                checked = settings.widgetValuesVisible,
                onCheckedChange = callbacks.onWidgetValuesChanged,
            )
            HelpDetails("Widget setup", "Add Glance glucose from your phone's widget picker. For the car, select it in Android Auto's widget settings if available. Car widgets depend on your Android Auto version and rollout.")
        }

        SettingsSection("Galaxy Watch") {
            SettingSwitch(
                title = "Watch readings",
                summary = "Anyone looking at the watch can see the reading.",
                detail = "Share the current reading with Glance on your Wear OS watch, including its watch-face complication. Anyone who can see the watch can see the reading. Separate from phone lock-screen privacy.",
                checked = settings.watchValuesVisible,
                onCheckedChange = callbacks.onWatchValuesChanged,
            )
            SettingSwitch(
                title = "Watch notification",
                summary = "One silent card on the watch; enable there too.",
                detail = "Show one silent glucose card on the watch while sharing and monitoring. Also enable notifications in the Glance watch app. Existing glucose alerts keep their Android/Galaxy Wearable settings.",
                checked = settings.watchNotificationsEnabled,
                onCheckedChange = callbacks.onWatchNotificationsChanged,
            )
            Text("Readings hide after 5 minutes without phone sync.", style = MaterialTheme.typography.bodyMedium)
            HelpDetails("Watch setup", "Install Glance on your watch, then choose Glance glucose in a text complication slot. Tap it to open the watch app. The phone must keep monitoring; the watch does not sign in or fetch separately. Turning sharing off clears connected watches; a disconnected watch may retain its last reading for up to five minutes.")
            Text(settings.watchSyncStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection("Glucose alerts") {
            AlertSettings(
                settings.alerts,
                settings.unit,
                callbacks.onAlertRuleChanged,
                callbacks.onAlertSoundChanged,
                callbacks.onOpenNotificationSettings,
            )
        }

        SettingsSection("Monitoring") {
            Text("Refresh cadence", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceOption("1 minute", settings.intervalMinutes == 1, Modifier.weight(1f)) { callbacks.onIntervalChanged(1) }
                ChoiceOption("5 minutes", settings.intervalMinutes == 5, Modifier.weight(1f)) { callbacks.onIntervalChanged(5) }
            }
            Text(
                if (settings.intervalMinutes == 1) {
                    "Faster updates · more battery use."
                } else {
                    "Less battery use · readings may show as delayed."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            SettingSwitch(
                title = "Resume after restart",
                summary = "Resume after unlock. Reopen after a force-stop.",
                detail = "Resume after the first unlock when Android allows it. A force-stopped app still needs to be opened.",
                checked = settings.resumeAfterBoot,
                onCheckedChange = callbacks.onResumeAfterBootChanged,
            )
            PermissionSettingsRow(setupState.notificationPermission, callbacks.onOpenNotificationSettings)
            OutlinedButton(onClick = callbacks.onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Battery settings · ${batteryStatusShort(setupState.batteryStatus)}")
            }
            if (displayState.monitoringEnabled) {
                TextButton(onClick = callbacks.onStopMonitoring, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop monitoring")
                }
            }
        }

        SettingsSection("Account") {
            OutlinedButton(onClick = callbacks.onChangePerson, modifier = Modifier.fillMaxWidth()) {
                Text("Change person")
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
private fun AccentChoice(selectedHex: String, onSelect: (String) -> Unit) {
    var draft by rememberSaveable(selectedHex) { mutableStateOf(selectedHex) }
    val normalized = ThemeAccent.normalize(draft)
    var colorExpanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { colorExpanded = !colorExpanded }, modifier = Modifier.fillMaxWidth().semantics {
        stateDescription = if (colorExpanded) "Expanded" else "Collapsed"
    }) { Text(if (colorExpanded) "App color −" else "App color +") }
    if (colorExpanded) {
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
        ColorWheelPicker(normalized ?: selectedHex) { draft = it }
        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= 7) draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom hex color") },
            placeholder = { Text("#087E82") },
            singleLine = true,
            isError = draft.isNotBlank() && normalized == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            supportingText = {
                Text(if (draft.isNotBlank() && normalized == null) "Enter six hex digits, such as #087E82." else "Drag the wheel or enter a hex color, then apply.")
            },
        )
        Button(
            onClick = { normalized?.let(onSelect) },
            enabled = normalized != null && normalized != selectedHex,
        ) { Text("Apply color") }
        Text(
            "Very light or dark colors are adjusted to keep text and the graph readable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(title == "Display") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text(if (expanded) "−" else "+", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) content()
    }
}

@Composable
private fun ChoiceOption(label: String, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(modifier.heightIn(min = 48.dp)
        .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background, shape)
        .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
        .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
        HelpDetails("About $title", detail)
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
