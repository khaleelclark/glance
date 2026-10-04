package com.khaleel.glance.ui

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.khaleel.glance.alerts.AlertConfig
import com.khaleel.glance.core.GlucoseFormat
import com.khaleel.glance.core.GlucoseFormat.label
import com.khaleel.glance.core.GlucoseUnit
import com.khaleel.glance.core.Reading
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

internal val chartWindow: Duration = Duration.ofHours(24)
private val gapLimit = Duration.ofMinutes(20)

internal fun chartPoints(history: List<Reading>, connectionId: String?, now: Instant): List<Reading> =
    history.filter {
        it.connectionId == connectionId &&
            it.specialValue == null &&
            it.valueMgDl?.isFinite() == true &&
            it.valueMgDl > 0.0 &&
            !it.measuredAt.isBefore(now.minus(chartWindow)) &&
            !it.measuredAt.isAfter(now)
    }.sortedBy { it.measuredAt }

/** Select only a real observation; never interpolate a value through a gap. */
internal fun nearestChartPointIndex(
    points: List<Reading>, now: Instant, widthPx: Float, x: Float,
    viewport: ChartViewport = ChartViewport(0.0, 1.0),
): Int? = points.indices.filter {
    timeFraction(points[it].measuredAt, now) in viewport.start..viewport.end
}.minByOrNull { index ->
    kotlin.math.abs(widthPx * viewport.xFraction(timeFraction(points[index].measuredAt, now)) - x)
}

/** Accessibility reading navigation is strictly confined to the selected preset. */
internal fun adjacentVisibleChartPointIndex(
    points: List<Reading>,
    selectedIndex: Int,
    now: Instant,
    viewport: ChartViewport,
    direction: Int,
): Int? {
    if (direction != -1 && direction != 1) return null
    val visible = points.indices.filter {
        timeFraction(points[it].measuredAt, now) in viewport.start..viewport.end
    }
    val visiblePosition = visible.indexOf(selectedIndex)
    if (visiblePosition < 0) return null
    return visible.getOrNull(visiblePosition + direction)
}

/** Keep a centered overlay fully inside its chart, including very narrow layouts. */
internal fun clampedOverlayStart(center: Float, labelWidth: Float, availableWidth: Float): Float {
    if (!center.isFinite() || !labelWidth.isFinite() || !availableWidth.isFinite()) return 0f
    val available = availableWidth.coerceAtLeast(0f)
    val width = labelWidth.coerceIn(0f, available)
    return (center - width / 2f).coerceIn(0f, available - width)
}

/** Shorter presets use finer local clock ticks; instants stay ordered across DST. */
internal fun chartTimeTicks(now: Instant, zoneId: ZoneId, span: Double = 1.0): List<Instant> {
    val start = now.minus(chartWindow)
    val minutes = if (span <= 1.0 / 12) 30L else if (span <= 0.25) 60L else 120L
    var local = now.atZone(zoneId).truncatedTo(ChronoUnit.MINUTES)
    local = local.minusMinutes((local.hour * 60L + local.minute) % minutes)
    val result = mutableListOf<Instant>()
    while (!local.toInstant().isBefore(start)) {
        result += local.toInstant()
        local = local.minusMinutes(minutes)
    }
    return result.asReversed()
}

