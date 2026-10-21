package com.khaleel.glance.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.khaleel.glance.R
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.RuntimeState
import com.khaleel.glance.notification.GlucoseStatusIcon
import java.util.UUID
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Separate dismissible alerts; the quiet ongoing monitoring card remains unchanged. */
class GlucoseAlertNotifier(
    private val context: Context,
    private val numericStatusIcon: () -> Boolean = { true },
) : AlertDelivery {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private data class ActiveAlert(
        val kind: AlertKind, val personId: String, val token: String,
        var iconText: String?, var iconDeadline: Instant?,
    )
    private var activeAlert: ActiveAlert? = null

    private data class StatusIcon(val text: String?, val expiry: AlertIconExpiry?)
    private fun statusIcon(display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean): StatusIcon {
        val expiry = AlertIconExpiry.forReading(display.reading, Instant.now())
        val text = GlucoseStatusIcon.text(display, unit, numericStatusIcon(), showOnLockScreen)
            .takeIf { expiry != null }
        return StatusIcon(text, expiry.takeIf { text != null })
    }

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

    @Synchronized
    override fun post(kind: AlertKind, display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) {
        val person = display.person ?: return
        val reading = display.reading ?: return
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(reading.measuredAt)
        val title = "${kind.label} · ${person.displayName}"
        val detail = "${GlucoseFormat.withUnit(reading, unit, Locale.getDefault())} at $time"
        val token = UUID.randomUUID().toString()
        val icon = statusIcon(display, unit, showOnLockScreen)
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
            .setDeleteIntent(PendingIntent.getBroadcast(
                context, notificationId(kind),
                Intent(context, AlertDismissReceiver::class.java)
                    .setAction(AlertDismissReceiver.ACTION_DISMISSED)
                    .setData(Uri.parse("glance-alert://dismiss/$token"))
                    .putExtra(AlertDismissReceiver.EXTRA_TOKEN, token),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            .addExtras(Bundle().apply { putString(ICON_TOKEN, token) })
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setWhen(reading.measuredAt.toEpochMilli())
            .setShowWhen(true)
            .setTimeoutAfter(icon.expiry?.remainingMillis ?: 0)
            .setVisibility(if (showOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_PRIVATE)
        icon.text?.let { builder.setSmallIcon(GlucoseStatusIcon.create(it)) }
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
        activeAlert = ActiveAlert(kind, person.id, token, icon.text, icon.expiry?.deadline)
    }

    @Synchronized
    override fun refreshIcon(display: DisplayState, unit: GlucoseUnit, showOnLockScreen: Boolean) {
        val active = activeAlert ?: run {
            // A prior process cannot prove the identity/freshness of a retained numeric alert icon.
            clear()
            return
        }
        if (!display.monitoringEnabled || display.runtime != RuntimeState.RUNNING ||
            display.person?.id != active.personId || display.reading?.connectionId != active.personId
        ) {
            clear()
            return
        }
        val posted = manager.activeNotifications.firstOrNull {
            it.id == notificationId(active.kind) && it.notification.extras.getString(ICON_TOKEN) == active.token
        } ?: run { activeAlert = null; return }
        if (!showOnLockScreen && posted.notification.visibility == Notification.VISIBILITY_PUBLIC) {
            clear()
            return
        }
        val icon = statusIcon(display, unit, showOnLockScreen)
        if (icon.text == active.iconText && icon.expiry?.deadline == active.iconDeadline) return
        val builder = Notification.Builder.recoverBuilder(context, posted.notification)
            .setSmallIcon(R.drawable.ic_glance_status)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(icon.expiry?.remainingMillis ?: 0)
        icon.text?.let { builder.setSmallIcon(GlucoseStatusIcon.create(it)) }
        // Preserve the alarm's original title, triggering reading/time, channel and delete token.
        manager.notify(posted.id, builder.build())
        active.iconText = icon.text
        active.iconDeadline = icon.expiry?.deadline
    }

    @Synchronized
    override fun dismiss(token: String) {
        // A delayed delete intent from an older alarm cannot remove a newer one.
        if (activeAlert?.token == token) clear()
    }

    @Synchronized
    override fun clear() {
        activeAlert = null
        AlertKind.entries.forEach { manager.cancel(notificationId(it)) }
    }

    companion object {
        private const val ICON_TOKEN = "com.khaleel.glance.alerts.ICON_TOKEN"
        fun channelId(kind: AlertKind): String = "glucose_alert_${kind.name.lowercase()}"
        private fun notificationId(kind: AlertKind): Int = 5200 + kind.ordinal
    }
}
