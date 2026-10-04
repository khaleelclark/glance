package com.khaleel.glance.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import com.khaleel.glance.R
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseUnit
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Separate dismissible alerts; the quiet ongoing monitoring card remains unchanged. */
class GlucoseAlertNotifier(private val context: Context) : AlertDelivery {
    private val manager = context.getSystemService(NotificationManager::class.java)

    override fun ensureChannels() {
        AlertKind.entries.forEach { kind ->
            val channel = NotificationChannel(
                channelId(kind),
                "${kind.label} glucose alerts",
                if (kind.urgent) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Glucose ${kind.label.lowercase()} threshold notifications"
                val sound = if (kind.urgent) Settings.System.DEFAULT_ALARM_ALERT_URI
                    else Settings.System.DEFAULT_NOTIFICATION_URI
                val attributes = AudioAttributes.Builder()
                    .setUsage(if (kind.urgent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
                setSound(sound, attributes)
                enableVibration(kind.urgent)
                // The individual alert supplies a redacted public version when privacy is off.
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun channelSettingsIntent(kind: AlertKind): Intent {
        ensureChannels()
        return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId(kind))
    }

    override fun canPost(kind: AlertKind): Boolean =
        manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId(kind))?.importance?.let {
                it != NotificationManager.IMPORTANCE_NONE
            } == true

    override fun post(kind: AlertKind, display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) {
        val person = display.person ?: return
        val reading = display.reading ?: return
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(reading.measuredAt)
        val title = "${kind.label} · ${person.displayName}"
        val detail = "${GlucoseFormat.withUnit(reading, unit, Locale.getDefault())} at $time"
        val open = PendingIntent.getActivity(
            context, 5100, Intent().setClassName(context, "com.khaleel.glance.MainActivity")
                .setAction("com.khaleel.glance.action.OPEN_ALERT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, channelId(kind))
            .setSmallIcon(R.drawable.ic_glance_status)
            .setContentTitle(title)
            .setContentText(detail)
            .setCategory(if (kind.urgent) Notification.CATEGORY_ALARM else Notification.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(reading.measuredAt.toEpochMilli())
            .setShowWhen(true)
            .setVisibility(if (showOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
        if (!showOnLockScreen) {
            builder.setPublicVersion(
                Notification.Builder(context, channelId(kind))
                    .setSmallIcon(R.drawable.ic_glance_status)
                    .setContentTitle("Glance glucose alert")
                    .setContentText("Open Glance to view the reading")
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build(),
            )
        }
        clear()
        manager.notify(notificationId(kind), builder.build())
    }

    override fun clear() {
        AlertKind.entries.forEach { manager.cancel(notificationId(it)) }
    }

    companion object {
        fun channelId(kind: AlertKind): String = "glucose_alert_${kind.name.lowercase()}"
        private fun notificationId(kind: AlertKind): Int = 5200 + kind.ordinal
    }
}
