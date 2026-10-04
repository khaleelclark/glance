package com.khaleel.glance.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.khaleel.glance.GlanceApplication

/** Stops icon refresh after a swipe/tap, including a refresh racing with the delete callback. */
class AlertDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val token = intent.getStringExtra(EXTRA_TOKEN) ?: return
        val app = context.applicationContext as? GlanceApplication ?: return
        app.alertEngine.dismissNotification(token)
    }

    companion object {
        const val ACTION_DISMISSED = "com.khaleel.glance.alerts.DISMISSED"
        const val EXTRA_TOKEN = "com.khaleel.glance.alerts.TOKEN"
    }
}
