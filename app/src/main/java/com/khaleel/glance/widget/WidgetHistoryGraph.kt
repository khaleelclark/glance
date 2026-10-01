package com.khaleel.glance.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object WidgetHistoryGraph {
    fun joined(previous: Reading, current: Reading): Boolean =
        Duration.between(previous.measuredAt, current.measuredAt) in Duration.ZERO..Duration.ofMinutes(15)

    fun create(samples: List<Reading>, lineColor: Int, textColor: Int, gridColor: Int): Bitmap? {
        if (samples.size < 2) return null
        val end = samples.last().measuredAt
        val start = end.minus(Duration.ofHours(4))
        val values = samples.map { GlucoseFormat.numericValue(it, GlucoseUnit.MG_DL) ?: return null }
        val low = (values.min() - 10).coerceAtLeast(0.0)
        val high = values.max() + 10
        val bitmap = Bitmap.createBitmap(520, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val left = 12f; val right = 508f; val top = 10f; val bottom = 144f
        paint.color = gridColor; paint.strokeWidth = 2f
        for (step in 0..4) {
            val x = left + (right - left) * step / 4
            canvas.drawLine(x, top, x, bottom, paint)
        }
        for (step in 0..2) {
            val y = top + (bottom - top) * step / 2
            canvas.drawLine(left, y, right, y, paint)
        }
        paint.color = lineColor; paint.strokeWidth = 5f; paint.strokeCap = Paint.Cap.ROUND
        var previousX = 0f; var previousY = 0f
        samples.forEachIndexed { index, sample ->
            val x = left + (Duration.between(start, sample.measuredAt).toMillis().toDouble() /
                Duration.ofHours(4).toMillis() * (right - left)).toFloat()
            val y = bottom - ((values[index] - low) / (high - low) * (bottom - top)).toFloat()
            if (index > 0 && joined(samples[index - 1], sample)) canvas.drawLine(previousX, previousY, x, y, paint)
            canvas.drawCircle(x, y, if (index == samples.lastIndex) 6f else 2.5f, paint)
            previousX = x; previousY = y
        }
        paint.color = textColor; paint.textSize = 24f
        val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault())
        for (step in listOf(0, 2, 4)) {
            paint.textAlign = when (step) { 0 -> Paint.Align.LEFT; 4 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
            canvas.drawText(formatter.format(start.plusSeconds(step * 3600L)), left + (right - left) * step / 4, 174f, paint)
        }
        return bitmap
    }
}
