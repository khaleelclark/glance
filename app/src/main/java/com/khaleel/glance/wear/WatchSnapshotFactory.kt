package com.khaleel.glance.wear

import com.khaleel.glance.core.*
import com.khaleel.glance.core.GlucoseFormat.label
import com.khaleel.glance.wear.shared.WatchSnapshot
import com.khaleel.glance.wear.shared.WatchStatus
import java.time.Instant
import java.util.Locale

internal object WatchSnapshotFactory {
    fun create(state: DisplayState, hasAccount: Boolean, visible: Boolean, notifications: Boolean,
               unit: GlucoseUnit, revision: Long, now: Instant): WatchSnapshot {
        fun status(value: WatchStatus) = WatchSnapshot(revision, now.toEpochMilli(), value)
        if (!visible || !hasAccount) return status(WatchStatus.HIDDEN)
        if (!state.monitoringEnabled || state.runtime == RuntimeState.STOPPED) return status(WatchStatus.STOPPED)
        if (state.runtime != RuntimeState.RUNNING || state.person == null) return status(WatchStatus.WAITING)
        if (state.connection in setOf(ConnectionState.SIGN_IN_REQUIRED, ConnectionState.TERMS_REQUIRED,
                ConnectionState.CONNECTION_REMOVED, ConnectionState.PROTOCOL_ERROR, ConnectionState.LOCAL_ERROR))
            return status(WatchStatus.ACTION_REQUIRED)
        val reading = state.reading?.takeIf { it.connectionId == state.person.id }
            ?: return status(WatchStatus.WAITING)
        val numeric = GlucoseFormat.numericValue(reading, unit)
        if (numeric == null && reading.specialValue !in setOf(SpecialValue.LOW, SpecialValue.HIGH))
            return status(WatchStatus.WAITING)
        val arrow = if (numeric == null) "" else when (reading.trend) {
            Trend.RISING_QUICKLY -> "↑"
            Trend.RISING, Trend.RISING_SLOWLY -> "↗"
            Trend.STEADY -> "→"
            Trend.FALLING, Trend.FALLING_SLOWLY -> "↘"
            Trend.FALLING_QUICKLY -> "↓"
            Trend.UNKNOWN -> ""
        }
        val trend = if (numeric == null) "" else when (reading.trend) {
            Trend.RISING_QUICKLY -> "Rising quickly"
            Trend.RISING -> "Rising"
            Trend.RISING_SLOWLY -> "Rising slowly"
            Trend.STEADY -> "Steady"
            Trend.FALLING_SLOWLY -> "Falling slowly"
            Trend.FALLING -> "Falling"
            Trend.FALLING_QUICKLY -> "Falling quickly"
            Trend.UNKNOWN -> "Trend unavailable"
        }
        return WatchSnapshot(revision, now.toEpochMilli(), WatchStatus.READING,
            reading.measuredAt.toEpochMilli(), GlucoseFormat.value(reading, unit, Locale.US),
            if (numeric == null) "" else unit.label, arrow, trend, notifications).let {
                if (it.valid()) it else status(WatchStatus.WAITING)
            }
    }
}
