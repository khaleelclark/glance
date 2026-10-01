package com.khaleel.glance.wear.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
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
                val data = dataFor(request.complicationType, frame.shortText, frame.longText, frame.shortTitle)
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
    ): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> shortData(shortText, shortTitle)
        ComplicationType.LONG_TEXT -> longData(longText.ifBlank { shortText })
        else -> null
    }

    private fun shortData(text: String, title: String, preview: Boolean = false): ComplicationData {
        val builder = ShortTextComplicationData.Builder(plain(text), plain("Glance $text"))
        if (title.isNotBlank()) builder.setTitle(plain(title))
        if (!preview) builder.setTapAction(openAppIntent())
        return builder.build()
    }

    private fun longData(text: String, preview: Boolean = false): ComplicationData {
        val builder = LongTextComplicationData.Builder(plain(text), plain(text))
        if (!preview) builder.setTapAction(openAppIntent())
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
