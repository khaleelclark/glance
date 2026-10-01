package com.khaleel.glance.wear.data

import com.khaleel.glance.wear.shared.WatchSnapshot
import com.khaleel.glance.wear.shared.WatchStatus

data class SnapshotEnvelope(
    val sourceNode: String,
    val snapshot: WatchSnapshot,
)

data class WatchCacheState(
    val sourceNode: String? = null,
    val lastRevision: Long = 0,
    val snapshot: WatchSnapshot? = null,
    val revoked: Boolean = false,
    val watchNotificationsOptIn: Boolean = false,
    val lastComplicationPushMillis: Long = 0,
)

enum class ReductionDecision {
    ACCEPTED,
    INVALID,
    WRONG_SOURCE,
    OLD_REVISION,
}

data class Reduction(
    val decision: ReductionDecision,
    val state: WatchCacheState,
)

object WatchCacheReducer {
    fun failedPersistence(attempted: WatchCacheState): WatchCacheState =
        attempted.copy(snapshot = null, revoked = true, watchNotificationsOptIn = false)

    fun reduce(
        current: WatchCacheState,
        incoming: SnapshotEnvelope,
        allowSourceSwitch: Boolean,
    ): Reduction {
        val candidate = incoming.snapshot
        if (incoming.sourceNode.isBlank() || !candidate.valid()) {
            return Reduction(ReductionDecision.INVALID, current)
        }
        val sameSource = current.sourceNode == null || current.sourceNode == incoming.sourceNode
        if (!sameSource && !allowSourceSwitch) {
            return Reduction(ReductionDecision.WRONG_SOURCE, current)
        }
        if (sameSource && candidate.revision <= current.lastRevision) {
            return Reduction(ReductionDecision.OLD_REVISION, current)
        }
        val revoked = candidate.status == WatchStatus.HIDDEN || candidate.status == WatchStatus.STOPPED
        return Reduction(
            ReductionDecision.ACCEPTED,
            current.copy(
                sourceNode = incoming.sourceNode,
                lastRevision = candidate.revision,
                snapshot = candidate,
                revoked = revoked,
            ),
        )
    }
}

/** Chooses one sticky remote source. This app never creates DataItems itself. */
object PhoneSnapshotSelector {
    fun select(
        candidates: List<SnapshotEnvelope>,
        preferredSource: String?,
        nearbyNodes: Set<String>,
    ): SnapshotEnvelope? {
        val valid = candidates.filter { it.sourceNode.isNotBlank() && it.snapshot.valid() }
        if (valid.isEmpty()) return null
        fun newest(items: List<SnapshotEnvelope>) = items.maxWithOrNull(
            compareBy<SnapshotEnvelope>({ it.snapshot.sentAtMillis }, { it.snapshot.revision }, { it.sourceNode }),
        )
        preferredSource?.let { source ->
            newest(valid.filter { it.sourceNode == source })?.let { return it }
        }
        newest(valid.filter { it.sourceNode in nearbyNodes })?.let { return it }
        return newest(valid)
    }
}
