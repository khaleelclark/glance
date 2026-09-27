package com.khaleel.glance.ui

import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseFormat.label
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.Trend
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class ReadingUiText(
    val value: String,
    val unit: String,
    val trendSymbol: String,
    val trendDescription: String,
    val measured: String,
    val freshness: String,
)

class GlanceUiText(
    zoneId: ZoneId,
    private val locale: Locale,
) {
    private val timeFormatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)

    fun reading(state: DisplayState, unit: GlucoseUnit, now: Instant): ReadingUiText? {
        val reading = state.reading ?: return null
        val trend = trend(reading.trend, state.freshness)
        return ReadingUiText(
            value = GlucoseFormat.value(reading, unit, locale),
            unit = unit.label,
            trendSymbol = trend.first,
            trendDescription = trend.second,
            measured = "Reading at ${timeFormatter.format(reading.measuredAt)} · ${age(reading.measuredAt, now)}",
            freshness = when (state.freshness) {
                ReadingFreshness.ABSENT -> "Waiting for first reading"
                ReadingFreshness.FRESH -> "Current reading"
                ReadingFreshness.DELAYED -> "Reading delayed"
                ReadingFreshness.STALE -> "No recent reading"
                ReadingFreshness.INVALID_TIME -> "Reading time unavailable"
            },
        )
    }

    fun monitoring(state: DisplayState): String = when {
        !state.monitoringEnabled -> "Monitoring paused"
        state.runtime == RuntimeState.STARTING -> "Starting monitoring…"
        state.runtime == RuntimeState.RUNNING -> "Monitoring is running"
        state.runtime == RuntimeState.UNABLE_TO_START -> "Monitoring could not start"
        else -> "Monitoring is stopped"
    }

    fun connection(state: DisplayState): String? = when (state.connection) {
        ConnectionState.READY -> null
        ConnectionState.FETCHING -> "Checking for a new reading…"
        ConnectionState.OFFLINE -> lastReadingSuffix(state, "No internet")
        ConnectionState.BACKING_OFF -> lastReadingSuffix(state, "Waiting before trying again")
        ConnectionState.SIGN_IN_REQUIRED -> "Sign in again to continue."
        ConnectionState.TERMS_REQUIRED -> "Open LibreLinkUp to review its terms, then reconnect."
        ConnectionState.CONNECTION_REMOVED -> "The selected shared person is no longer available."
        ConnectionState.PROTOCOL_ERROR -> lastReadingSuffix(state, "LibreLinkUp returned an unexpected response")
    }

    fun notificationPreview(state: DisplayState, settings: UserSettingsUi, now: Instant): Pair<String, String> {
        if (!settings.showOnLockScreen) return "Glance" to "New glucose reading available"
        val person = state.person?.displayName ?: "Shared glucose reading"
        val reading = reading(state, settings.unit, now)
            ?: return person to "Waiting for first reading"
        val title = if (state.freshness == ReadingFreshness.FRESH) {
            "$person · ${reading.value} ${reading.unit} ${reading.trendSymbol}".trim()
        } else {
            "$person · ${reading.freshness}"
        }
        return title to reading.measured
    }

    private fun trend(trend: Trend, freshness: ReadingFreshness): Pair<String, String> {
        if (freshness == ReadingFreshness.STALE || freshness == ReadingFreshness.INVALID_TIME) return "" to "Trend unavailable"
        return when (trend) {
            Trend.RISING_QUICKLY -> "↑" to "rising quickly"
            Trend.RISING -> "↗" to "rising"
            Trend.RISING_SLOWLY -> "↗" to "rising slowly"
            Trend.STEADY -> "→" to "steady"
            Trend.FALLING_SLOWLY -> "↘" to "falling slowly"
            Trend.FALLING -> "↘" to "falling"
            Trend.FALLING_QUICKLY -> "↓" to "falling quickly"
            Trend.UNKNOWN -> "" to "trend unavailable"
        }
    }

    private fun age(measuredAt: Instant, now: Instant): String {
        val seconds = Duration.between(measuredAt, now).seconds
        if (seconds < -120) return "time unavailable"
        if (seconds < 60) return "just now"
        val minutes = seconds / 60
        if (minutes < 60) return if (minutes == 1L) "1 min ago" else "$minutes min ago"
        val hours = minutes / 60
        return if (hours == 1L) "1 hr ago" else "$hours hr ago"
    }

    private fun lastReadingSuffix(state: DisplayState, message: String) =
        if (state.reading == null) "$message. No reading is available yet." else "$message. Showing the last reading."

}
