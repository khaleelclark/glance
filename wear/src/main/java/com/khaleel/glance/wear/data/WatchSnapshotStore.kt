package com.khaleel.glance.wear.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.khaleel.glance.wear.shared.WatchSnapshot
import com.khaleel.glance.wear.shared.WatchStatus

class WatchSnapshotStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): WatchCacheState {
        val source = preferences.getString(KEY_SOURCE, null)?.takeIf { it.isNotBlank() }
        val revision = preferences.getLong(KEY_REVISION, 0)
        val hasSnapshot = preferences.getBoolean(KEY_HAS_SNAPSHOT, false)
        val snapshot = if (hasSnapshot) loadSnapshot() else null
        return WatchCacheState(
            sourceNode = source,
            lastRevision = revision.coerceAtLeast(0),
            snapshot = snapshot,
            revoked = preferences.getBoolean(KEY_REVOKED, false) || (hasSnapshot && snapshot == null),
            watchNotificationsOptIn = preferences.getBoolean(KEY_NOTIFICATION_OPT_IN, false),
            lastComplicationPushMillis = preferences.getLong(KEY_LAST_COMPLICATION_PUSH, 0).coerceAtLeast(0),
        )
    }

    /** Synchronous checked commit: callers must not publish state when this returns false. */
    @SuppressLint("UseKtx") // The synchronous result is part of the fail-closed publication contract.
    fun commit(state: WatchCacheState): Boolean {
        val editor = preferences.edit()
            .putLong(KEY_REVISION, state.lastRevision)
            .putBoolean(KEY_REVOKED, state.revoked)
            .putBoolean(KEY_NOTIFICATION_OPT_IN, state.watchNotificationsOptIn)
            .putLong(KEY_LAST_COMPLICATION_PUSH, state.lastComplicationPushMillis)
        if (state.sourceNode == null) editor.remove(KEY_SOURCE) else editor.putString(KEY_SOURCE, state.sourceNode)
        val snapshot = state.snapshot
        editor.putBoolean(KEY_HAS_SNAPSHOT, snapshot != null)
        if (snapshot == null) {
            SNAPSHOT_KEYS.forEach(editor::remove)
        } else {
            editor.putLong(KEY_SENT_AT, snapshot.sentAtMillis)
                .putString(KEY_STATUS, snapshot.status.name)
                .putLong(KEY_MEASURED_AT, snapshot.measuredAtMillis)
                .putString(KEY_VALUE, snapshot.value)
                .putString(KEY_UNIT, snapshot.unit)
                .putString(KEY_ARROW, snapshot.arrow)
                .putString(KEY_TREND, snapshot.trend)
                .putBoolean(KEY_PHONE_NOTIFICATIONS, snapshot.notificationsEnabled)
        }
        return editor.commit()
    }

    private fun loadSnapshot(): WatchSnapshot? = runCatching {
        WatchSnapshot(
            revision = preferences.getLong(KEY_REVISION, 0),
            sentAtMillis = preferences.getLong(KEY_SENT_AT, 0),
            status = WatchStatus.valueOf(preferences.getString(KEY_STATUS, "") ?: ""),
            measuredAtMillis = preferences.getLong(KEY_MEASURED_AT, 0),
            value = preferences.getString(KEY_VALUE, "") ?: "",
            unit = preferences.getString(KEY_UNIT, "") ?: "",
            arrow = preferences.getString(KEY_ARROW, "") ?: "",
            trend = preferences.getString(KEY_TREND, "") ?: "",
            notificationsEnabled = preferences.getBoolean(KEY_PHONE_NOTIFICATIONS, false),
        ).takeIf { it.valid() }
    }.getOrNull()

    private companion object {
        const val FILE_NAME = "watch_snapshot_v1"
        const val KEY_SOURCE = "source_node"
        const val KEY_REVISION = "revision"
        const val KEY_HAS_SNAPSHOT = "has_snapshot"
        const val KEY_REVOKED = "revoked"
        const val KEY_NOTIFICATION_OPT_IN = "watch_notifications_opt_in"
        const val KEY_LAST_COMPLICATION_PUSH = "last_complication_push"
        const val KEY_SENT_AT = "sent_at"
        const val KEY_STATUS = "status"
        const val KEY_MEASURED_AT = "measured_at"
        const val KEY_VALUE = "value"
        const val KEY_UNIT = "unit"
        const val KEY_ARROW = "arrow"
        const val KEY_TREND = "trend"
        const val KEY_PHONE_NOTIFICATIONS = "phone_notifications"
        val SNAPSHOT_KEYS = listOf(
            KEY_SENT_AT, KEY_STATUS, KEY_MEASURED_AT, KEY_VALUE, KEY_UNIT,
            KEY_ARROW, KEY_TREND, KEY_PHONE_NOTIFICATIONS,
        )
    }
}
