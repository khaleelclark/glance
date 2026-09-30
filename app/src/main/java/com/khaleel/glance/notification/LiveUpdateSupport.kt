package com.khaleel.glance.notification

import android.app.Notification
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings

/** API 36.0 understands the promotion extra; the platform builder setter arrived in 36.1. */
object LiveUpdateSupport {
    fun canRequest(context: Context): Boolean = Build.VERSION.SDK_INT >= 36 &&
        context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()

    // The inlined extra is understood by Android 16.0 even though the public constant is 36.1.
    @SuppressLint("InlinedApi")
    fun request(builder: Notification.Builder, text: String) {
        if (Build.VERSION.SDK_INT < 36) return
        builder.setShortCriticalText(text)
        builder.addExtras(Bundle().apply { putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true) })
    }

    fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 36) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else appSettingsIntent(context)

    fun appSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun status(context: Context): String {
        if (Build.VERSION.SDK_INT < 36) return "Live Update needs Android 16 or newer. The regular notification remains available."
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return "App notifications are blocked in Android settings."
        if (!manager.canPostPromotedNotifications()) return "Live Updates are not allowed for Glance in Android settings."
        val channel = manager.getNotificationChannel(GlucoseNotificationRenderer.CHANNEL_ID)
        if (channel != null && channel.importance <= NotificationManager.IMPORTANCE_MIN) {
            return "The Glucose monitoring category is blocked or minimized. Allow it above Minimized for Live Update."
        }
        val posted = manager.activeNotifications.firstOrNull {
            it.id == GlucoseNotificationRenderer.NOTIFICATION_ID
        }?.notification ?: return "Live Update is allowed. Start monitoring to request it; Samsung decides whether to show it."
        return if (posted.flags and Notification.FLAG_PROMOTED_ONGOING != 0) {
            "Android reports the monitoring notification as promoted. Samsung controls its Now bar appearance."
        } else if (posted.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING)) {
            "Live Update requested; Android has not promoted the card. Check Samsung's Live notifications and Now bar settings."
        } else "Using the regular card. Enable Status chip Live Update to request promotion."
    }
}
