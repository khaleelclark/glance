package com.khaleel.glance.ui

/** Fractions of the retained day; the default shows the latest six hours. */
internal data class ChartViewport(val start: Double = 0.75, val span: Double = 0.25) {
    companion object {
        fun latest(hours: Int): ChartViewport {
            require(hours in 1..24)
            val span = hours / 24.0
            return ChartViewport(1.0 - span, span)
        }
    }

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
