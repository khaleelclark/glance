package com.khaleel.glance.notification

import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.Trend
import java.time.ZoneId
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

internal data class NotificationContent(
    val title: String,
    val text: String,
    val bigText: String,
    val statusChipText: String? = null,
    val prominent: String = title,
    val prominentUnit: String? = null,
    val prominentArrow: String? = null,
    val prominentDelta: String? = null,
    val compactDetail: String = text,
    val expandedDetail: String = bigText,
)

internal class NotificationContentFormatter(
    zoneId: ZoneId,
    private val locale: Locale,
) {
    private val timeFormatter = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)

    fun format(state: DisplayState, unit: GlucoseUnit): NotificationContent {
        val person = state.person?.displayName ?: "Glance"
        if (!state.monitoringEnabled) return stoppedContent(person, state.reading, unit)
        if (state.runtime == RuntimeState.UNABLE_TO_START) {
            val last = state.reading?.let {
                "Last: ${formatValue(it, unit)} at ${timeFormatter.format(it.measuredAt)}"
            }
            return NotificationContent(
                title = "$person · Monitoring unavailable",
                text = last ?: "Open Glance to resume monitoring.",
                bigText = listOfNotNull(
                    last,
                    "Monitoring could not start. Open Glance to review notification and background access.",
                ).joinToString("\n"),
                prominent = "UNAVAILABLE",
                compactDetail = "$person · ${last ?: "Open Glance to resume"}",
            )
        }

        val reading = state.reading
        if (reading == null || state.freshness == ReadingFreshness.ABSENT) {
            return absentContent(person, state.connection)
        }

        val value = formatValue(reading, unit)
        val measuredAt = timeFormatter.format(reading.measuredAt)
        val connectionNote = connectionNote(state.connection)

        return when (state.freshness) {
            ReadingFreshness.FRESH -> {
                val arrow = trendArrow(reading.trend)
                val delta = recentDelta(reading, state.history, unit)
                val text = listOfNotNull(value, arrow, delta).joinToString(" ")
                val timeAndPerson = "$person · $measuredAt"
                NotificationContent(
                    title = timeAndPerson,
                    text = text,
                    bigText = listOfNotNull(
                        text,
                        connectionNote,
                    ).joinToString("\n"),
                    statusChipText = when (state.connection) {
                        ConnectionState.SIGN_IN_REQUIRED,
                        ConnectionState.TERMS_REQUIRED,
                        ConnectionState.CONNECTION_REMOVED,
                        ConnectionState.PROTOCOL_ERROR,
                        ConnectionState.LOCAL_ERROR -> "CHECK"
                        else -> GlucoseFormat.value(reading, unit, locale)
                            .takeUnless { it == "Unavailable" } ?: "WAIT"
                    },
                    prominent = GlucoseFormat.value(reading, unit, locale),
                    prominentUnit = when (unit) {
                        GlucoseUnit.MG_DL -> "mg/dL"
                        GlucoseUnit.MMOL_L -> "mmol/L"
                    },
                    prominentArrow = arrow,
                    prominentDelta = delta,
                    compactDetail = timeAndPerson,
                    expandedDetail = listOfNotNull(
                        timeAndPerson,
                        connectionNote,
                    ).joinToString(" · "),
                )
            }
            ReadingFreshness.DELAYED -> {
                val arrow = trendArrow(reading.trend)
                val last = "Last: ${listOfNotNull(value, arrow).joinToString(" ")} at $measuredAt"
                NotificationContent(
                    title = "$person · Reading delayed",
                    text = last,
                    bigText = listOfNotNull(
                        last,
                        trendDescription(reading.trend),
                        connectionNote,
                    ).joinToString("\n"),
                    statusChipText = "OLD",
                    prominent = "OLD",
                    compactDetail = "$last · $person",
                    expandedDetail = listOfNotNull(
                        "$last · $person",
                        trendDescription(reading.trend),
                        connectionNote,
                    ).joinToString(" · "),
                )
            }
            ReadingFreshness.STALE -> {
                val last = "Last: $value at $measuredAt"
                NotificationContent(
                    title = "$person · No recent reading",
                    text = last,
                    bigText = listOfNotNull(last, connectionNote).joinToString("\n"),
                    statusChipText = "STALE",
                    prominent = "STALE",
                    compactDetail = "$last · $person",
                    expandedDetail = listOfNotNull("$last · $person", connectionNote).joinToString(" · "),
                )
            }
            ReadingFreshness.INVALID_TIME -> {
                val last = "Last: $value at $measuredAt · time unverified"
                NotificationContent(
                    title = "$person · Reading time unavailable",
                    text = last,
                    bigText = listOfNotNull(last, connectionNote).joinToString("\n"),
                    statusChipText = "TIME?",
                    prominent = "TIME?",
                    compactDetail = "$last · $person",
                    expandedDetail = listOfNotNull("$last · $person", connectionNote).joinToString(" · "),
                )
            }
            ReadingFreshness.ABSENT -> absentContent(person, state.connection)
        }
    }

    private fun stoppedContent(
        person: String,
        reading: Reading?,
        unit: GlucoseUnit,
    ): NotificationContent {
        val last = reading?.let {
            "Last: ${formatValue(it, unit)} at ${timeFormatter.format(it.measuredAt)}"
        } ?: "No reading available"
        return NotificationContent(
            title = "$person · Monitoring stopped",
            text = last,
            bigText = "$last\nOpen Glance to start monitoring.",
            prominent = "STOPPED",
            compactDetail = "$person · $last",
        )
    }

    private fun absentContent(person: String, connection: ConnectionState): NotificationContent {
        val title: String
        val detail: String
        when (connection) {
            ConnectionState.SIGN_IN_REQUIRED -> {
                title = "$person · Sign-in required"
                detail = "Open Glance to reconnect."
            }
            ConnectionState.TERMS_REQUIRED -> {
                title = "$person · LibreLinkUp action required"
                detail = "Review the required terms in LibreLinkUp, then try again."
            }
            ConnectionState.CONNECTION_REMOVED -> {
                title = "$person · Shared person unavailable"
                detail = "Open Glance to choose an available shared person."
            }
            ConnectionState.PROTOCOL_ERROR -> {
                title = "$person · Reading unavailable"
                detail = "Glance could not understand the latest response."
            }
            ConnectionState.LOCAL_ERROR -> {
                title = "$person · Saving failed"
                detail = "Glance could not save local data. Open the app for details."
            }
            else -> {
                title = "$person · Waiting for first reading"
                detail = when (connection) {
                    ConnectionState.FETCHING -> "Fetching the latest reading."
                    ConnectionState.OFFLINE -> "No internet. Glance will retry when allowed."
                    ConnectionState.BACKING_OFF -> "Temporarily unable to refresh. Glance will retry later."
                    else -> "Monitoring is active."
                }
            }
        }
        val prominent = when (connection) {
            ConnectionState.SIGN_IN_REQUIRED -> "SIGN IN"
            ConnectionState.TERMS_REQUIRED -> "TERMS"
            ConnectionState.CONNECTION_REMOVED -> "NO SHARE"
            ConnectionState.PROTOCOL_ERROR, ConnectionState.LOCAL_ERROR -> "ERROR"
            else -> "WAITING"
        }
        return NotificationContent(
            title, detail, detail,
            prominent = prominent,
            compactDetail = "$person · $detail",
            expandedDetail = "$person · $detail",
        )
    }

    private fun formatValue(reading: Reading, unit: GlucoseUnit): String =
        GlucoseFormat.withUnit(reading, unit, locale)

    private fun recentDelta(current: Reading, history: List<Reading>, unit: GlucoseUnit): String? {
        val previous = history.asSequence()
            .filter {
                it.connectionId == current.connectionId &&
                    it.measuredAt.isBefore(current.measuredAt) &&
                    Duration.between(it.measuredAt, current.measuredAt) <= Duration.ofMinutes(15) &&
                    GlucoseFormat.numericValue(it, unit) != null
            }
            .maxByOrNull { it.measuredAt } ?: return null
        val currentValue = GlucoseFormat.numericValue(current, unit) ?: return null
        val previousValue = GlucoseFormat.numericValue(previous, unit) ?: return null
        val change = when (unit) {
            GlucoseUnit.MG_DL -> "%+d".format(locale, currentValue.roundToInt() - previousValue.roundToInt())
            GlucoseUnit.MMOL_L -> "%+.1f".format(
                locale,
                (currentValue.times(10).roundToInt() - previousValue.times(10).roundToInt()) / 10.0,
            )
        }
        return "($change)"
    }

    private fun trendArrow(trend: Trend): String? = when (trend) {
        Trend.RISING_QUICKLY -> "↑"
        Trend.RISING -> "↗"
        Trend.RISING_SLOWLY -> "↗"
        Trend.STEADY -> "→"
        Trend.FALLING_SLOWLY -> "↘"
        Trend.FALLING -> "↘"
        Trend.FALLING_QUICKLY -> "↓"
        Trend.UNKNOWN -> null
    }

    private fun trendDescription(trend: Trend): String? = when (trend) {
        Trend.RISING_QUICKLY -> "Trend: rising quickly"
        Trend.RISING -> "Trend: rising"
        Trend.RISING_SLOWLY -> "Trend: rising slowly"
        Trend.STEADY -> "Trend: steady"
        Trend.FALLING_SLOWLY -> "Trend: falling slowly"
        Trend.FALLING -> "Trend: falling"
        Trend.FALLING_QUICKLY -> "Trend: falling quickly"
        Trend.UNKNOWN -> null
    }

    private fun connectionNote(connection: ConnectionState): String? = when (connection) {
        ConnectionState.OFFLINE -> "No internet. Showing the last reading."
        ConnectionState.BACKING_OFF -> "Temporarily unable to refresh. Showing the last reading."
        ConnectionState.SIGN_IN_REQUIRED -> "Sign-in required. Showing the last reading."
        ConnectionState.TERMS_REQUIRED -> "LibreLinkUp action required. Showing the last reading."
        ConnectionState.CONNECTION_REMOVED -> "Shared person unavailable. Showing the last reading."
        ConnectionState.PROTOCOL_ERROR -> "Response unavailable. Showing the last reading."
        ConnectionState.LOCAL_ERROR -> "Glance could not save local data. Showing the last saved reading."
        ConnectionState.READY, ConnectionState.FETCHING -> null
    }

}
