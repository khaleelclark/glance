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
        // Early Android 16 requires colorization; newer builds require it off. Ask the actual
        // framework instead of assuming Samsung's behavior from SDK_INT / One UI version.
        if (!builder.build().hasPromotableCharacteristics()) {
            builder.setColorized(true)
            if (!builder.build().hasPromotableCharacteristics()) builder.setColorized(false)
        }
    }

    fun settingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 36) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else appSettingsIntent(context)

    fun appSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Posting facts only: no glucose, names, timestamps or chip contents. */
    fun diagnostics(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val active = manager.activeNotifications
        val record = active.firstOrNull {
            it.id == GlucoseNotificationRenderer.NOTIFICATION_ID
        }
        val posted = record?.notification
        val channel = manager.getNotificationChannel(GlucoseNotificationRenderer.CHANNEL_ID)
        return listOf(
            "Monitoring card posted: ${posted != null}",
            "Ongoing flag: ${posted?.let { it.flags and Notification.FLAG_ONGOING_EVENT != 0 } ?: false}",
            "Clear-all protection flag: ${posted?.let { it.flags and Notification.FLAG_NO_CLEAR != 0 } ?: false}",
            "Foreground-service flag: ${posted?.let { it.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 } ?: false}",
            "Public clear-all protection flag: ${posted?.publicVersion?.let { it.flags and Notification.FLAG_NO_CLEAR != 0 } ?: false}",
            "Promotion requested: ${posted?.extras?.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING) ?: false}",
            "Promoted flag: ${posted?.let { it.flags and Notification.FLAG_PROMOTED_ONGOING != 0 } ?: false}",
            "Platform layout eligible: ${if (Build.VERSION.SDK_INT >= 36) posted?.hasPromotableCharacteristics() ?: false else "unsupported"}",
            "Colorization requested: ${posted?.extras?.getBoolean(Notification.EXTRA_COLORIZED) ?: false}",
            "Monitoring icon type: ${posted?.smallIcon?.type ?: "absent"}",
            "Monitoring automatically grouped: ${record?.overrideGroupKey != null}",
            "App group summaries: ${active.count { it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 }}",
            "Active alert icon types: ${active.filter { it.notification.channelId.startsWith("glucose_alert_") }.map { it.notification.smallIcon.type }.distinct().sorted().joinToString().ifEmpty { "none" }}",
            "Chip text supplied: ${Build.VERSION.SDK_INT >= 36 && !posted?.shortCriticalText.isNullOrEmpty()}",
            "Custom layout supplied: ${posted?.let { it.contentView != null || it.bigContentView != null } ?: false}",
            "Monitoring channel importance: ${channel?.importance ?: "not created"}",
            "Keep card visible: ${NotificationVisibility.keepVisible(context)}",
            "Card updates suppressed: ${NotificationVisibility.isDismissed(context)}",
            "Regular card after dismissal: ${NotificationVisibility.regularAfterDismissal(context)}",
        ).joinToString("\n")
    }

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
        if (NotificationVisibility.regularAfterDismissal(context)) {
            return "Keeping the regular card after dismissal. To try Live Update again, turn its switch off and on."
        }
        return if (posted.flags and Notification.FLAG_PROMOTED_ONGOING != 0) {
            "Android reports the monitoring notification as promoted. Samsung controls its Now bar appearance." +
                if (posted.shortCriticalText.isNullOrEmpty())
                    " No chip text is supplied. Glance hides chip values when its lock-screen values are off." else ""
        } else if (posted.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING)) {
            if (!posted.hasPromotableCharacteristics())
                "Android rejected the Live Update layout. The regular notification remains available."
            else "Live Update layout eligible and requested; Android has not promoted the card. Check Samsung's Live notifications and Now bar settings."
        } else "Using the regular card. Enable Status chip Live Update to request promotion."
    }
}
