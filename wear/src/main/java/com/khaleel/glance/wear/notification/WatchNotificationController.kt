package com.khaleel.glance.wear.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khaleel.glance.wear.MainActivity
import com.khaleel.glance.wear.R
import com.khaleel.glance.wear.data.WatchCacheState
import com.khaleel.glance.wear.shared.WatchPresentation
import com.khaleel.glance.wear.shared.WatchSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WatchNotificationController(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun update(state: WatchCacheState, nowMillis: Long) {
        createChannel()
        val content = WatchNotificationFormatter.format(state, nowMillis)
        if (content == null || !notificationPermissionGranted()) {
            hide()
            return
        }
        try {
            manager.notify(NOTIFICATION_ID, build(content))
        } catch (_: SecurityException) {
            hide()
        }
    }

    fun hide() {
        manager.cancel(NOTIFICATION_ID)
    }

    private fun build(
        content: WatchNotificationContent,
    ): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_glance_wear)
            .setContentTitle(if (com.khaleel.glance.wear.BuildConfig.DEBUG) "DEMO · ${content.title}" else content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setLocalOnly(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(false)
            .setTimeoutAfter(content.timeoutMillis.coerceAtLeast(1))
            .setWhen(content.whenMillis)
            .setShowWhen(true)
            .build()
    }

    private fun notificationPermissionGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "glance_watch_glucose"
        const val NOTIFICATION_ID = 2101
    }
}

data class WatchNotificationContent(
    val title: String,
    val text: String,
    val whenMillis: Long,
    val timeoutMillis: Long,
)

object WatchNotificationFormatter {
    fun format(
        state: WatchCacheState,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): WatchNotificationContent? {
        val snapshot = state.snapshot ?: return null
        if (!state.watchNotificationsOptIn || !snapshot.notificationsEnabled || state.revoked) return null
        val presentation = WatchPresentation.from(snapshot, nowMillis)
        if (presentation.heading in setOf("Open phone", "Hidden", "Stopped")) return null
        val expiry = presentation.expiresAtMillis.takeIf { it > 0 }
            ?: (snapshot.sentAtMillis + WatchSnapshot.LEASE_MILLIS)
        if (expiry <= nowMillis) return null
        val reading = presentation.value.isNotEmpty()
        val title = if (presentation.fresh) {
            listOf(presentation.value, presentation.unit, presentation.arrow)
                .filter { it.isNotBlank() }.joinToString(" ")
        } else presentation.heading
        val measuredTime = DateTimeFormatter.ofPattern("HH:mm").format(
            Instant.ofEpochMilli(snapshot.measuredAtMillis).atZone(zoneId),
        )
        val text = when {
            reading && !presentation.fresh ->
                "Last: ${presentation.value} ${presentation.unit} at $measuredTime"
            reading -> "Reading at $measuredTime"
            else -> "Open Glance for status"
        }
        return WatchNotificationContent(
            title = title,
            text = text,
            whenMillis = snapshot.measuredAtMillis.takeIf { it > 0 } ?: snapshot.sentAtMillis,
            timeoutMillis = expiry - nowMillis,
        )
    }
}
