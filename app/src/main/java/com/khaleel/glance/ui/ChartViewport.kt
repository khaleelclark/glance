package com.khaleel.glance.ui

/** Fractions of the retained day; the default shows the latest three hours. */
internal data class ChartViewport(val start: Double = 0.875, val span: Double = 0.125) {
    val end: Double get() = start + span

    fun transform(panFraction: Double, zoom: Double, anchorFraction: Double): ChartViewport {
        if (!panFraction.isFinite() || !zoom.isFinite() || zoom <= 0 || !anchorFraction.isFinite()) return this
        val anchor = anchorFraction.coerceIn(0.0, 1.0)
        val nextSpan = (span / zoom).coerceIn(1.0 / 24, 1.0)
        val nextStart = start + anchor * (span - nextSpan) - panFraction * nextSpan
        return ChartViewport(nextStart.coerceIn(0.0, 1.0 - nextSpan), nextSpan)
    }

    fun xFraction(dayFraction: Double): Float = ((dayFraction - start) / span).toFloat()
}
