package com.khaleel.glance.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import java.time.Duration

/** A small history thumbnail, never a fabricated graph or a substitute for freshness labels. */
internal object NotificationSparkline {
    fun samples(state: DisplayState): List<Reading> {
        if (!state.monitoringEnabled || state.runtime != RuntimeState.RUNNING ||
            state.freshness != ReadingFreshness.FRESH) return emptyList()
        val current = state.reading ?: return emptyList()
        if (current.connectionId != state.person?.id ||
            GlucoseFormat.numericValue(current, GlucoseUnit.MG_DL) == null) return emptyList()
        val start = current.measuredAt.minus(Duration.ofHours(3))
        return (listOf(current) + state.history).filter {
            it.connectionId == current.connectionId && !it.measuredAt.isBefore(start) &&
                !it.measuredAt.isAfter(current.measuredAt) &&
                GlucoseFormat.numericValue(it, GlucoseUnit.MG_DL) != null
        }.distinctBy { it.measuredAt }.sortedBy { it.measuredAt }
    }

    fun create(state: DisplayState): Bitmap? {
        val samples = samples(state)
        if (samples.size < 2) return null
        val values = samples.map { GlucoseFormat.numericValue(it, GlucoseUnit.MG_DL)!! }
        val low = values.min() - 10.0
        val high = values.max() + 10.0
        val duration = Duration.between(samples.first().measuredAt, samples.last().measuredAt).toMillis().toDouble()
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(16, 18, 24)
        canvas.drawRoundRect(0f, 0f, 128f, 128f, 24f, 24f, paint)
        paint.color = Color.rgb(60, 63, 72)
        paint.strokeWidth = 1f
        for (position in listOf(32f, 64f, 96f)) {
            canvas.drawLine(8f, position, 120f, position, paint)
            canvas.drawLine(position, 8f, position, 120f, paint)
        }
        paint.color = Color.rgb(145, 150, 255)
        paint.strokeWidth = 5f
        paint.strokeCap = Paint.Cap.ROUND
        var previousX = 0f
        var previousY = 0f
        samples.forEachIndexed { index, reading ->
            val x = 10f + (Duration.between(samples.first().measuredAt, reading.measuredAt).toMillis() / duration * 108).toFloat()
            val y = 118f - ((values[index] - low) / (high - low) * 108).toFloat()
            if (index > 0 && Duration.between(samples[index - 1].measuredAt, reading.measuredAt) <= Duration.ofMinutes(15)) {
                canvas.drawLine(previousX, previousY, x, y, paint)
            }
            canvas.drawCircle(x, y, 2.5f, paint)
            previousX = x
            previousY = y
        }
        paint.color = Color.WHITE
        canvas.drawCircle(previousX, previousY, 4f, paint)
        return bitmap
    }
}
