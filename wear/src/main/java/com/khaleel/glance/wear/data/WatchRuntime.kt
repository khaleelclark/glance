package com.khaleel.glance.wear.data

import android.content.Context
import android.annotation.SuppressLint
import com.khaleel.glance.wear.complication.ComplicationPublisher
import com.khaleel.glance.wear.notification.WatchNotificationController
import com.khaleel.glance.wear.shared.WatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ApplyResult { ACCEPTED, INVALID, WRONG_SOURCE, OLD_REVISION, PERSIST_FAILED }

/**
 * The monitor covers persistence plus notification/complication publication. An older callback
 * therefore cannot run publication after a newer tombstone has committed.
 */
object WatchRuntime {
    private val monitor = Any()
    private val mutableState = MutableStateFlow(WatchCacheState())
    val state: StateFlow<WatchCacheState> = mutableState.asStateFlow()

    @Volatile private var initialized = false
    @SuppressLint("StaticFieldLeak") // Store is initialized with applicationContext only.
    private lateinit var store: WatchSnapshotStore

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(monitor) {
            if (initialized) return
            store = WatchSnapshotStore(context.applicationContext)
            mutableState.value = store.load()
            initialized = true
            publishLocked(context.applicationContext, tombstone = mutableState.value.revoked)
        }
    }

    fun applySnapshot(
        context: Context,
        incoming: SnapshotEnvelope,
        allowSourceSwitch: Boolean = false,
    ): ApplyResult = synchronized(monitor) {
        initialize(context)
        val reduction = WatchCacheReducer.reduce(mutableState.value, incoming, allowSourceSwitch)
        val rejected = when (reduction.decision) {
            ReductionDecision.INVALID -> ApplyResult.INVALID
            ReductionDecision.WRONG_SOURCE -> ApplyResult.WRONG_SOURCE
            ReductionDecision.OLD_REVISION -> ApplyResult.OLD_REVISION
            ReductionDecision.ACCEPTED -> null
        }
        if (rejected != null) return@synchronized rejected
        if (!store.commit(reduction.state)) {
            failClosedLocked(context, reduction.state)
            return@synchronized ApplyResult.PERSIST_FAILED
        }
        mutableState.value = reduction.state
        val tombstone = incoming.snapshot.status == WatchStatus.HIDDEN ||
            incoming.snapshot.status == WatchStatus.STOPPED
        publishLocked(context.applicationContext, tombstone)
        ApplyResult.ACCEPTED
    }

    fun applyInitialQuery(
        context: Context,
        candidates: List<SnapshotEnvelope>,
        nearbyNodes: Set<String>,
    ): ApplyResult? = synchronized(monitor) {
        initialize(context)
        val currentSource = mutableState.value.sourceNode
        val selected = PhoneSnapshotSelector.select(candidates, currentSource, nearbyNodes)
            ?: return@synchronized null
        val switch = currentSource != null && selected.sourceNode != currentSource &&
            candidates.none { it.sourceNode == currentSource && it.snapshot.valid() }
        applySnapshot(context, selected, allowSourceSwitch = switch)
    }

    fun applyDeletion(context: Context, sourceNode: String): ApplyResult = synchronized(monitor) {
        initialize(context)
        val current = mutableState.value
        if (current.sourceNode != sourceNode) return@synchronized ApplyResult.WRONG_SOURCE
        val cleared = current.copy(snapshot = null, revoked = true)
        if (!store.commit(cleared)) {
            failClosedLocked(context, cleared)
            return@synchronized ApplyResult.PERSIST_FAILED
        }
        mutableState.value = cleared
        publishLocked(context.applicationContext, tombstone = true)
        ApplyResult.ACCEPTED
    }

    fun setWatchNotificationOptIn(context: Context, enabled: Boolean): Boolean = synchronized(monitor) {
        initialize(context)
        val changed = mutableState.value.copy(watchNotificationsOptIn = enabled)
        if (!store.commit(changed)) {
            failClosedLocked(context, changed)
            return@synchronized false
        }
        mutableState.value = changed
        publishLocked(context.applicationContext, tombstone = false)
        true
    }

    private fun failClosedLocked(context: Context, attempted: WatchCacheState) {
        val hidden = WatchCacheReducer.failedPersistence(attempted)
        mutableState.value = hidden
        if (!store.commit(hidden)) store.remove()
        publishLocked(context.applicationContext, tombstone = true)
    }

    private fun publishLocked(context: Context, tombstone: Boolean) {
        runCatching { WatchNotificationController(context).update(mutableState.value, System.currentTimeMillis()) }
        val now = System.currentTimeMillis()
        val current = mutableState.value
        if (ComplicationPushPolicy.shouldPush(current.lastComplicationPushMillis, now, tombstone)) {
            val timestamped = current.copy(lastComplicationPushMillis = now)
            if (store.commit(timestamped)) {
                mutableState.value = timestamped
            }
            // Privacy clearing must still be requested when cache persistence fails.
            runCatching { ComplicationPublisher.requestUpdate(context) }
        }
    }

}

object ComplicationPushPolicy {
    const val INTERVAL_MILLIS = 5 * 60 * 1000L

    fun shouldPush(lastPushMillis: Long, nowMillis: Long, tombstone: Boolean): Boolean =
        tombstone || lastPushMillis <= 0 ||
            (nowMillis >= lastPushMillis && nowMillis - lastPushMillis >= INTERVAL_MILLIS)
}
