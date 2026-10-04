package com.khaleel.glance.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khaleel.glance.alerts.AlertHistoryEntry
import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseFormat.label
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.PersonDisplayState
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.Trend
import com.khaleel.glance.core.forPerson
import com.khaleel.glance.ui.GlanceButton as Button
import com.khaleel.glance.ui.GlanceOutlinedButton as OutlinedButton
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal enum class AppDestination(val label: String, val glyph: GlanceGlyph) {
    NOW("Now", GlanceGlyph.NOW), LOGBOOK("Logbook", GlanceGlyph.LOGBOOK),
    PEOPLE("People", GlanceGlyph.PEOPLE), SETTINGS("Settings", GlanceGlyph.SETTINGS),
}
private enum class SettingsSheet { UNIT, CADENCE, PRESENTATION, ALERT_PERSON, ACCENT }
private enum class ConfirmAction { SIGN_OUT, DELETE_DATA }

@Composable
internal fun CompletedGlanceApp(
    displayState: DisplayState,
    setupState: SetupUiState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    alertHistory: List<AlertHistoryEntry>,
    text: GlanceUiText,
    now: Instant,
    zoneId: ZoneId,
    locale: Locale,
) {
    val roster = remember(displayState) { rosterStates(displayState) }
    var destinationName by rememberSaveable { mutableStateOf(AppDestination.NOW.name) }
    var viewedPersonId by rememberSaveable { mutableStateOf(displayState.person?.id) }
    val destinations = AppDestination.entries.filter { it != AppDestination.PEOPLE || roster.size > 1 }
    val destination = AppDestination.entries.firstOrNull { it.name == destinationName }
        ?.takeIf { it in destinations } ?: AppDestination.NOW
    LaunchedEffect(roster.map { it.person.id }, displayState.person?.id) {
        if (viewedPersonId !in roster.map { it.person.id }) viewedPersonId = displayState.person?.id ?: roster.firstOrNull()?.person?.id
        if (destination == AppDestination.PEOPLE && roster.size <= 1) destinationName = AppDestination.NOW.name
    }
    BackHandler(enabled = destination != AppDestination.NOW) { destinationName = AppDestination.NOW.name }
    val viewedState = viewedPersonId?.let(displayState::forPerson) ?: displayState
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { GlanceBottomBar(destinations, destination) { destinationName = it.name } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
                AppDestination.NOW -> MainReadingScreen(viewedState, displayState.person?.id, settings, callbacks, text, now, zoneId, locale)
                AppDestination.LOGBOOK -> LogbookScreen(alertHistory, settings, now, zoneId, locale, callbacks.onClearAlertLog)
                AppDestination.PEOPLE -> PeopleScreen(displayState, roster, settings, text, now, callbacks.onRefreshPeople,
                    onView = {
                        viewedPersonId = it.id
                        callbacks.onViewedPersonChanged(it.id)
                        destinationName = AppDestination.NOW.name
                    },
                    onPin = callbacks.onPinPerson)
                AppDestination.SETTINGS -> SettingsScreen(displayState, setupState, settings, callbacks, roster.map { it.person })
            }
        }
    }
}

