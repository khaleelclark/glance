package com.khaleel.glance.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import com.khaleel.glance.R
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.monitoring.MonitoringService
import java.time.ZoneId
import java.util.Locale

/**
 * Builds and owns Glance's single ongoing monitoring notification.
 *
 * Action receivers must re-check the current monitoring generation before doing work. The
 * notification deliberately carries no account, connection, or reading data in its intents.
 */
class GlucoseNotificationRenderer(
    context: Context,
    private val state: DisplayState,
    private val unit: GlucoseUnit,
    private val lockScreenVisible: Boolean,
    private val enhanced: Boolean = false,
    private val actionGeneration: Long? = null,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val content = NotificationContentFormatter(zoneId, locale).format(state, unit)

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Current shared glucose reading while monitoring is active"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = if (lockScreenVisible) {
                Notification.VISIBILITY_PUBLIC
            } else {
                Notification.VISIBILITY_PRIVATE
            }
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun build(): Notification {
        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_glance_status)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(Notification.BigTextStyle().bigText(content.bigText))
            .setContentIntent(contentIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(state.monitoringEnabled)
            .setOnlyAlertOnce(true)
            .setShowWhen(state.reading != null)
            .setVisibility(
                if (lockScreenVisible) Notification.VISIBILITY_PUBLIC
                else Notification.VISIBILITY_PRIVATE,
            )

        state.reading?.let { builder.setWhen(it.measuredAt.toEpochMilli()) }

        if (state.monitoringEnabled && actionGeneration != null) {
            builder
                .addAction(
                    action(
                        "Refresh",
                        ACTION_REFRESH,
                        android.R.drawable.ic_popup_sync,
                    ),
                )
                .addAction(
                    action(
                        "Pause",
                        ACTION_PAUSE,
                        android.R.drawable.ic_media_pause,
                    ),
                )
        }

        if (!lockScreenVisible) {
            builder.setPublicVersion(buildRedactedPublicVersion())
        }

        if (
            enhanced &&
            state.monitoringEnabled &&
            supportsPromotedOngoingApi() &&
            notificationManager.canPostPromotedNotifications()
        ) {
            builder.setRequestPromotedOngoing(true)
        }

        return builder.build()
    }

    fun post() {
        createChannel()
        notificationManager.notify(NOTIFICATION_ID, build())
    }

    fun hide() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent()
            .setClassName(appContext, MAIN_ACTIVITY_CLASS)
            .setAction(ACTION_OPEN)
        return PendingIntent.getActivity(
            appContext,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun action(
        label: String,
        intentAction: String,
        iconResource: Int,
    ): Notification.Action {
        val generation = requireNotNull(actionGeneration)
        val pendingIntent = if (intentAction == ACTION_REFRESH) {
            MonitoringService.refreshPendingIntent(appContext, generation)
        } else {
            MonitoringService.pausePendingIntent(appContext, generation)
        }
        val icon = Icon.createWithResource(appContext, iconResource)
        return Notification.Action.Builder(icon, label, pendingIntent).build()
    }

    private fun buildRedactedPublicVersion(): Notification =
        Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_glance_status)
            .setContentTitle("Glance · Monitoring")
            .setContentText("Reading hidden on lock screen")
            .setContentIntent(contentIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(state.monitoringEnabled)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

    private fun supportsPromotedOngoingApi(): Boolean =
        Build.VERSION.SDK_INT >= 37 ||
            (
                Build.VERSION.SDK_INT >= 36 &&
                    Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
            )

    companion object {
        const val CHANNEL_ID = "glucose_monitoring"
        const val NOTIFICATION_ID = 1001

        const val ACTION_OPEN = "com.khaleel.glance.action.OPEN"
        const val ACTION_REFRESH = "com.khaleel.glance.action.REFRESH"
        const val ACTION_PAUSE = "com.khaleel.glance.action.PAUSE"

        private const val CHANNEL_NAME = "Glucose monitoring"
        private const val MAIN_ACTIVITY_CLASS = "com.khaleel.glance.MainActivity"
        private const val REQUEST_OPEN = 1000
    }
}
