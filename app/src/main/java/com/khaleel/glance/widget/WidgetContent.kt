package com.khaleel.glance.widget

import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseFormat.label
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.Trend
import com.khaleel.glance.data.ReadingPolicy
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

internal data class WidgetContent(
    val heading: String,
    val value: String,
    val unit: String = "",
    val arrow: String = "",
    val delta: String? = null,
    val measured: String,
    val note: String? = null,
    val samples: List<Reading> = emptyList(),
    val accessibility: String,
)

/** Rechecks source time and selected identity at every host update; does not fetch readings. */
internal object WidgetContentFormatter {
    fun format(
        state: DisplayState, unit: GlucoseUnit, visible: Boolean, hasAccount: Boolean,
        now: Instant, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault(),
    ): WidgetContent {
        fun status(value: String, detail: String) = WidgetContent(
            "Glance", value, measured = detail, accessibility = "Glance. $value. $detail",
        )
        if (!visible) return status("Hidden", "Enable Widget values in Glance Settings")
        if (!hasAccount) return status("Sign in", "Open Glance on your phone")
        val person = state.person ?: return status("Select person", "Open Glance on your phone")
        if (!state.monitoringEnabled || state.runtime == RuntimeState.STOPPED) {
            return status("Stopped", "Start monitoring in Glance")
        }
        if (state.runtime != RuntimeState.RUNNING) return status("Waiting", "Monitoring is not running yet")
        when (state.connection) {
            ConnectionState.SIGN_IN_REQUIRED -> return status("Sign in", "Reconnect in Glance")
            ConnectionState.TERMS_REQUIRED -> return status("Action needed", "Check LibreLinkUp on your phone")
            ConnectionState.CONNECTION_REMOVED -> return status("No share", "Choose a shared person in Glance")
            ConnectionState.PROTOCOL_ERROR, ConnectionState.LOCAL_ERROR -> return status("Unavailable", "Check Glance on your phone")
            else -> Unit
        }
        val reading = state.reading?.takeIf { it.connectionId == person.id }
            ?: return status("Waiting", "No reading for the selected person")
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
        val measured = "Reading ${time.format(reading.measuredAt)}"
        val value = GlucoseFormat.value(reading, unit, locale)
        val freshness = ReadingPolicy.freshness(reading, now)
        if (freshness != ReadingFreshness.FRESH) {
            val label = when (freshness) {
                ReadingFreshness.DELAYED -> "Delayed"
                ReadingFreshness.STALE -> "Stale"
                else -> "Time unknown"
            }
            return WidgetContent("Glance · ${person.displayName}", label,
                measured = "Last: $value ${unit.label} · ${time.format(reading.measuredAt)}",
                accessibility = "Glance. $label. Last reading $value ${unit.label} at ${time.format(reading.measuredAt)}")
        }
        val numeric = GlucoseFormat.numericValue(reading, unit)
        val trend = if (numeric == null) "" else when (reading.trend) {
            Trend.RISING_QUICKLY -> "↑"
            Trend.RISING, Trend.RISING_SLOWLY -> "↗"
            Trend.STEADY -> "→"
            Trend.FALLING, Trend.FALLING_SLOWLY -> "↘"
            Trend.FALLING_QUICKLY -> "↓"
            Trend.UNKNOWN -> ""
        }
        val trendWords = if (numeric == null) "" else when (reading.trend) {
            Trend.RISING_QUICKLY -> "rising quickly"
            Trend.RISING -> "rising"
            Trend.RISING_SLOWLY -> "rising slowly"
            Trend.STEADY -> "steady"
            Trend.FALLING_SLOWLY -> "falling slowly"
            Trend.FALLING -> "falling"
            Trend.FALLING_QUICKLY -> "falling quickly"
            Trend.UNKNOWN -> "trend unavailable"
        }
        val samples = if (numeric != null) history(state) else emptyList()
        val previous = samples.lastOrNull { it.measuredAt < reading.measuredAt &&
            Duration.between(it.measuredAt, reading.measuredAt) <= Duration.ofMinutes(15) &&
            GlucoseFormat.numericValue(it, unit) != null }
        val delta = if (numeric != null && previous != null) {
            val difference = numeric - GlucoseFormat.numericValue(previous, unit)!!
            val amount = when (unit) {
                GlucoseUnit.MG_DL -> difference.roundToInt().let { if (it > 0) "+$it" else "$it" }
                GlucoseUnit.MMOL_L -> String.format(locale, "%+.1f", difference)
            }
            val seconds = Duration.between(previous.measuredAt, reading.measuredAt).seconds
            val interval = if (seconds % 60 == 0L) "${seconds / 60} min" else "$seconds sec"
            "$amount / $interval"
        } else null
        val note = when (state.connection) {
            ConnectionState.OFFLINE -> "Offline"
            ConnectionState.BACKING_OFF -> "Retrying later"
            else -> if (numeric == null) "Source: $value" else null
        }
        return WidgetContent(
            heading = "Glance · ${person.displayName}", value = value,
            unit = if (numeric != null) unit.label else "", arrow = trend, delta = delta,
            measured = measured, note = note, samples = samples,
            accessibility = listOfNotNull(person.displayName, value, if (numeric != null) unit.label else null,
                trendWords.takeIf { it.isNotEmpty() }, delta?.let { "Change $it ${unit.label}" }, measured, note).joinToString(". "),
        )
    }

    fun history(state: DisplayState): List<Reading> {
        val current = state.reading ?: return emptyList()
        if (current.connectionId != state.person?.id || GlucoseFormat.numericValue(current, GlucoseUnit.MG_DL) == null) return emptyList()
        val start = current.measuredAt.minus(Duration.ofHours(4))
        // Latest source corrections win in history; the current reading wins at its own time.
        return (state.history.sortedByDescending { it.receivedAt }.let { listOf(current) + it })
            .filter { it.connectionId == current.connectionId && it.measuredAt >= start &&
                it.measuredAt <= current.measuredAt && GlucoseFormat.numericValue(it, GlucoseUnit.MG_DL) != null }
            .distinctBy { it.measuredAt }.sortedBy { it.measuredAt }
    }
}
