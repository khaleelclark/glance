package com.khaleel.glance.alerts

import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SourceResult

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
}

/** Evaluates only successful polls whose result was actually published for the selected person. */
class AlertEngine(
    private val preferences: AlertStore,
    private val notifier: AlertDelivery,
    private val clock: Clock,
    private val currentDisplay: () -> DisplayState,
    private val currentPrivacy: () -> Boolean,
) {
    @Synchronized
    fun onPollResult(
        result: SourceResult<Reading>,
        display: DisplayState,
        unit: GlucoseUnit,
        showOnLockScreen: Boolean,
    ) {
        val returned = (result as? SourceResult.Success)?.value ?: return
        val current = display.reading ?: return
        if (display.person?.id != returned.connectionId ||
            current.connectionId != returned.connectionId ||
            current.measuredAt != returned.measuredAt ||
            current.valueMgDl != returned.valueMgDl ||
            current.specialValue != returned.specialValue
        ) return
        val live = currentDisplay()
        if (!live.monitoringEnabled || live.person?.id != returned.connectionId ||
            live.reading?.measuredAt != current.measuredAt ||
            live.reading?.valueMgDl != current.valueMgDl
        ) return

        val prior = preferences.snapshot()
        val evaluation = AlertPolicy.evaluate(preferences.config(), prior, display, clock.now())
        if (evaluation.snapshot == prior) return
        val kind = evaluation.kind
        if (kind != null) notifier.ensureChannels()
        val deliver = kind != null && notifier.canPost(kind)
        val toSave = if (kind != null && !deliver) {
            // A blocked channel did not fire; preserve its previous cooldown deadline.
            evaluation.snapshot.copy(lastFiredAt = prior.lastFiredAt)
        } else evaluation.snapshot
        preferences.saveSnapshot(toSave)
        if (kind != null && deliver) notifier.post(kind, display, unit, showOnLockScreen && currentPrivacy())
        else if (evaluation.clearExisting) notifier.clear()
    }

    @Synchronized
    fun clearNotifications() = notifier.clear()
}
