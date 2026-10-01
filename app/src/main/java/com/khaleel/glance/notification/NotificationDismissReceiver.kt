package com.khaleel.glance.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.khaleel.glance.monitoring.MonitoringService
import com.khaleel.glance.GlanceApplication
import com.khaleel.glance.core.RuntimeState

/** Applies the owner's retention choice; never restarts a stopped monitoring session. */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val generation = intent.getLongExtra(EXTRA_GENERATION, Long.MIN_VALUE)
        val app = context.applicationContext as? GlanceApplication ?: return
        val state = app.controller.display.value
        val action = NotificationDismissal.action(
            generation, MonitoringService.currentActionGeneration, state.monitoringEnabled,
            state.runtime == RuntimeState.RUNNING, NotificationVisibility.keepVisible(context),
        )
        if (action == DismissalAction.IGNORE) return
        NotificationVisibility.dismiss(context)
        if (action == DismissalAction.RESTORE_REGULAR) app.showCurrentNotification()
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

    fun keepVisible(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEEP_VISIBLE, true)

    fun setKeepVisible(context: Context, value: Boolean) {
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEEP_VISIBLE, value).commit())
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