@Composable
internal fun ReadingHistoryChart(
    history: List<Reading>,
    connectionId: String?,
    unit: GlucoseUnit,
    now: Instant,
    zoneId: ZoneId,
    locale: Locale,
    selectedTime: Long?,
    onSelectedTimeChange: (Long) -> Unit,
    alerts: AlertConfig = AlertConfig.defaults(),
) {
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fontScale = LocalDensity.current.fontScale
    val chartHeight = when {
        compact && fontScale >= 1.5f -> 210.dp
        compact -> 170.dp
        fontScale >= 1.5f -> 340.dp
        else -> 300.dp
    }
    val points = chartPoints(history, connectionId, now)
    val limits = chartLimits(alerts)
    var viewportStart by rememberSaveable(connectionId) { mutableStateOf(0.75) }
    var viewportSpan by rememberSaveable(connectionId) { mutableStateOf(0.25) }
    val viewport = ChartViewport(viewportStart, viewportSpan)
    fun setViewport(next: ChartViewport) {
        viewportStart = next.start
        viewportSpan = next.span
    }
    val latestPoints by rememberUpdatedState(points)
    val latestNow by rememberUpdatedState(now)
    val selectedIndex = points.indexOfFirst { it.measuredAt.toEpochMilli() == selectedTime }
        .takeIf { it >= 0 } ?: points.lastIndex
    val selected = points.getOrNull(selectedIndex)
    val selectedVisible = selected?.takeIf { timeFraction(it.measuredAt, now) in viewport.start..viewport.end }
    val latestOnSelect by rememberUpdatedState(onSelectedTimeChange)
    val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(zoneId)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Text("Glucose trend", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (points.isEmpty()) {
            Text("The graph will appear after Glance receives readings for this person.")
        } else {
                val minPoint = points.minBy { it.valueMgDl!! }
                val maxPoint = points.maxBy { it.valueMgDl!! }
                val bottom = chartBottomMgDl
                val top = chartTopMgDl
                val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val lowColor = if (dark) Color(0xFFFF6B6B) else Color(0xFFC62828)
                val highColor = if (dark) Color(0xFFFFCA65) else Color(0xFF9A6700)
                val inRangeColor = if (dark) Color(0xFF79D991) else Color(0xFF2E7D32)
                val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.92f else 0.86f)
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val selectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                val markerBackingColor = MaterialTheme.colorScheme.surface
                val markerOutlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f)
                fun valueColor(valueMgDl: Double): Color = when (limits.band(valueMgDl)) {
                    ChartBand.LOW -> lowColor
                    ChartBand.HIGH -> highColor
                    ChartBand.IN_RANGE -> inRangeColor
                }
                fun bandLabel(valueMgDl: Double): String = when (limits.band(valueMgDl)) {
                    ChartBand.LOW -> "below Low limit"
                    ChartBand.HIGH -> "above High limit"
                    ChartBand.IN_RANGE -> "in range"
                }
                val limitLegend = "Low: ${axisValue(limits.lowMgDl, points[0], unit, locale)} ${unit.label} · " +
                    "High: ${axisValue(limits.highMgDl, points[0], unit, locale)} ${unit.label}"
                val scaleLegend = "Scale: ${axisValue(bottom, points[0], unit, locale)}–" +
                    "${axisValue(top, points[0], unit, locale)} ${unit.label}"
                val timeTicks = chartTimeTicks(now, zoneId, viewport.span)
                val selectedDescription = selectedVisible?.let { reading ->
                    val scaleStatus = when {
                        reading.valueMgDl!! > top -> ", above graph scale"
                        reading.valueMgDl < bottom -> ", below graph scale"
                        else -> ""
                    }
                    " Selected source reading ${GlucoseFormat.withUnit(reading, unit, locale)}, " +
                        "${bandLabel(reading.valueMgDl)}$scaleStatus, measured ${timeFormat.format(reading.measuredAt)}."
                }.orEmpty()
                val windowDescription = if (points.none {
                    timeFraction(it.measuredAt, now) in viewport.start..viewport.end
                }) " No source readings in this time window." else ""
                val chartDescription = "${points.size} source readings over 24 hours. " +
                    "Observed range ${GlucoseFormat.withUnit(minPoint, unit, locale)} to " +
                    "${GlucoseFormat.withUnit(maxPoint, unit, locale)}. $scaleLegend. $limitLegend. " +
                    "Red below Low; yellow above High; green marks the newest in-range reading. " +
                    "Touch a point to inspect. Use the 3, 6, 12 or 24 hour choices to change the time window." +
                    windowDescription + selectedDescription

                Row(Modifier.fillMaxWidth()) {
                    val topRail = when {
                        fontScale >= 1.5f -> 64.dp
                        compact -> 38.dp
                        else -> 46.dp
                    }
                    val bottomRail = if (fontScale >= 1.5f) 44.dp else if (compact) 26.dp else 30.dp
                    val plotHeight = chartHeight - topRail - bottomRail
                    Box(Modifier.width(48.dp * fontScale.coerceAtLeast(1f)).height(chartHeight)) {
                        Text(
                            unit.label,
                            Modifier.offset(y = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        for (value in chartGridLabelValues(compact || fontScale >= 1.5f)) {
                            val y = if (value == chartBottomMgDl) topRail + plotHeight + 4.dp
                            else topRail + plotHeight * chartYFraction(value).toFloat() - 8.dp
                            Text(axisValue(value, points[0], unit, locale), Modifier.offset(y = y),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val chartWidth = maxWidth
                        val selectedFraction = selected?.let { timeFraction(it.measuredAt, now) }
                        val selectedX = selectedFraction?.takeIf { it in viewport.start..viewport.end }
                            ?.let { chartWidth * viewport.xFraction(it) }
                        val tooltipWidth = minOf((if (compact) 112.dp else 132.dp) * fontScale.coerceAtLeast(1f), chartWidth)
                        val timeLabelWidth = minOf((if (compact) 112.dp else 152.dp) * fontScale.coerceAtLeast(1f), chartWidth)
                        val tooltipX = selectedX?.let {
                            clampedOverlayStart(it.value, tooltipWidth.value, chartWidth.value).dp
                        }
                        val timeLabelX = selectedX?.let {
                            clampedOverlayStart(it.value, timeLabelWidth.value, chartWidth.value).dp
                        }
                        val railTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
                            .withLocale(locale).withZone(zoneId)
                        Column(
                            Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(chartHeight).pointerInput(connectionId) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        fun select(x: Float) {
                                            nearestChartPointIndex(
                                                latestPoints,
                                                latestNow,
                                                size.width.toFloat(),
                                                x,
                                                ChartViewport(viewportStart, viewportSpan),
                                            )?.let { latestOnSelect(latestPoints[it].measuredAt.toEpochMilli()) }
                                        }
                                        // Once a second finger joins, never scrub with the remaining finger.
                                        var multipointer = false
                                        do {
                                            val event = awaitPointerEvent()
                                            val pressed = event.changes.filter { it.pressed }
                                            if (pressed.size >= 2) {
                                                multipointer = true
                                            } else if (!multipointer) {
                                                event.changes.firstOrNull { it.id == down.id }?.let { select(it.position.x) }
                                            }
                                            event.changes.forEach { it.consume() }
                                        } while (pressed.isNotEmpty())
                                    }
                                },
                            ) {
                                Canvas(
                                    Modifier.fillMaxSize()
                                    .semantics {
                                        contentDescription = chartDescription
                                        stateDescription = String.format(Locale.ROOT, "Showing %.1f hours", viewport.span * 24) +
                                            ", from " + timeFormat.format(now.minusMillis(((1.0 - viewport.start) * chartWindow.toMillis()).toLong())) +
                                            " to " + timeFormat.format(now.minusMillis(((1.0 - viewport.end) * chartWindow.toMillis()).toLong()))
                                        fun selectAdjacent(direction: Int): Boolean {
                                            val index = adjacentVisibleChartPointIndex(
                                                points, selectedIndex, now, viewport, direction,
                                            ) ?: return false
                                            latestOnSelect(points[index].measuredAt.toEpochMilli())
                                            return true
                                        }
                                        customActions = listOf(
                                            CustomAccessibilityAction("Earlier reading") { selectAdjacent(-1) },
                                            CustomAccessibilityAction("Later reading") { selectAdjacent(1) },
                                        )
                                    },
                                ) {
                                    val plotTop = topRail.toPx()
                                    val plotBottom = size.height - bottomRail.toPx()
                                    val offsets = points.map { plotOffset(it, now, size, plotTop, plotBottom, viewport) }
                                    fun y(value: Double): Float = plotTop + (plotBottom - plotTop) * chartYFraction(value).toFloat()
                                    val lowY = y(limits.lowMgDl).coerceIn(plotTop, plotBottom)
                                    val highY = y(limits.highMgDl).coerceIn(plotTop, plotBottom)
                                    val thresholdDash = PathEffect.dashPathEffect(
                                        floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
                                    )
                                    clipRect(left = 0f, top = plotTop, right = size.width, bottom = plotBottom) {
                                        if (!limits.overlaps && lowY > highY) {
                                            drawRect(
                                                inRangeColor.copy(alpha = if (dark) 0.12f else 0.09f),
                                                Offset(0f, highY),
                                                Size(size.width, lowY - highY),
                                            )
                                        }
                                        for (value in chartGridValues()) {
                                            drawLine(gridColor, Offset(0f, y(value)), Offset(size.width, y(value)), strokeWidth = 1.dp.toPx())
                                        }
                                        for (tick in timeTicks) {
                                            val x = size.width * viewport.xFraction(timeFraction(tick, now))
                                            drawLine(
                                                gridColor.copy(alpha = 0.6f),
                                                Offset(x, plotTop),
                                                Offset(x, plotBottom),
                                                strokeWidth = 1.dp.toPx(),
                                            )
                                        }
                                        for ((value, color) in listOf(limits.highMgDl to highColor, limits.lowMgDl to lowColor)) {
                                            if (value in bottom..top) drawLine(
                                                color,
                                                Offset(0f, y(value)),
                                                Offset(size.width, y(value)),
                                                strokeWidth = 1.5.dp.toPx(),
                                                pathEffect = thresholdDash,
                                            )
                                        }
                                        fun trace(color: Color) {
                                            offsets.zipWithNext().forEachIndexed { index, (left, right) ->
                                                if (Duration.between(points[index].measuredAt, points[index + 1].measuredAt) <= gapLimit) {
                                                    drawLine(color, left, right, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                                }
                                            }
                                        }
                                        trace(lineColor)
                                        clipRect(bottom = highY) { trace(highColor) }
                                        // Color clipping changes only presentation, never source observations.
                                        clipRect(top = lowY) { trace(lowColor) }
                                        offsets.getOrNull(selectedIndex)?.takeIf { selectedVisible != null }?.let { offset ->
                                            drawLine(
                                                selectedColor,
                                                Offset(offset.x, plotTop),
                                                Offset(offset.x, plotBottom),
                                                strokeWidth = 1.dp.toPx(),
                                            )
                                            if (points[selectedIndex].valueMgDl!! in bottom..top) {
                                                drawCircle(markerBackingColor, radius = 6.dp.toPx(), center = offset)
                                                drawCircle(valueColor(points[selectedIndex].valueMgDl!!), radius = 4.dp.toPx(), center = offset)
                                            }
                                        }
                                        offsets.getOrNull(points.lastIndex)?.let { offset ->
                                            val newest = points.last()
                                            if (offset.x in 0f..size.width && newest.valueMgDl!! in bottom..top) {
                                                drawCircle(markerBackingColor, radius = 8.dp.toPx(), center = offset)
                                                drawCircle(valueColor(newest.valueMgDl), radius = 5.5.dp.toPx(), center = offset)
                                                drawCircle(
                                                    markerOutlineColor,
                                                    radius = 8.dp.toPx(),
                                                    center = offset,
                                                    style = Stroke(width = 1.dp.toPx()),
                                                )
                                            }
                                        }
                                    }
                                    // Out-of-scale source values get edge arrows, never a fabricated 40/350 reading.
                                    points.forEachIndexed { index, reading ->
                                        val value = reading.valueMgDl!!
                                        val x = offsets[index].x
                                        if (value !in bottom..top && x in 0f..size.width) {
                                            val above = value > top
                                            val edgeY = if (above) plotTop else plotBottom
                                            val radius = when (index) {
                                                points.lastIndex -> 7.dp.toPx()
                                                selectedIndex -> 6.dp.toPx()
                                                else -> 3.dp.toPx()
                                            }
                                            val inward = if (above) radius * 2 else -radius * 2
                                            val marker = Path().apply {
                                                moveTo(x, edgeY)
                                                lineTo(x - radius, edgeY + inward)
                                                lineTo(x + radius, edgeY + inward)
                                                close()
                                            }
                                            drawPath(marker, valueColor(value))
                                            if (index == points.lastIndex) drawPath(
                                                marker,
                                                markerOutlineColor,
                                                style = Stroke(width = 1.dp.toPx()),
                                            )
                                        }
                                    }
                                }
                                selectedVisible?.let { reading ->
                                    val palette = readingRangePalette(limits.band(reading.valueMgDl!!))
                                    Surface(
                                        Modifier.offset(x = tooltipX ?: 0.dp).width(tooltipWidth)
                                            .clearAndSetSemantics { },
                                        shape = RoundedCornerShape(10.dp),
                                        color = palette.container,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    ) {
                                        val scaleStatus = when {
                                            reading.valueMgDl!! > top -> " · above scale"
                                            reading.valueMgDl < bottom -> " · below scale"
                                            else -> ""
                                        }
                                        Text(
                                            GlucoseFormat.withUnit(reading, unit, locale) + scaleStatus,
                                            Modifier.padding(horizontal = 6.dp, vertical = if (compact) 3.dp else 6.dp),
                                            style = if (scaleStatus.isEmpty()) MaterialTheme.typography.labelLarge
                                            else MaterialTheme.typography.labelMedium,
                                            color = palette.onContainer,
                                            textAlign = TextAlign.Center,
                                            maxLines = if (scaleStatus.isEmpty()) 1 else 2,
                                        )
                                    }
                                    Text(
                                        railTimeFormat.format(reading.measuredAt),
                                        Modifier.offset(x = timeLabelX ?: 0.dp, y = chartHeight - bottomRail + 5.dp)
                                            .width(timeLabelWidth)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                            .clearAndSetSemantics { },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                            }
                            val axisTimeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                .withLocale(locale).withZone(zoneId)
                            Box(Modifier.width(chartWidth).height(22.dp)) {
                                var previousLabelEnd = (-1).dp
                                for (tick in timeTicks) {
                                    val fraction = timeFraction(tick, now)
                                    if (fraction !in viewport.start..viewport.end) continue
                                    if (viewport.end == 1.0 && viewport.xFraction(fraction) > 0.85f) continue
                                    val labelWidth = minOf(80.dp * fontScale.coerceAtLeast(1f), chartWidth)
                                    val x = (chartWidth * viewport.xFraction(fraction) - labelWidth / 2)
                                        .coerceIn(0.dp, chartWidth - labelWidth)
                                    if (x < previousLabelEnd || (viewport.end == 1.0 && x + labelWidth > chartWidth - labelWidth)) continue
                                    previousLabelEnd = x + labelWidth
                                    Text(
                                        axisTimeFormat.format(tick),
                                        Modifier.offset(x = x).width(labelWidth),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                                if (viewport.end == 1.0) Text(
                                    "Now",
                                    Modifier.offset(x = (chartWidth - 80.dp * fontScale.coerceAtLeast(1f)).coerceAtLeast(0.dp)).width(minOf(80.dp * fontScale.coerceAtLeast(1f), chartWidth)),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().selectableGroup().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (hours in listOf(3, 6, 12, 24)) {
                        val target = ChartViewport.latest(hours)
                        val active = viewport == target
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                            Box(Modifier.heightIn(min = 48.dp).selectable(selected = active, role = Role.RadioButton,
                                onClick = {
                                    setViewport(target)
                                    if (selected == null || timeFraction(selected.measuredAt, now) !in target.start..target.end) {
                                        points.lastOrNull { timeFraction(it.measuredAt, now) in target.start..target.end }
                                            ?.let { latestOnSelect(it.measuredAt.toEpochMilli()) }
                                    }
                                }).semantics { contentDescription = "Show latest $hours hours" },
                                contentAlignment = Alignment.Center) {
                                Text("$hours hr", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (points.size == 1) {
                    Text("More readings are needed to draw a trend.", style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}

private fun axisValue(valueMgDl: Double, template: Reading, unit: GlucoseUnit, locale: Locale): String =
    GlucoseFormat.value(
        template.copy(valueMgDl = valueMgDl, sourceValue = null, specialValue = null),
        unit,
        locale,
    )

private fun plotOffset(
    reading: Reading,
    now: Instant,
    size: Size,
    plotTop: Float,
    plotBottom: Float,
    viewport: ChartViewport,
): Offset {
    val x = size.width * viewport.xFraction(timeFraction(reading.measuredAt, now))
    val yFraction = chartYFraction(reading.valueMgDl!!).toFloat()
    return Offset(x, plotTop + (plotBottom - plotTop) * yFraction)
}

private fun timeFraction(measuredAt: Instant, now: Instant): Double =
    (Duration.between(now.minus(chartWindow), measuredAt).toMillis().toDouble() /
        chartWindow.toMillis()).coerceIn(0.0, 1.0)
