package com.khaleel.glance.ui

import android.graphics.Color
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.AlertRule
import com.khaleel.glance.core.GlucoseUnit
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlertSettings(
    config: AlertConfig,
    unit: GlucoseUnit,
    onRuleChange: (AlertKind, AlertRule) -> Unit,
    onSoundChange: (AlertKind) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = AlertKind.entries.firstOrNull { it.name == editingName }
    Text(
        "Choose a limit, then turn it on.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AlertKind.entries.forEachIndexed { index, kind ->
        val rule = config[kind]
        Row(
            Modifier.fillMaxWidth().heightIn(min = 70.dp).clickable { editingName = kind.name }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(kind.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${if (kind.isLow) "At or below" else "At or above"} ${thresholdText(rule.thresholdMgDl, unit)} · ${rule.cooldownMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onRuleChange(kind, rule.copy(enabled = it)) },
                modifier = Modifier.semantics { contentDescription = "Enable ${kind.label} alert" },
            )
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (index < AlertKind.entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("Android alert settings")
    }

    editing?.let { kind ->
        ModalBottomSheet(
            onDismissRequest = { editingName = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            AlertRuleSheet(
                kind = kind,
                savedRule = config[kind],
                config = config,
                unit = unit,
                onRuleChange = onRuleChange,
                onSoundChange = onSoundChange,
                onDone = { editingName = null },
            )
        }
    }
}

@Composable
private fun AlertRuleSheet(
    kind: AlertKind,
    savedRule: AlertRule,
    config: AlertConfig,
    unit: GlucoseUnit,
    onRuleChange: (AlertKind, AlertRule) -> Unit,
    onSoundChange: (AlertKind) -> Unit,
    onDone: () -> Unit,
) {
    var draft by remember(kind) { mutableStateOf(savedRule) }
    var pending by remember(kind) { mutableStateOf<AlertRule?>(null) }
    var saveAttempt by remember(kind) { mutableIntStateOf(0) }
    val currentSave by rememberUpdatedState(onRuleChange)
    fun flush() {
        val next = pending ?: return
        pending = null
        try { currentSave(kind, next) } finally { saveAttempt++ }
    }
    fun stage(next: AlertRule) { draft = next; pending = next }
    LaunchedEffect(savedRule, saveAttempt) { if (pending == null) draft = savedRule }
    LaunchedEffect(pending) { if (pending != null) { delay(300); flush() } }
    DisposableEffect(Unit) { onDispose { flush() } }

    val (minimum, maximum) = alertRange(kind, config)
    val values = remember(minimum, maximum, unit) { thresholdWheelValues(minimum, maximum, unit) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(kind.label, style = MaterialTheme.typography.headlineSmall)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                contentDescription = "Enable ${kind.label} alert"
                toggleableState = ToggleableState(draft.enabled)
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Alert enabled", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(draft.enabled, { stage(draft.copy(enabled = it)); flush() })
        }
        Text(
            "${if (kind.isLow) "Alert at or below" else "Alert at or above"} ${thresholdText(draft.thresholdMgDl, unit)}",
            style = MaterialTheme.typography.titleMedium,
        )
        ValueWheel(values, draft.thresholdMgDl, unit.label, "${kind.label} glucose threshold") {
            stage(draft.copy(thresholdMgDl = it))
        }
        Text("Repeat after ${draft.cooldownMinutes} minutes", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = draft.cooldownMinutes.toFloat(),
            onValueChange = { stage(draft.copy(cooldownMinutes = it.roundToInt().coerceIn(1, 60))) },
            onValueChangeFinished = ::flush,
            valueRange = 1f..60f,
            steps = 58,
            modifier = Modifier.semantics { contentDescription = "${kind.label} repeat cooldown" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSoundChange(kind) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Sound & priority")
            }
            TextButton(onClick = { flush(); onDone() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ValueWheel(
    values: List<WheelValue>, selectedValue: Int, unitLabel: String, description: String, onValueSelected: (Int) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSize = with(LocalDensity.current) { 24.sp.toPx() }
    val dividerColor = MaterialTheme.colorScheme.primary
    val currentOnSelect by rememberUpdatedState(onValueSelected)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        key(values.first().storedValue, values.last().storedValue, values.size, unitLabel) {
            Box(Modifier.width(160.dp).height(132.dp)) {
                AndroidView(
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = 0; maxValue = values.lastIndex
                            displayedValues = values.map { it.label }.toTypedArray()
                            value = nearestWheelIndex(values, selectedValue)
                            wrapSelectorWheel = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setBackgroundColor(Color.TRANSPARENT)
                            setSelectionDividerHeight(0); setTextColor(textColor); setTextSize(textSize)
                            contentDescription = description
                            setOnValueChangedListener { _, _, index -> currentOnSelect(values[index].storedValue) }
                        }
                    },
                    update = { picker ->
                        picker.setTextColor(textColor); picker.setTextSize(textSize)
                        val index = nearestWheelIndex(values, selectedValue)
                        if (picker.value != index) picker.value = index
                    },
                    modifier = Modifier.width(160.dp).height(132.dp),
                )
                HorizontalDivider(Modifier.align(Alignment.Center).offset(y = (-24).dp), color = dividerColor)
                HorizontalDivider(Modifier.align(Alignment.Center).offset(y = 24.dp), color = dividerColor)
            }
        }
        Text(unitLabel, style = MaterialTheme.typography.titleLarge)
    }
}

private fun alertRange(kind: AlertKind, config: AlertConfig): Pair<Int, Int> = when (kind) {
    AlertKind.URGENT_LOW -> 40 to config[AlertKind.LOW].thresholdMgDl
    AlertKind.LOW -> config[AlertKind.URGENT_LOW].thresholdMgDl to (config[AlertKind.HIGH].thresholdMgDl - 1)
    AlertKind.HIGH -> (config[AlertKind.LOW].thresholdMgDl + 1) to config[AlertKind.URGENT_HIGH].thresholdMgDl
    AlertKind.URGENT_HIGH -> config[AlertKind.HIGH].thresholdMgDl to 400
}
private val AlertKind.isLow get() = this == AlertKind.LOW || this == AlertKind.URGENT_LOW
private val GlucoseUnit.label get() = if (this == GlucoseUnit.MG_DL) "mg/dL" else "mmol/L"
private fun thresholdText(mgDl: Int, unit: GlucoseUnit) = if (unit == GlucoseUnit.MG_DL) "$mgDl mg/dL"
else String.format(Locale.getDefault(), "%.1f mmol/L", mgDl / 18.0182)
