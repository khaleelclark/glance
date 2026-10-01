package com.khaleel.glance.wear.shared

import com.google.android.gms.wearable.DataMap

object WatchDataMap {
    fun encode(snapshot: WatchSnapshot): DataMap = DataMap().apply {
        require(snapshot.valid())
        putInt("schema", WatchSnapshot.SCHEMA)
        putLong("revision", snapshot.revision)
        putLong("sent_at", snapshot.sentAtMillis)
        putString("status", snapshot.status.name)
        putLong("measured_at", snapshot.measuredAtMillis)
        putString("value", snapshot.value)
        putString("unit", snapshot.unit)
        putString("arrow", snapshot.arrow)
        putString("trend", snapshot.trend)
        putBoolean("notifications", snapshot.notificationsEnabled)
    }

    fun decode(data: DataMap): WatchSnapshot? = runCatching {
        if (data.getInt("schema") != WatchSnapshot.SCHEMA) return null
        WatchSnapshot(
            revision = data.getLong("revision"), sentAtMillis = data.getLong("sent_at"),
            status = WatchStatus.valueOf(data.getString("status") ?: return null),
            measuredAtMillis = data.getLong("measured_at"),
            value = data.getString("value") ?: "", unit = data.getString("unit") ?: "",
            arrow = data.getString("arrow") ?: "", trend = data.getString("trend") ?: "",
            notificationsEnabled = data.getBoolean("notifications"),
        ).takeIf { it.valid() }
    }.getOrNull()
}
