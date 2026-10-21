package com.khaleel.glance.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.ReadingFreshness
import com.khaleel.glance.core.RuntimeState
import java.util.Locale
import java.io.ByteArrayOutputStream

/** No historical number is presented as a current status-bar value. */
object GlucoseStatusIcon {
    fun text(
        state: DisplayState,
        unit: GlucoseUnit,
        enabled: Boolean,
        lockScreenVisible: Boolean = true,
    ): String? {
        // PRIVATE/publicVersion redacts the card, but does not reliably hide the original icon.
        if (!enabled || !lockScreenVisible || !state.monitoringEnabled || state.runtime != RuntimeState.RUNNING ||
            state.freshness != ReadingFreshness.FRESH
        ) return null
        val reading = state.reading?.takeIf { it.connectionId == state.person?.id } ?: return null
        if (GlucoseFormat.numericValue(reading, unit) == null) return null
        return GlucoseFormat.value(reading, unit, Locale.US)
            .takeIf { it.length <= 4 && it.matches(Regex("[0-9]+(\\.[0-9])?")) }
    }

    /** Android tints the alpha mask; transparent background and white glyphs work in both themes. */
    fun create(text: String): Icon = create(text, encoded = Build.VERSION.SDK_INT >= 37)

    /** Data icons compare by content in GroupHelper, bitmap icons by object identity. */
    internal fun create(text: String, encoded: Boolean): Icon {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            textSize = 88f
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        paint.textSize *= minOf(80f / bounds.width().coerceAtLeast(1), 76f / bounds.height().coerceAtLeast(1))
        paint.getTextBounds(text, 0, text.length, bounds)
        Canvas(bitmap).drawText(text, 48f - bounds.exactCenterX(), 48f - bounds.exactCenterY(), paint)
        // Android 17 updates auto-summary attributes as child icons change. Android 16's
        // section-based grouping can keep its initial icon, so retain the previous bitmap
        // representation there rather than introducing a frozen numeric group summary.
        if (!encoded) return Icon.createWithBitmap(bitmap)
        val png = ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        return Icon.createWithData(png, 0, png.size)
    }
}
