package com.khaleel.glance.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.khaleel.glance.GlanceApplication

/** Applies the owner's retention choice; never restarts a stopped monitoring session. */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val generation = intent.getLongExtra(EXTRA_GENERATION, Long.MIN_VALUE)
        val app = context.applicationContext as? GlanceApplication ?: return
        app.onNotificationDismissed(generation)
    }

    companion object {
        const val ACTION_DISMISSED = "com.khaleel.glance.notification.DISMISSED"
        const val EXTRA_GENERATION = "com.khaleel.glance.notification.GENERATION"
    }
}

/** Explicit retention overrides suppression, but never re-promotes a dismissed Live Update. */
object NotificationVisibility {
    private const val PREFS = "notification_visibility"
    private const val DISMISSED = "dismissed"
    private const val KEEP_VISIBLE = "keep_visible"
    private var retention: NotificationRetention? = null

    @Synchronized
    private fun retention(context: Context): NotificationRetention = retention ?: NotificationRetention(
        object : NotificationRetentionStorage {
            private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            override fun read() = preferences.getBoolean(KEEP_VISIBLE, true)
            override fun write(value: Boolean) = preferences.edit().putBoolean(KEEP_VISIBLE, value).commit()
        },
    ).also { retention = it }

    fun keepVisible(context: Context): Boolean = retention(context).keepVisible

    fun setKeepVisible(context: Context, value: Boolean) {
        retention(context).keepVisible = value
    }

    fun regularAfterDismissal(context: Context): Boolean = keepVisible(context) && wasDismissed(context)

    private fun wasDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DISMISSED, false)

    fun isDismissed(context: Context): Boolean =
        !keepVisible(context) && wasDismissed(context)

    fun dismiss(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(DISMISSED, true).commit()
    }

    fun restore(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(DISMISSED, false).commit()
    }
}
