package com.khaleel.glance.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import com.khaleel.glance.R
import com.khaleel.glance.BuildConfig
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.ConnectionState
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
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
    private val numericStatusIcon: Boolean = true,
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
        val promoted = enhanced &&
            state.monitoringEnabled &&
            state.runtime == RuntimeState.RUNNING &&
            LiveUpdateSupport.canRequest(appContext)
        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_glance_status)
            .setContentTitle(content.title)
            .setContentText(emphasizedReading(content.text))
            .setContentIntent(contentIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(state.monitoringEnabled)
            .setOnlyAlertOnce(true)
            .setShowWhen(state.reading != null)
            .setVisibility(
                if (lockScreenVisible) Notification.VISIBILITY_PUBLIC
                else Notification.VISIBILITY_PRIVATE,
            )

        // A promoted chip already has the reading as text: retain the app glyph to avoid duplication.
        GlucoseStatusIcon.text(state, unit, numericStatusIcon && !promoted, lockScreenVisible)?.let {
            builder.setSmallIcon(GlucoseStatusIcon.create(it))
        }

        state.reading?.let { builder.setWhen(it.measuredAt.toEpochMilli()) }

        actionGeneration?.let { builder.setDeleteIntent(dismissIntent(it)) }

        if (!lockScreenVisible) {
            builder.setPublicVersion(buildRedactedPublicVersion())
        }
        // Use only genuine same-person history. Samsung chooses the large icon's placement.
        if (!promoted && lockScreenVisible) {
            NotificationSparkline.create(state)?.let { builder.setLargeIcon(it) }
        }

        if (promoted) {
            builder.setStyle(Notification.BigTextStyle().bigText(emphasizedReading(content.bigText)))
            // The promoted status chip can show a short current value without opening the shade.
            // Delayed or stale readings use a label instead of an apparently current number.
            // The original chip can appear outside the redacted public card on lock screen/AOD.
            LiveUpdateSupport.request(builder, if (lockScreenVisible) content.statusChipText.orEmpty() else "")
        } else {
            // Promoted Live Updates cannot use RemoteViews and Android makes them uncollapsible.
            // The regular card uses distinct compact and expanded views for readable numbers.
            builder.setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(customView(R.layout.notification_glucose_compact, expanded = false))
                .setCustomBigContentView(customView(R.layout.notification_glucose_expanded, expanded = true))
        }

        return builder.build().apply {
            // Clear all leaves the monitoring card in place. Android 14+ may still allow a swipe.
            if (state.monitoringEnabled) flags = flags or Notification.FLAG_NO_CLEAR
        }
    }

    fun post() {
        if (NotificationVisibility.isDismissed(appContext)) return
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

    private fun dismissIntent(generation: Long): PendingIntent {
        val intent = Intent(appContext, NotificationDismissReceiver::class.java)
            .setAction(NotificationDismissReceiver.ACTION_DISMISSED)
            .setData(Uri.parse("glance://notification-dismissed/$generation"))
            .putExtra(NotificationDismissReceiver.EXTRA_GENERATION, generation)
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_DISMISS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun customView(layoutId: Int, expanded: Boolean): RemoteViews =
        RemoteViews(appContext.packageName, layoutId).apply {
            val demo = BuildConfig.DEBUG && state.person?.displayName?.startsWith("DEMO · ") == true
            setTextViewText(R.id.notification_primary, content.prominent)
            setTextViewText(R.id.notification_unit, content.prominentUnit?.let {
                if (demo) "$it\nDEMO" else it
            }.orEmpty())
            setViewVisibility(
                R.id.notification_unit,
                if (content.prominentUnit == null) View.GONE else View.VISIBLE,
            )
            setTextViewText(R.id.notification_arrow, content.prominentArrow.orEmpty())
            setViewVisibility(
                R.id.notification_arrow,
                if (content.prominentArrow == null) View.GONE else View.VISIBLE,
            )
            // The compact reference is one clean arrow/value/unit row. Details belong below
            // it when expanded; error/old-reading states still show their explanatory line.
            val fresh = state.freshness == ReadingFreshness.FRESH && state.monitoringEnabled &&
                state.runtime == RuntimeState.RUNNING &&
                state.connection in listOf(ConnectionState.READY, ConnectionState.FETCHING)
            setViewVisibility(R.id.notification_detail, if (!expanded && fresh) View.GONE else View.VISIBLE)
            if (!expanded && !fresh) {
                setTextViewTextSize(R.id.notification_primary, android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextViewTextSize(R.id.notification_arrow, android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
            }
            setTextViewText(
                R.id.notification_detail,
                if (expanded) content.expandedDetail else
                    if (demo) "DEMO · ${content.compactDetail}" else content.compactDetail,
            )
        }

    private fun emphasizedReading(text: String): CharSequence {
        if (state.freshness != ReadingFreshness.FRESH || state.reading == null) return text
        val firstLineEnd = text.indexOf('\n').takeIf { it >= 0 } ?: text.length
        return SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, firstLineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun buildRedactedPublicVersion(): Notification {
        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_glance_status)
            .setContentTitle("Glance · Monitoring")
            .setContentText("Reading hidden on lock screen")
            .setContentIntent(contentIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(state.monitoringEnabled)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
        if (Build.VERSION.SDK_INT >= 36) builder.setShortCriticalText("")
        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "glucose_monitoring"
        const val NOTIFICATION_ID = 1001

        const val ACTION_OPEN = "com.khaleel.glance.action.OPEN"
        const val ACTION_REFRESH = "com.khaleel.glance.action.REFRESH"
        const val ACTION_PAUSE = "com.khaleel.glance.action.PAUSE"

        private const val CHANNEL_NAME = "Glucose monitoring"
        private const val MAIN_ACTIVITY_CLASS = "com.khaleel.glance.MainActivity"
        private const val REQUEST_OPEN = 1000
        private const val REQUEST_DISMISS = 1003
    }
}
