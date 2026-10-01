package com.khaleel.glance.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.khaleel.glance.wear.MainActivity
import com.khaleel.glance.wear.data.WatchRuntime
import com.khaleel.glance.wear.data.WearDataSync
import java.time.Instant

class GlanceComplicationService : ComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> shortData("Glance", "", preview = true)
        ComplicationType.LONG_TEXT -> longData("Glance · Open app", preview = true)
        else -> null
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        WatchRuntime.initialize(this)
        val formatted = WearComplicationFormatter.format(
            WatchRuntime.state.value.snapshot,
            System.currentTimeMillis(),
        )
        val defaultData = dataFor(request.complicationType, formatted.defaultText, "")
        if (defaultData == null) {
            listener.onComplicationData(null)
        } else {
            val entries = formatted.frames.mapNotNull { frame ->
                val data = dataFor(request.complicationType, frame.shortText, frame.longText, frame.shortTitle, frame.endMillis)
                    ?: return@mapNotNull null
                TimelineEntry(
                    TimeInterval(
                        Instant.ofEpochMilli(frame.startMillis),
                        Instant.ofEpochMilli(frame.endMillis),
                    ),
                    data,
                )
            }
            listener.onComplicationDataTimeline(ComplicationDataTimeline(defaultData, entries))
        }
        WearDataSync.query(this)
    }

    private fun dataFor(
        type: ComplicationType,
        shortText: String,
        longText: String,
        shortTitle: String = "",
        expiresAtMillis: Long = 0,
    ): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> shortData(
            when (shortText) {
                "Open phone" -> "Phone"
                "Check phone" -> "Check"
                "Time unknown" -> "No time"
                else -> shortText
            }, shortTitle, expiresAtMillis = expiresAtMillis, description = longText.ifBlank { shortText },
        )
        ComplicationType.LONG_TEXT -> longData(longText.ifBlank { shortText }, expiresAtMillis = expiresAtMillis)
        else -> null
    }

    private fun shortData(text: String, title: String, preview: Boolean = false, expiresAtMillis: Long = 0,
                          description: String = text): ComplicationData {
        val builder = ShortTextComplicationData.Builder(plain(text), plain("Glance $description"))
        if (com.khaleel.glance.wear.BuildConfig.DEBUG) builder.setTitle(plain("DEMO"))
        else if (title.isNotBlank()) builder.setTitle(plain(title))
        if (!preview) builder.setTapAction(openAppIntent())
        if (expiresAtMillis > 0) builder.setValidTimeRange(TimeRange.before(Instant.ofEpochMilli(expiresAtMillis)))
        return builder.build()
    }

    private fun longData(text: String, preview: Boolean = false, expiresAtMillis: Long = 0): ComplicationData {
        val label = if (com.khaleel.glance.wear.BuildConfig.DEBUG) "DEMO · $text" else text
        val builder = LongTextComplicationData.Builder(plain(label), plain(label))
        if (!preview) builder.setTapAction(openAppIntent())
        if (expiresAtMillis > 0) builder.setValidTimeRange(TimeRange.before(Instant.ofEpochMilli(expiresAtMillis)))
        return builder.build()
    }

    private fun plain(text: String) = PlainComplicationText.Builder(text).build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

object ComplicationPublisher {
    fun requestUpdate(context: android.content.Context) {
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, GlanceComplicationService::class.java),
        ).requestUpdateAll()
    }
}
