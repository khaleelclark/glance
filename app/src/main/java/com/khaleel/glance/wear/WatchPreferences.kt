package com.khaleel.glance.wear

import android.content.Context
import java.io.IOException

internal interface WatchGrantStorage {
    fun read(): Boolean
    fun write(visible: Boolean): Boolean
    fun remove(): Boolean
}

internal class WatchGrant(private val storage: WatchGrantStorage) {
    private var blocked = false
    var visible: Boolean
        @Synchronized get() = !blocked && runCatching { storage.read() }.getOrDefault(false)
        @Synchronized set(value) {
            blocked = true
            if (runCatching { storage.write(value) }.getOrDefault(false)) {
                blocked = !value
                return
            }
            if (!value && runCatching { storage.remove() }.getOrDefault(false)) return
            throw IOException("Could not save watch sharing")
        }
}

/** Watch-face privacy is independent of the phone's lock-screen and widget grants. */
class WatchPreferences(context: Context) {
    private val grant = WatchGrant(object : WatchGrantStorage {
        private fun prefs() = context.getSharedPreferences("watch_sharing", Context.MODE_PRIVATE)
        override fun read() = prefs().getBoolean("visible", false)
        override fun write(visible: Boolean) = prefs().edit().putBoolean("visible", visible).commit()
        override fun remove() = context.deleteSharedPreferences("watch_sharing")
    })
    private val notificationPrefs = context.getSharedPreferences("watch_notifications", Context.MODE_PRIVATE)
    private var acceptedNotifications = runCatching { notificationPrefs.getBoolean("enabled", false) }.getOrDefault(false)
    var valuesVisible: Boolean
        get() = grant.visible
        set(value) { grant.visible = value }
    var notificationsEnabled: Boolean
        @Synchronized get() = acceptedNotifications
        @Synchronized set(value) {
            if (!notificationPrefs.edit().putBoolean("enabled", value).commit())
                throw IOException("Could not save watch notifications")
            acceptedNotifications = value
        }
}
