package com.khaleel.glance.wear.data

import android.content.Context
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.khaleel.glance.wear.shared.WatchDataMap
import com.khaleel.glance.wear.shared.WatchSnapshot

object WearDataSync {
    /** Best-effort cache refresh. All Play services failure paths terminate without throwing. */
    fun query(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            Wearable.getDataClient(appContext).dataItems
                .addOnSuccessListener { buffer ->
                    val candidates = try {
                        buffer.mapNotNull { item ->
                            if (item.uri.path != WatchSnapshot.PATH) return@mapNotNull null
                            val source = item.uri.host ?: return@mapNotNull null
                            val snapshot = runCatching {
                                WatchDataMap.decode(DataMapItem.fromDataItem(item).dataMap)
                            }.getOrNull() ?: return@mapNotNull null
                            SnapshotEnvelope(source, snapshot)
                        }
                    } finally {
                        buffer.release()
                    }
                    queryNearbyAndApply(appContext, candidates)
                }
                .addOnFailureListener { /* Status remains cache-driven and fail-closed. */ }
        }
    }

    private fun queryNearbyAndApply(context: Context, candidates: List<SnapshotEnvelope>) {
        runCatching {
            Wearable.getNodeClient(context).connectedNodes
                .addOnCompleteListener { task ->
                    val nearby = if (task.isSuccessful) {
                        task.result.orEmpty().filter { it.isNearby }.mapTo(mutableSetOf()) { it.id }
                    } else emptySet()
                    runCatching { WatchRuntime.applyInitialQuery(context, candidates, nearby) }
                }
        }.onFailure {
            runCatching { WatchRuntime.applyInitialQuery(context, candidates, emptySet()) }
        }
    }
}
