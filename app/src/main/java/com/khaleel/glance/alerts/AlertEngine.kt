package com.khaleel.glance.alerts

import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SourceResult
import com.khaleel.glance.core.forPerson
import java.util.UUID

interface AlertStore {
    fun config(): AlertConfig
    fun snapshot(): AlertSnapshot
    fun saveSnapshot(snapshot: AlertSnapshot)
}

interface AlertDelivery {
    fun ensureChannels()
    fun canPost(kind: AlertKind): Boolean
    fun post(kind: AlertKind, display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean)
    fun clear()
    fun clearPerson(personId: String) = clear()
}

/** Evaluates only successful polls whose result was actually published for the selected person. */
class AlertEngine(
    private val preferences: AlertStore,
    private val notifier: AlertDelivery,
    private val clock: Clock,
    private val currentDisplay: () -> DisplayState,
    private val currentPrivacy: () -> Boolean,
    private val history: AlertHistoryPersistence = NoAlertHistory,
    private val personStore: (String) -> AlertStore = { preferences },
    private val currentPersonDisplay: (String) -> DisplayState? = { personId ->
        currentDisplay().forPerson(personId)
    },
) {
    @Synchronized
    fun onPollResult(
        result: SourceResult<Reading>,
        display: DisplayState,
        unit: GlucoseUnit,
        showOnLockScreen: Boolean,
    ) {
        val returned = (result as? SourceResult.Success)?.value ?: return
        if (!matchesPublished(display, returned)) return
        val live = currentPersonDisplay(returned.connectionId) ?: return
        if (!matchesPublished(live, returned)) return

        val store = personStore(returned.connectionId)
        val prior = store.snapshot()
        val firedAt = clock.now()
        val evaluation = AlertPolicy.evaluate(store.config(), prior, live, firedAt)
        if (evaluation.snapshot == prior) return
        val kind = evaluation.kind
        if (kind != null) notifier.ensureChannels()
        val deliver = kind != null && notifier.canPost(kind)
        val toSave = if (kind != null && !deliver) {
            // A blocked channel did not fire; preserve its previous cooldown deadline.
            evaluation.snapshot.copy(lastFiredAt = prior.lastFiredAt)
        } else evaluation.snapshot
        store.saveSnapshot(toSave)
        if (kind != null && deliver) {
            try {
                notifier.post(kind, live, unit, showOnLockScreen && currentPrivacy())
            } catch (error: Exception) {
                // The sample stays consumed, but a failed post must not start a false cooldown.
                runCatching {
                    store.saveSnapshot(evaluation.snapshot.copy(lastFiredAt = prior.lastFiredAt))
                }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
            val person = live.person ?: return
            val reading = live.reading ?: return
            // A delivered alert must not be repeated merely because its optional audit write failed.
            runCatching {
                history.append(AlertHistoryEntry(UUID.randomUUID().toString(), person, kind, reading, firedAt))
            }
        } else if (evaluation.clearExisting) {
            notifier.clearPerson(returned.connectionId)
        }
    }

    private fun matchesPublished(display: DisplayState, returned: Reading): Boolean {
        val current = display.reading ?: return false
        return display.monitoringEnabled &&
            display.runtime == com.khaleel.glance.core.RuntimeState.RUNNING &&
            display.person?.id == returned.connectionId &&
            current.connectionId == returned.connectionId &&
            current.measuredAt == returned.measuredAt &&
            current.valueMgDl == returned.valueMgDl &&
            current.specialValue == returned.specialValue &&
            current.sourceRecordId == returned.sourceRecordId
    }

    @Synchronized
    fun clearNotifications() = notifier.clear()

    /** A lock-screen choice must not change after an alert's final privacy check. */
    @Synchronized
    fun changePrivacy(updateChoice: () -> Unit) {
        try { updateChoice() } finally { notifier.clear() }
    }
}
