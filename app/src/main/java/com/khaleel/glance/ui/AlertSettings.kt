package com.khaleel.glance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.alerts.AlertKind
import com.khaleel.glance.alerts.AlertRule
import com.khaleel.glance.core.GlucoseUnit
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun AlertSettings(
    config: AlertConfig,
    unit: GlucoseUnit,
    onRuleChange: (AlertKind, AlertRule) -> Unit,
    onSoundChange: (AlertKind) -> Unit,
) {
    var expandedName by rememberSaveable { mutableStateOf<String?>(null) }
    Text(
        "Alerts are off until you enable them. Urgent alerts request high priority; monitoring, notification permission, and Android's sound settings still control delivery.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        TextButton(onClick = { expandedName = if (expandedName == kind.name) null else kind.name }) {
            Text(if (expandedName == kind.name) "Close ${kind.label} settings" else "Set ${kind.label} threshold and sound")
        }
        if (expandedName == kind.name) {
            AlertRuleEditor(kind, rule, config, unit, onRuleChange, onSoundChange)
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
    onSoundChange: (AlertKind) -> Unit,
) {
    val (minimum, maximum) = when (kind) {
        AlertKind.URGENT_LOW -> 40 to config[AlertKind.LOW].thresholdMgDl
        AlertKind.LOW -> config[AlertKind.URGENT_LOW].thresholdMgDl to (config[AlertKind.HIGH].thresholdMgDl - 1)
        AlertKind.HIGH -> (config[AlertKind.LOW].thresholdMgDl + 1) to config[AlertKind.URGENT_HIGH].thresholdMgDl
        AlertKind.URGENT_HIGH -> config[AlertKind.HIGH].thresholdMgDl to 400
    }
    var threshold by rememberSaveable(kind.name, rule.thresholdMgDl) {
        mutableFloatStateOf(rule.thresholdMgDl.toFloat())
    }
    var cooldown by rememberSaveable(kind.name, rule.cooldownMinutes) {
        mutableFloatStateOf(rule.cooldownMinutes.toFloat())
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "${if (kind.isLow) "Alert at or below" else "Alert at or above"} ${thresholdText(threshold.roundToInt(), unit)}",
            style = MaterialTheme.typography.titleMedium,
        )
        if (minimum < maximum) {
            Slider(
                value = thresholdPosition(threshold.roundToInt().coerceIn(minimum, maximum), unit),
                onValueChange = { threshold = thresholdFromPosition(it, unit).toFloat() },
                onValueChangeFinished = {
                    onRuleChange(kind, rule.copy(thresholdMgDl = threshold.roundToInt().coerceIn(minimum, maximum)))
                },
                valueRange = thresholdPosition(minimum, unit)..thresholdPosition(maximum, unit),
                modifier = Modifier.semantics { contentDescription = "${kind.label} threshold in ${unit.label}" },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    onRuleChange(kind, rule.copy(thresholdMgDl = adjustedThreshold(rule.thresholdMgDl, unit, -1).coerceIn(minimum, maximum)))
                },
                enabled = rule.thresholdMgDl > minimum,
            ) { Text("−") }
            OutlinedButton(
                onClick = {
                    onRuleChange(kind, rule.copy(thresholdMgDl = adjustedThreshold(rule.thresholdMgDl, unit, 1).coerceIn(minimum, maximum)))
                },
                enabled = rule.thresholdMgDl < maximum,
            ) { Text("+") }
        }
        Text("Repeat cooldown: ${cooldown.roundToInt()} minutes")
        Slider(
            value = cooldown,
            onValueChange = { cooldown = it },
            onValueChangeFinished = {
                onRuleChange(kind, rule.copy(cooldownMinutes = cooldown.roundToInt().coerceIn(1, 60)))
            },
            valueRange = 1f..60f,
            modifier = Modifier.semantics { contentDescription = "${kind.label} alert cooldown in minutes" },
        )
        OutlinedButton(onClick = { onSoundChange(kind) }) { Text("Choose ${kind.label} sound in Android settings") }
    }
}

private val AlertKind.isLow: Boolean
    get() = this == AlertKind.LOW || this == AlertKind.URGENT_LOW

private val GlucoseUnit.label: String
    get() = if (this == GlucoseUnit.MG_DL) "mg/dL" else "mmol/L"

private fun thresholdText(mgDl: Int, unit: GlucoseUnit): String =
    if (unit == GlucoseUnit.MG_DL) "$mgDl mg/dL"
    else String.format(Locale.getDefault(), "%.1f mmol/L", mgDl / 18.0182)

private fun adjustedThreshold(mgDl: Int, unit: GlucoseUnit, direction: Int): Int =
    if (unit == GlucoseUnit.MG_DL) mgDl + direction
    else ((((mgDl / 18.0182 * 10).roundToInt() + direction) / 10.0) * 18.0182).roundToInt()

private fun thresholdPosition(mgDl: Int, unit: GlucoseUnit): Float =
    if (unit == GlucoseUnit.MG_DL) mgDl.toFloat() else (mgDl / 18.0182).toFloat()

private fun thresholdFromPosition(value: Float, unit: GlucoseUnit): Int =
    if (unit == GlucoseUnit.MG_DL) value.roundToInt() else (value * 18.0182).roundToInt()