@Composable
private fun GlanceBottomBar(items: List<AppDestination>, selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
        modifier = Modifier.navigationBarsPadding()) {
        items.forEach { item ->
            NavigationBarItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { GlanceGlyphIcon(item.glyph, color = if (item == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                label = { Text(item.label) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
internal fun MainReadingScreen(
    state: DisplayState,
    primaryPersonId: String?,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    text: GlanceUiText,
    now: Instant,
    zoneId: ZoneId,
    locale: Locale,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var selectedTime by rememberSaveable(state.person?.id) { mutableStateOf<Long?>(null) }
    val summary: @Composable ColumnScope.() -> Unit = {
        ReadingHeader(state, primaryPersonId, callbacks)
        ReadingHero(state, settings, text, now, locale)
        ReadingStatus(state, text, callbacks)
    }
    val chart: @Composable ColumnScope.() -> Unit = {
        ReadingHistoryChart(state.history, state.person?.id, settings.unit, now, zoneId, locale,
            selectedTime, { selectedTime = it }, settings.alerts)
    }
    if (landscape) {
        Row(Modifier.fillMaxSize().statusBarsPadding().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(.43f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = summary)
            Column(Modifier.weight(.57f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = chart)
        }
    } else {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) { summary(); chart() }
    }
}

@Composable
private fun ReadingHeader(state: DisplayState, primaryPersonId: String?, callbacks: GlanceCallbacks) {
    val refreshEnabled = state.monitoringEnabled && state.runtime == RuntimeState.RUNNING && state.connection !in nonRefreshableConnections
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlanceMark()
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text("GLANCE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(state.person?.displayName ?: "Shared reading", Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.person?.id != null && state.person.id == primaryPersonId) Badge("PINNED")
            }
        }
        IconButton(callbacks.onRefresh, enabled = refreshEnabled,
            modifier = Modifier.semantics { contentDescription = "Refresh reading" }) {
            GlanceGlyphIcon(GlanceGlyph.REFRESH, color = if (refreshEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = if (state.runtime == RuntimeState.RUNNING) callbacks.onStopMonitoring else callbacks.onResumeMonitoring,
            modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.runtime == RuntimeState.RUNNING) "Pause" else "Resume") }
    }
}

@Composable
private fun ReadingHero(state: DisplayState, settings: UserSettingsUi, text: GlanceUiText, now: Instant, locale: Locale) {
    val reading = text.reading(state, settings.unit, now)
    val delta = readingDeltaText(state.reading, state.history, settings.unit, locale)
    Surface(Modifier.fillMaxWidth().animateContentSize(), GlanceShapes.extraLarge,
        MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reading?.freshness ?: "Waiting for first reading", style = MaterialTheme.typography.labelLarge,
                color = if (state.freshness == ReadingFreshness.FRESH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (reading == null) {
                Text("No reading yet", style = MaterialTheme.typography.headlineLarge)
                Text("Glance is waiting for LibreLinkUp.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(reading.value, style = MaterialTheme.typography.displayLarge, modifier = Modifier.alignByBaseline())
                    Text(reading.unit, style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
                    if (reading.trendSymbol.isNotBlank()) Text(reading.trendSymbol,
                        style = MaterialTheme.typography.displayMedium.copy(color = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.alignByBaseline().semantics { contentDescription = "Trend ${reading.trendDescription}" })
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(reading.trendDescription, style = MaterialTheme.typography.titleMedium)
                    delta?.let { Text("· $it", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(reading.measured, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReadingStatus(state: DisplayState, text: GlanceUiText, callbacks: GlanceCallbacks) {
    val connection = text.connection(state)
    Row(Modifier.fillMaxWidth().clip(GlanceShapes.medium).background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(Modifier.size(9.dp), CircleShape, color = when {
            state.runtime != RuntimeState.RUNNING -> MaterialTheme.colorScheme.outline
            connection != null -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.primary
        }) {}
        Column(Modifier.weight(1f)) {
            Text(text.monitoring(state), style = MaterialTheme.typography.labelLarge)
            AnimatedVisibility(connection != null) { connection?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        if (state.connection in reconnectConnections) TextButton(callbacks.onReconnect) { Text("Reconnect") }
        if (state.connection == ConnectionState.LOCAL_ERROR) TextButton(callbacks.onOpenDiagnostics) { Text("Details") }
    }
}

@Composable
private fun PeopleScreen(
    display: DisplayState, roster: List<PersonDisplayState>, settings: UserSettingsUi, text: GlanceUiText, now: Instant,
    onRefresh: () -> Unit, onView: (Connection) -> Unit, onPin: (Connection) -> Unit,
) {
    ScreenList {
        ScreenHeader("People", "Everyone Glance is monitoring") {
            IconButton(onRefresh, modifier = Modifier.semantics { contentDescription = "Refresh all people" }) {
                GlanceGlyphIcon(GlanceGlyph.REFRESH, color = MaterialTheme.colorScheme.primary)
            }
        }
        roster.forEach { item ->
            val projected = display.forPerson(item.person.id) ?: display.copy(person = item.person, reading = item.reading,
                freshness = item.freshness, connection = item.connection, history = item.history)
            val reading = text.reading(projected, settings.unit, now)
            val primary = display.person?.id == item.person.id
            Surface(Modifier.fillMaxWidth().clickable { onView(item.person) }, GlanceShapes.large,
                MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.person.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(personStatus(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (primary) Badge("PRIMARY")
                    }
                    if (reading == null) Text("Waiting for first reading", color = MaterialTheme.colorScheme.onSurfaceVariant) else {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(reading.value, style = MaterialTheme.typography.headlineLarge)
                            Text(reading.unit, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 3.dp))
                            if (reading.trendSymbol.isNotBlank()) Text(reading.trendSymbol, style = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.primary))
                        }
                        Text(reading.measured, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (!primary) TextButton({ onPin(item.person) }) { Text("Pin to notification") }
                        TextButton({ onView(item.person) }) { Text("View") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogbookScreen(
    history: List<AlertHistoryEntry>, settings: UserSettingsUi, now: Instant, zoneId: ZoneId, locale: Locale, onClear: () -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val people = remember(history) { history.map { it.person }.distinctBy { it.id } }
    val filtered = history.filter { filter == null || it.person.id == filter }.sortedByDescending { it.firedAt }
    val groups = groupAlertHistory(filtered, zoneId)
    val time = remember(zoneId, locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zoneId) }
    ScreenList {
        ScreenHeader("Logbook", "Glucose alerts that fired on this phone") {
            if (history.isNotEmpty()) TextButton({ confirmClear = true }) { Text("Clear") }
        }
        if (people.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == null, { filter = null }, label = { Text("All people") })
            people.forEach { person -> FilterChip(filter == person.id, { filter = person.id }, label = { Text(person.displayName) }) }
        }
        if (filtered.isEmpty()) GlanceCard(Modifier.fillMaxWidth()) {
            Text(if (history.isEmpty()) "No alerts yet" else "No alerts for this person", style = MaterialTheme.typography.titleLarge)
            Text("When a glucose alert fires, its type and source reading will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        groups.forEach { (day, entries) ->
            Text(dayLabel(day, now, zoneId, locale), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(Modifier.fillMaxWidth(), GlanceShapes.large, MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column {
                    entries.forEachIndexed { index, entry ->
                        if (index > 0) GroupDivider()
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row { Text(entry.kind.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Text(time.format(entry.firedAt), color = MaterialTheme.colorScheme.primary) }
                            Text(entry.person.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(GlucoseFormat.withUnit(entry.reading, settings.unit, locale) + trendSymbol(entry.reading.trend).let { if (it.isBlank()) "" else "  $it" },
                                style = MaterialTheme.typography.headlineSmall)
                            Text("Source reading ${time.format(entry.reading.measuredAt)} · Alert fired ${time.format(entry.firedAt)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, title = { Text("Clear alert log?") },
        text = { Text("This removes alert history from this phone. It does not change alert rules or account data.") },
        confirmButton = { TextButton({ confirmClear = false; onClear() }) { Text("Clear log") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    display: DisplayState, setup: SetupUiState, settings: UserSettingsUi, callbacks: GlanceCallbacks, roster: List<Connection>,
) {
    var sheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    var help by rememberSaveable { mutableStateOf(false) }
    ScreenList {
        ScreenHeader("Settings", "Glance, arranged around daily use")
        SettingsGroup("Reading") {
            ValueSettingRow("Units", settings.unit.label) { sheet = SettingsSheet.UNIT }; GroupDivider()
            ValueSettingRow("Refresh cadence", if (settings.intervalMinutes == 1) "Every minute" else "Every 5 minutes") { sheet = SettingsSheet.CADENCE }
        }
        SettingsGroup("Notification") {
            SwitchSettingRow("Lock-screen values", "Show name, reading and time while locked.", settings.showOnLockScreen, callbacks.onLockScreenVisibilityChanged); GroupDivider()
            ValueSettingRow("Presentation", if (settings.enhancedNotification) "Live Update" else "Graph") { sheet = SettingsSheet.PRESENTATION }; GroupDivider()
            if (settings.enhancedNotification) {
                Text(settings.liveUpdateStatus, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionSettingRow("Live Update system settings", onClick = callbacks.onOpenLiveUpdateSettings)
                GroupDivider()
            }
            SwitchSettingRow("Glucose number icon", "Fresh values only, when privacy allows.", settings.numericStatusIcon, callbacks.onNumericStatusIconChanged); GroupDivider()
            SwitchSettingRow("Keep notification visible", "Restore it after clearing while monitoring.", settings.keepNotificationVisible, callbacks.onKeepNotificationVisibleChanged)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(callbacks.onRestoreNotification, Modifier.weight(1f),
                    display.monitoringEnabled && setup.notificationPermission == PermissionStatus.GRANTED) { Text("Show notification") }
                TextButton({ help = true }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("How it works") }
            }
            ActionSettingRow("Notification settings · ${permissionStatusShort(setup.notificationPermission)}", onClick = callbacks.onOpenNotificationSettings)
        }
        SettingsGroup("Monitoring") {
            SwitchSettingRow("Resume after restart", "Restarts after first unlock when Android allows.", settings.resumeAfterBoot, callbacks.onResumeAfterBootChanged); GroupDivider()
            ActionSettingRow("Battery settings · ${batteryStatusShort(setup.batteryStatus)}", onClick = callbacks.onOpenBatterySettings)
            if (display.monitoringEnabled) { GroupDivider(); TextButton(callbacks.onStopMonitoring, Modifier.fillMaxWidth()) { Text("Stop monitoring") } }
        }
        SettingsGroup("Alerts") {
            if (roster.size > 1) { ValueSettingRow("Alert rules for", settings.alertPersonName ?: "Choose a person") { sheet = SettingsSheet.ALERT_PERSON }; GroupDivider() }
            AlertSettings(settings.alerts, settings.unit, callbacks.onAlertRuleChanged, callbacks.onAlertSoundChanged,
                callbacks.onOpenNotificationSettings, settings.alertPersonName)
        }
        SettingsGroup("Appearance & devices") {
            ValueSettingRow("App color", accentName(settings.accentHex)) { sheet = SettingsSheet.ACCENT }; GroupDivider()
            SwitchSettingRow("Show widget values", "May be visible while the phone is locked.", settings.widgetValuesVisible, callbacks.onWidgetValuesChanged); GroupDivider()
            SwitchSettingRow("Share watch readings", "Shows values in the watch app and complication.", settings.watchValuesVisible, callbacks.onWatchValuesChanged); GroupDivider()
            SwitchSettingRow("Watch notification", "Allows one silent native watch notification.", settings.watchNotificationsEnabled, callbacks.onWatchNotificationsChanged)
            Text(settings.watchSyncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsGroup("Account & support") {
            ActionSettingRow("Change person", onClick = callbacks.onChangePerson); GroupDivider()
            ActionSettingRow("Reconnect LibreLinkUp", onClick = callbacks.onReconnect); GroupDivider()
            ActionSettingRow("Diagnostics", onClick = callbacks.onOpenDiagnostics); GroupDivider()
            ActionSettingRow("Delete local data") { confirm = ConfirmAction.DELETE_DATA }; GroupDivider()
            ActionSettingRow("Sign out", true) { confirm = ConfirmAction.SIGN_OUT }
        }
    }
    sheet?.let { active ->
        ModalBottomSheet({ sheet = null }, sheetState = rememberModalBottomSheetState(true), containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (active) {
                    SettingsSheet.UNIT -> SheetChoices("Display units", listOf("mg/dL" to (settings.unit == GlucoseUnit.MG_DL), "mmol/L" to (settings.unit == GlucoseUnit.MMOL_L))) {
                        callbacks.onUnitChanged(if (it == "mg/dL") GlucoseUnit.MG_DL else GlucoseUnit.MMOL_L); sheet = null }
                    SettingsSheet.CADENCE -> SheetChoices("Refresh cadence", listOf("Every minute" to (settings.intervalMinutes == 1), "Every 5 minutes" to (settings.intervalMinutes == 5)),
                        "One minute checks sooner; five minutes uses less battery and network.") { callbacks.onIntervalChanged(if (it == "Every minute") 1 else 5); sheet = null }
                    SettingsSheet.PRESENTATION -> SheetChoices("Notification presentation", listOf("Graph" to !settings.enhancedNotification, "Live Update" to settings.enhancedNotification),
                        "Live Update is requested on supported devices; Android may show the graph notification instead.") { callbacks.onEnhancedNotificationChanged(it == "Live Update"); sheet = null }
                    SettingsSheet.ALERT_PERSON -> SheetChoices("Alert rules for", roster.map { it.displayName to (it.id == settings.alertPersonId) },
                        "Each person keeps independent alert rules.") { label -> roster.firstOrNull { it.displayName == label }?.let { callbacks.onViewedPersonChanged(it.id) }; sheet = null }
                    SettingsSheet.ACCENT -> {
                        Text("App color", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                        Text("Ocean blue is the Glance default. Custom saved colors remain supported.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AccentChoice(settings.accentHex, callbacks.onAccentHexChanged)
                        Button({ sheet = null }, Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        }
    }
    if (help) AlertDialog({ help = false }, title = { Text("How notifications work") },
        text = { Text("Routine readings are silent. Lock-screen privacy also hides the number icon and Live Update value. Android can fall back to the standard graph notification at any time.") },
        confirmButton = { TextButton({ help = false }) { Text("Done") } })
    confirm?.let { action ->
        AlertDialog({ confirm = null }, title = { Text(if (action == ConfirmAction.SIGN_OUT) "Sign out?" else "Delete local data?") },
            text = { Text(if (action == ConfirmAction.SIGN_OUT) "Monitoring will stop and saved account and reading data will be removed." else "Saved readings and local diagnostics will be removed. This cannot be undone.") },
            confirmButton = { TextButton({ confirm = null; if (action == ConfirmAction.SIGN_OUT) callbacks.onSignOut() else callbacks.onDeleteLocalData() }) { Text(if (action == ConfirmAction.SIGN_OUT) "Sign out" else "Delete") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
}

@Composable private fun ScreenList(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp), content = content)

@Composable
private fun ScreenHeader(title: String, subtitle: String, action: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action?.invoke()
    }
}

@Composable private fun Badge(text: String) = Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
    Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Text(title.uppercase(), Modifier.padding(start = 5.dp).semantics { heading() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(Modifier.fillMaxWidth(), GlanceShapes.large, MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), content = content) }
}
@Composable private fun GroupDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable private fun ValueSettingRow(title: String, value: String, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 58.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ActionSettingRow(title: String, danger: Boolean = false, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    Text("›", style = MaterialTheme.typography.titleLarge, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun SwitchSettingRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) = Row(
    Modifier.fillMaxWidth().heightIn(min = 68.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
    Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Switch(checked, null)
}

@Composable private fun SheetChoices(title: String, choices: List<Pair<String, Boolean>>, summary: String? = null, onSelect: (String) -> Unit) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    summary?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Column(Modifier.selectableGroup()) { choices.forEachIndexed { index, item ->
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = item.second, role = Role.RadioButton) { onSelect(item.first) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.first, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Surface(Modifier.size(22.dp), CircleShape, if (item.second) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, if (item.second) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)) {
                if (item.second) Box(Modifier.padding(6.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape))
            }
        }
        if (index < choices.lastIndex) GroupDivider()
    } }
}

@Composable private fun AccentChoice(selectedHex: String, onSelect: (String) -> Unit) {
    var draft by rememberSaveable(selectedHex) { mutableStateOf(selectedHex) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val normalized = ThemeAccent.normalize(draft)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeAccent.presets.forEach { preset -> FilterChip(selectedHex.equals(preset.hex, true), { onSelect(preset.hex) }, label = { Text(preset.name) }, leadingIcon = { Text("●", color = ThemeAccent.color(preset.hex)) }) }
    }
    OutlinedButton({ custom = !custom }, Modifier.fillMaxWidth()) { Text(if (custom) "Close custom color" else "Custom color") }
    AnimatedVisibility(custom) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorWheelPicker(normalized ?: selectedHex) { draft = it }
        OutlinedTextField(draft, { if (it.length <= 7) draft = it }, Modifier.fillMaxWidth(), label = { Text("Hex color") },
            placeholder = { Text(ThemeAccent.DEFAULT) }, singleLine = true, isError = draft.isNotBlank() && normalized == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), supportingText = { Text(if (normalized == null) "Enter six hex digits." else "Adjusted when needed for readable contrast.") })
        Button({ normalized?.let(onSelect) }, Modifier.fillMaxWidth(), normalized != null && !normalized.equals(selectedHex, true)) { Text("Apply color") }
    } }
}

internal fun rosterStates(display: DisplayState): List<PersonDisplayState> {
    val primary = display.person?.let { PersonDisplayState(it, display.reading, display.freshness, display.connection, display.lastSuccessfulFetch, display.history) }
    return (listOfNotNull(primary) + display.people).distinctBy { it.person.id }
}

internal fun groupAlertHistory(history: List<AlertHistoryEntry>, zoneId: ZoneId): List<Pair<LocalDate, List<AlertHistoryEntry>>> =
    history.sortedByDescending { it.firedAt }.groupBy { it.firedAt.atZone(zoneId).toLocalDate() }.map { it.key to it.value }

internal fun readingDeltaText(current: Reading?, history: List<Reading>, unit: GlucoseUnit, locale: Locale): String? {
    val currentValue = current?.valueMgDl?.takeIf { it.isFinite() } ?: return null
    if (current.specialValue != null) return null
    val prior = history.filter { it.connectionId == current.connectionId && it.measuredAt < current.measuredAt && it.valueMgDl?.isFinite() == true && it.specialValue == null }.maxByOrNull { it.measuredAt } ?: return null
    val minutes = Duration.between(prior.measuredAt, current.measuredAt).toMinutes()
    if (minutes !in 1..30) return null
    val delta = currentValue - prior.valueMgDl!!
    val shown = if (unit == GlucoseUnit.MG_DL) String.format(locale, "%+.0f", delta) else String.format(locale, "%+.1f", delta / 18.0182)
    return "$shown ${unit.label} in $minutes min"
}

private val nonRefreshableConnections = setOf(ConnectionState.SIGN_IN_REQUIRED, ConnectionState.TERMS_REQUIRED,
    ConnectionState.CONNECTION_REMOVED, ConnectionState.PROTOCOL_ERROR, ConnectionState.LOCAL_ERROR, ConnectionState.FETCHING)
private val reconnectConnections = setOf(ConnectionState.SIGN_IN_REQUIRED, ConnectionState.TERMS_REQUIRED,
    ConnectionState.CONNECTION_REMOVED, ConnectionState.PROTOCOL_ERROR)
private fun trendSymbol(trend: Trend) = when (trend) {
    Trend.RISING_QUICKLY -> "↑"; Trend.RISING, Trend.RISING_SLOWLY -> "↗"; Trend.STEADY -> "→"
    Trend.FALLING, Trend.FALLING_SLOWLY -> "↘"; Trend.FALLING_QUICKLY -> "↓"; Trend.UNKNOWN -> ""
}
private fun personStatus(state: PersonDisplayState) = when (state.connection) {
    ConnectionState.READY -> when (state.freshness) { ReadingFreshness.FRESH -> "Current"; ReadingFreshness.DELAYED -> "Reading delayed"; ReadingFreshness.STALE -> "No recent reading"; ReadingFreshness.INVALID_TIME -> "Reading time unavailable"; ReadingFreshness.ABSENT -> "Waiting for first reading" }
    ConnectionState.FETCHING -> "Checking now"; ConnectionState.OFFLINE -> "Offline · showing last reading"; ConnectionState.BACKING_OFF -> "Waiting to retry"
    ConnectionState.SIGN_IN_REQUIRED -> "Sign-in required"; ConnectionState.TERMS_REQUIRED -> "LibreLinkUp terms required"; ConnectionState.CONNECTION_REMOVED -> "Connection unavailable"
    ConnectionState.PROTOCOL_ERROR -> "Source response unavailable"; ConnectionState.LOCAL_ERROR -> "Local data unavailable"
}
private fun dayLabel(day: LocalDate, now: Instant, zoneId: ZoneId, locale: Locale): String {
    val today = now.atZone(zoneId).toLocalDate()
    return when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day) }
}
private fun accentName(hex: String) = ThemeAccent.presets.firstOrNull { it.hex.equals(hex, true) }?.name ?: "Custom"
private fun permissionStatusShort(status: PermissionStatus) = when (status) { PermissionStatus.UNKNOWN -> "not checked"; PermissionStatus.GRANTED -> "allowed"; PermissionStatus.DENIED -> "not allowed" }
private fun batteryStatusShort(status: BatteryStatus) = when (status) { BatteryStatus.UNKNOWN -> "not checked"; BatteryStatus.STANDARD -> "standard"; BatteryStatus.UNRESTRICTED -> "unrestricted" }
