package com.khaleel.glance.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.khaleel.glance.monitoring.MonitoringService

/** Records an Android 14+ user dismissal without stopping monitoring. */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val generation = intent.getLongExtra(EXTRA_GENERATION, Long.MIN_VALUE)
        if (generation != MonitoringService.currentActionGeneration) return
        NotificationVisibility.dismiss(context)
    }

    companion object {
        const val ACTION_DISMISSED = "com.khaleel.glance.notification.DISMISSED"
        const val EXTRA_GENERATION = "com.khaleel.glance.notification.GENERATION"
    }
}

/** Dismissal survives process recreation; automatic restarts stop instead of reposting. */
object NotificationVisibility {
    private const val PREFS = "notification_visibility"
    private const val DISMISSED = "dismissed"

    fun isDismissed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DISMISSED, false)

    fun dismiss(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(DISMISSED, true).commit()
    }

    fun restore(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(DISMISSED, false).commit()
    }
}
