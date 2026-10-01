package com.khaleel.glance.wear

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.khaleel.glance.data.AppController
import com.khaleel.glance.wear.shared.WatchDataMap
import com.khaleel.glance.wear.shared.WatchSnapshot
import com.khaleel.glance.wear.shared.WatchStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.time.Instant

/** One serial outbox: a queued old render never follows a revocation on the same DataItem. */
class WatchPublisher(context: Context, private val controller: AppController,
                     private val preferences: WatchPreferences) {
    private val context = context.applicationContext
    private val lock = Any()
    private val revisionPrefs = context.getSharedPreferences("watch_transport", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var latest: WatchSnapshot? = null
    @Volatile var syncStatus: String = "Waiting for watch sync"
        private set

    init {
        scope.launch {
            for (ignored in signal) {
                val snapshot = synchronized(lock) { latest } ?: continue
                // Tasks completes after Play services accepts the local DataItem. Await it before
                // posting the next revision, including an urgent privacy/Stop tombstone.
                runCatching {
                    val request = PutDataMapRequest.create(WatchSnapshot.PATH).apply {
                        dataMap.putAll(WatchDataMap.encode(snapshot))
                    }.asPutDataRequest().setUrgent()
                    Tasks.await(Wearable.getDataClient(context).putDataItem(request))
                }.onSuccess {
                    syncStatus = "Queued to paired watch; delivery depends on connection"
                }.onFailure {
                    syncStatus = "Watch sync unavailable; check pairing and Google Play services"
                }
            }
        }
    }

    fun refresh() = synchronized(lock) {
        enqueue { revision, now -> WatchSnapshotFactory.create(controller.display.value,
            controller.hasAccount, preferences.valuesVisible, preferences.notificationsEnabled,
            controller.settings.unit, revision, now) }
    }

    fun redact() = synchronized(lock) {
        enqueue { revision, now -> WatchSnapshot(revision, now.toEpochMilli(), WatchStatus.HIDDEN) }
    }

    /** Changes the grant and replaces pending content while holding the outbox monitor. */
    fun setValuesVisible(visible: Boolean) = synchronized(lock) {
        try { preferences.valuesVisible = visible }
        finally { refresh() }
    }

    fun setNotificationsEnabled(enabled: Boolean) = synchronized(lock) {
        preferences.notificationsEnabled = enabled
        refresh()
    }

    private fun enqueue(build: (Long, Instant) -> WatchSnapshot) {
        val now = Instant.now()
        val revision = runCatching {
            maxOf(revisionPrefs.getLong("revision", 0L) + 1L, now.toEpochMilli())
        }.getOrNull()
        if (revision != null) {
            val candidate = build(revision, now)
            val previous = latest
            if (previous != null && previous.copy(revision = candidate.revision,
                    sentAtMillis = candidate.sentAtMillis) == candidate &&
                (candidate.status == WatchStatus.HIDDEN || candidate.status == WatchStatus.STOPPED ||
                    candidate.sentAtMillis - previous.sentAtMillis < 60_000L)) return
        }
        // A failed revision write must not grant a future out-of-order health-data publication.
        if (revision == null || !runCatching {
                revisionPrefs.edit().putLong("revision", revision).commit()
            }.getOrDefault(false)) {
            latest = null
            syncStatus = "Could not save watch sync state; sharing paused"
            return
        }
        latest = build(revision, now)
        signal.trySend(Unit)
    }
}
