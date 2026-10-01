package com.khaleel.glance.notification

import android.content.Context
import java.io.IOException

internal interface LockScreenPrivacyStorage {
    fun read(): Boolean
    fun write(value: Boolean): Boolean
    fun remove(): Boolean
}

/** A failed write never grants visibility; deleting this grant can durably revoke it. */
internal class LockScreenVisibility(private val storage: LockScreenPrivacyStorage) {
    private var blocked = false
    var visible: Boolean
        @Synchronized get() = !blocked && runCatching { storage.read() }.getOrDefault(false)
        @Synchronized set(value) {
            blocked = true
            if (runCatching { storage.write(value) }.getOrDefault(false)) {
                blocked = !value
                return
            }
            val removed = runCatching { storage.remove() }.getOrDefault(false)
            if (!value && removed) return
            throw IOException("Could not persist lock-screen privacy")
        }
}

/** Separate grant: legacy lock_visible is never imported again after a failed revocation. */
class LockScreenPrivacy(context: Context) {
    private val visibility = LockScreenVisibility(object : LockScreenPrivacyStorage {
        private fun preferences() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        override fun read() = preferences().getBoolean("values_visible", false)
        override fun write(value: Boolean) = preferences().edit().putBoolean("values_visible", value).commit()
        override fun remove() = context.deleteSharedPreferences(PREFS)
    })
    var visible: Boolean
        get() = visibility.visible
        set(value) { visibility.visible = value }

    companion object { const val PREFS = "lock_screen_privacy" }
}
