package com.khaleel.glance.wear.data

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.khaleel.glance.wear.shared.WatchDataMap
import com.khaleel.glance.wear.shared.WatchSnapshot

class GlanceDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        runCatching {
            dataEvents.forEach { event ->
                val item = event.dataItem
                if (item.uri.path != WatchSnapshot.PATH) return@forEach
                val source = item.uri.host ?: return@forEach
                when (event.type) {
                    DataEvent.TYPE_CHANGED -> {
                        val snapshot = runCatching {
                            WatchDataMap.decode(DataMapItem.fromDataItem(item).dataMap)
                        }.getOrNull() ?: return@forEach
                        WatchRuntime.applySnapshot(this, SnapshotEnvelope(source, snapshot))
                    }
                    DataEvent.TYPE_DELETED -> WatchRuntime.applyDeletion(this, source)
                }
            }
        }
    }
}
