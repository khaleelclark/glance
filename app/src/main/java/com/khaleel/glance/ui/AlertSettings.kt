package com.khaleel.glance.ui

import android.graphics.Color
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.AlertRule
import com.khaleel.glance.core.GlucoseUnit
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun AlertSettings(
    config: AlertConfig,
    unit: GlucoseUnit,
    onRuleChange: (AlertKind, AlertRule) -> Unit,
    onSoundChange: (AlertKind) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    var expandedName by rememberSaveable { mutableStateOf<String?>(null) }
    Text(
        "Alerts are off until you enable them. Each alert has its own Android notification category for sound and priority.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
        Text("All Android notification categories")
    }
    Text(
        "On Samsung, if categories are hidden, enable Settings → Notifications → Advanced settings → Manage notification categories for each app.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    AlertKind.entries.forEach { kind ->
        val rule = config[kind]
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(kind.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${if (kind.isLow) "At or below" else "At or above"} ${thresholdText(rule.thresholdMgDl, unit)} · ${rule.cooldownMinutes} min",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onRuleChange(kind, rule.copy(enabled = it)) },
                modifier = Modifier.semantics { contentDescription = "Enable ${kind.label} alert" },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = { expandedName = if (expandedName == kind.name) null else kind.name },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (expandedName == kind.name) "Close value wheel" else "Value & repeat", textAlign = TextAlign.Center)
            }
            TextButton(
                onClick = { onSoundChange(kind) },
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "Open ${kind.label} notification category in Android settings"
                },
            ) { Text("Sound & priority", textAlign = TextAlign.Center) }
        }
        if (expandedName == kind.name) {
            AlertRuleEditor(kind, rule, config, unit, onRuleChange)
        }
        if (kind != AlertKind.URGENT_HIGH) HorizontalDivider()
    }
}

@Composable
private fun AlertRuleEditor(
    kind: AlertKind,
    rule: AlertRule,
    config: AlertConfig,
    unit: GlucoseUnit,
    onRuleChange: (AlertKind, AlertRule) -> Unit,
) {
    val (minimum, maximum) = when (kind) {
        AlertKind.URGENT_LOW -> 40 to config[AlertKind.LOW].thresholdMgDl
        AlertKind.LOW -> config[AlertKind.URGENT_LOW].thresholdMgDl to (config[AlertKind.HIGH].thresholdMgDl - 1)
        AlertKind.HIGH -> (config[AlertKind.LOW].thresholdMgDl + 1) to config[AlertKind.URGENT_HIGH].thresholdMgDl
        AlertKind.URGENT_HIGH -> config[AlertKind.HIGH].thresholdMgDl to 400
    }
    val thresholdValues = remember(minimum, maximum, unit) { thresholdWheelValues(minimum, maximum, unit) }
    val currentRule by rememberUpdatedState(rule)
    val currentOnRuleChange by rememberUpdatedState(onRuleChange)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${if (kind.isLow) "Alert at or below" else "Alert at or above"} ${thresholdText(rule.thresholdMgDl, unit)}",
            style = MaterialTheme.typography.titleMedium,
        )
        ValueWheel(
            values = thresholdValues,
            selectedValue = rule.thresholdMgDl,
            unitLabel = unit.label,
            description = "${kind.label} glucose threshold",
            onValueSelected = { currentOnRuleChange(kind, currentRule.copy(thresholdMgDl = it)) },
        )
        Text("Repeat cooldown: ${rule.cooldownMinutes} minutes", style = MaterialTheme.typography.titleMedium)
        ValueWheel(
            values = remember { (1..60).map { WheelValue(it, it.toString()) } },
            selectedValue = rule.cooldownMinutes,
            unitLabel = "min",
            description = "${kind.label} alert repeat cooldown",
            onValueSelected = { currentOnRuleChange(kind, currentRule.copy(cooldownMinutes = it)) },
        )
    }
}

@Composable
private fun ValueWheel(
    values: List<WheelValue>,
    selectedValue: Int,
    unitLabel: String,
    description: String,
    onValueSelected: (Int) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val textSize = with(LocalDensity.current) { 28.sp.toPx() }
    val currentOnValueSelected by rememberUpdatedState(onValueSelected)
    val dividerColor = MaterialTheme.colorScheme.primary
    var pendingValue by remember(values) { mutableIntStateOf(selectedValue) }
    LaunchedEffect(pendingValue) {
        delay(300)
        if (pendingValue != selectedValue) currentOnValueSelected(pendingValue)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        key(values.first().storedValue, values.last().storedValue, values.size, unitLabel) {
            Box(Modifier.width(168.dp).height(168.dp)) {
                AndroidView(
                    factory = { context ->
                        NumberPicker(context).apply {
                            minValue = 0
                            maxValue = values.lastIndex
                            displayedValues = values.map { it.label }.toTypedArray()
                            value = nearestWheelIndex(values, selectedValue)
                            wrapSelectorWheel = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            setBackgroundColor(Color.TRANSPARENT)
                            setSelectionDividerHeight(0)
                            setTextColor(textColor)
                            setTextSize(textSize)
                            contentDescription = description
                            setOnValueChangedListener { _, _, index -> pendingValue = values[index].storedValue }
                        }
                    },
                    update = { picker ->
                        picker.setTextColor(textColor)
                        picker.setTextSize(textSize)
                        val selectedIndex = nearestWheelIndex(values, selectedValue)
                        if (picker.value != selectedIndex && pendingValue == selectedValue) picker.value = selectedIndex
                    },
                    modifier = Modifier.width(168.dp).height(168.dp),
                )
                HorizontalDivider(Modifier.align(Alignment.Center).offset(y = (-28).dp), color = dividerColor)
                HorizontalDivider(Modifier.align(Alignment.Center).offset(y = 28.dp), color = dividerColor)
            }
        }
        Text(unitLabel, style = MaterialTheme.typography.titleLarge)
    }
}

private val AlertKind.isLow: Boolean
    get() = this == AlertKind.LOW || this == AlertKind.URGENT_LOW

private val GlucoseUnit.label: String
    get() = if (this == GlucoseUnit.MG_DL) "mg/dL" else "mmol/L"

private fun thresholdText(mgDl: Int, unit: GlucoseUnit): String =
    if (unit == GlucoseUnit.MG_DL) "$mgDl mg/dL"
    else String.format(Locale.getDefault(), "%.1f mmol/L", mgDl / 18.0182)
