package com.khaleel.glance.alerts

import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.core.SourceResult
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
    fun refreshIcon(display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) = Unit
    fun dismiss(token: String) = Unit
    fun clear()
}

/** Evaluates only successful polls whose result was actually published for the selected person. */
class AlertEngine(
    private val preferences: AlertStore,
    private val notifier: AlertDelivery,
    private val clock: Clock,
    private val currentDisplay: () -> DisplayState,
    private val currentPrivacy: () -> Boolean,
    private val history: AlertHistoryPersistence = NoAlertHistory,
) {
    @Synchronized
    fun onPollResult(
        result: SourceResult<Reading>,
        display: DisplayState,
        unit: GlucoseUnit,
        showOnLockScreen: Boolean,
    ) {
        val returned = (result as? SourceResult.Success)?.value ?: return
        val person = display.person ?: return
        val current = display.reading ?: return
        if (person.id != returned.connectionId ||
            current.connectionId != returned.connectionId ||
            current.measuredAt != returned.measuredAt ||
            current.valueMgDl != returned.valueMgDl ||
            current.specialValue != returned.specialValue
        ) return
        val live = currentDisplay()
        val livePerson = live.person ?: return
        val liveReading = live.reading ?: return
        if (!live.monitoringEnabled || live.runtime != RuntimeState.RUNNING ||
            live.freshness != ReadingFreshness.FRESH ||
            livePerson.id != returned.connectionId ||
            liveReading.connectionId != returned.connectionId ||
            liveReading.measuredAt != current.measuredAt ||
            liveReading.valueMgDl != current.valueMgDl ||
            liveReading.specialValue != current.specialValue
        ) return

        val prior = preferences.snapshot()
        val firedAt = clock.now()
        val evaluation = AlertPolicy.evaluate(preferences.config(), prior, live, firedAt)
        if (evaluation.snapshot == prior) return
        val kind = evaluation.kind
        if (kind != null) notifier.ensureChannels()
        val deliver = kind != null && notifier.canPost(kind)
        val toSave = if (kind != null && !deliver) {
            // A blocked channel did not fire; preserve its previous cooldown deadline.
            evaluation.snapshot.copy(lastFiredAt = prior.lastFiredAt)
        } else evaluation.snapshot
        preferences.saveSnapshot(toSave)
        if (kind != null && deliver) {
            notifier.post(kind, live, unit, showOnLockScreen && currentPrivacy())
            history.append(
                AlertHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    person = livePerson,
                    kind = kind,
                    reading = liveReading,
                    firedAt = clock.now(),
                ),
            )
        } else if (evaluation.clearExisting) {
            notifier.clear()
        }
    }

    @Synchronized
    fun clearNotifications() = notifier.clear()

    /** Update only an already-visible alert; do not evaluate thresholds or reset cooldowns. */
    @Synchronized
    fun refreshIcons(display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) {
        if (currentDisplay() != display) return
        notifier.refreshIcon(display, unit, showOnLockScreen && currentPrivacy())
    }

    @Synchronized
    fun dismissNotification(token: String) = notifier.dismiss(token)

    /** Clears only the visible alarm ledger; alert deduplication and cooldowns remain intact. */
    @Synchronized
    fun clearHistory() = history.clear()

    /** A lock-screen choice must not change after an alert's final privacy check. */
    @Synchronized
    fun changePrivacy(updateChoice: () -> Unit) {
        try { updateChoice() } finally { notifier.clear() }
    }
}
