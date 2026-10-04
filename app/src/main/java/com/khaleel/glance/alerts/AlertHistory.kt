package com.khaleel.glance.alerts

import com.khaleel.glance.core.Connection
import com.khaleel.glance.core.Reading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** User-visible local health data, never part of a redacted diagnostic export. */
data class AlertHistoryEntry(
    val id: String,
    val person: Connection,
    val kind: AlertKind,
    val reading: Reading,
    val firedAt: Instant,
)

interface AlertHistoryPersistence {
    val events: StateFlow<List<AlertHistoryEntry>>
    fun append(entry: AlertHistoryEntry)
    fun clear()
}

object NoAlertHistory : AlertHistoryPersistence {
    override val events: StateFlow<List<AlertHistoryEntry>> = MutableStateFlow(emptyList())
    override fun append(entry: AlertHistoryEntry) = Unit
    override fun clear() = Unit
}
