package com.khaleel.glance.ui

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

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
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
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

/** Local clock ticks become finer when zoomed in; instants stay ordered across DST. */
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
    val chartHeight = if (compact) 170.dp else 300.dp
    val points = chartPoints(history, connectionId, now)
    val limits = chartLimits(alerts)
    var viewportStart by rememberSaveable(connectionId) { mutableStateOf(0.75) }
    var viewportSpan by rememberSaveable(connectionId) { mutableStateOf(0.25) }
    val viewport = ChartViewport(viewportStart, viewportSpan)
    fun setViewport(next: ChartViewport): Boolean {
        val changed = next != ChartViewport(viewportStart, viewportSpan)
        viewportStart = next.start
        viewportSpan = next.span
        return changed
    }
    val latestPoints by rememberUpdatedState(points)
    val latestNow by rememberUpdatedState(now)
    val selectedIndex = points.indexOfFirst { it.measuredAt.toEpochMilli() == selectedTime }
        .takeIf { it >= 0 } ?: points.lastIndex
    val selected = points.getOrNull(selectedIndex)
    val latestOnSelect by rememberUpdatedState(onSelectedTimeChange)
    val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(zoneId)

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp) {
        Column(Modifier.padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Glucose trend", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    if (!compact) Text("Source readings only", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("${(viewport.span * 24).toInt()} HR", Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (points.isEmpty()) {
                Text("The graph will appear after Glance receives readings for this person.")
            } else {
                val minPoint = points.minBy { it.valueMgDl!! }
                val maxPoint = points.maxBy { it.valueMgDl!! }
                val bottom = chartBottomMgDl
                val top = chartTopMgDl
                val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val lowColor = if (dark) Color(0xFFFF6B6B) else Color(0xFFC62828)
                val highColor = if (dark) Color(0xFFFFB74D) else Color(0xFFB85C00)
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val selectedColor = MaterialTheme.colorScheme.tertiary
                fun valueColor(valueMgDl: Double): Color = when (limits.band(valueMgDl)) {
                    ChartBand.LOW -> lowColor
                    ChartBand.HIGH -> highColor
                    ChartBand.IN_RANGE -> lineColor
                }
                val limitLegend = "Low: ${axisValue(limits.lowMgDl, points[0], unit, locale)} ${unit.label} · " +
                    "High: ${axisValue(limits.highMgDl, points[0], unit, locale)} ${unit.label}"
                val scaleLegend = "Scale: ${axisValue(bottom, points[0], unit, locale)}–" +
                    "${axisValue(top, points[0], unit, locale)} ${unit.label}"
                val timeTicks = chartTimeTicks(now, zoneId, viewport.span)
                val chartDescription = "${points.size} source readings over 24 hours. " +
                    "Observed range ${GlucoseFormat.withUnit(minPoint, unit, locale)} to " +
                    "${GlucoseFormat.withUnit(maxPoint, unit, locale)}. $scaleLegend. $limitLegend. Red below Low; orange above High. Touch to inspect readings. Pinch to zoom. Use the 6, 12 or 24 hour choices to reset the time window."

                if (!compact) Text(
                    "Touch a point to inspect · Pinch to zoom",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Low ${axisValue(limits.lowMgDl, points[0], unit, locale)}", color = lowColor, style = MaterialTheme.typography.labelMedium)
                    Text("High ${axisValue(limits.highMgDl, points[0], unit, locale)}", color = highColor, style = MaterialTheme.typography.labelMedium)
                    Text(unit.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
                if (limits.overlaps) Text("Low and High graph limits overlap; red takes priority.",
                    style = MaterialTheme.typography.bodySmall)
                if (limits.lowMgDl !in bottom..top || limits.highMgDl !in bottom..top) {
                    Text("A limit is outside the graph scale. $scaleLegend.", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth()) {
                    Box(Modifier.width(48.dp).height(chartHeight)) {
                        for (value in chartAxisValues(limits, if (compact) 60.0 else 30.0)) {
                            val y = 16.dp + (chartHeight - 32.dp) * chartYFraction(value).toFloat() - 8.dp
                            Text(axisValue(value, points[0], unit, locale), Modifier.offset(y = y),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val chartWidth = maxWidth
                        Column(
                            Modifier.fillMaxWidth().pointerInput(connectionId) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    fun select(x: Float) {
                                        nearestChartPointIndex(latestPoints, latestNow, size.width.toFloat(), x, ChartViewport(viewportStart, viewportSpan))
                                            ?.let { latestOnSelect(latestPoints[it].measuredAt.toEpochMilli()) }
                                    }
                                    // Once a second finger joins, never scrub with the remaining finger.
                                    var transformed = false
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.size >= 2) {
                                            transformed = true
                                            val centroid = event.calculateCentroid(useCurrent = false)
                                            setViewport(ChartViewport(viewportStart, viewportSpan).transform(
                                                0.0, // Two-finger translation never scrolls the timeline.
                                                event.calculateZoom().toDouble(),
                                                centroid.x.toDouble() / size.width,
                                            ))
                                        } else if (!transformed) {
                                            event.changes.firstOrNull { it.id == down.id }?.let { select(it.position.x) }
                                        }
                                        event.changes.forEach { it.consume() }
                                    } while (pressed.isNotEmpty())
                                }
                            },
                        ) {
                            Canvas(
                                Modifier.fillMaxWidth().height(chartHeight)
                                    .semantics {
                                        contentDescription = chartDescription
                                        stateDescription = String.format(Locale.ROOT, "Showing %.1f hours", viewport.span * 24) +
                                            ", from " + timeFormat.format(now.minusMillis(((1.0 - viewport.start) * chartWindow.toMillis()).toLong())) +
                                            " to " + timeFormat.format(now.minusMillis(((1.0 - viewport.end) * chartWindow.toMillis()).toLong()))
                                        fun reveal(index: Int): Boolean {
                                            if (index !in points.indices) return false
                                            val fraction = timeFraction(points[index].measuredAt, now)
                                            if (fraction !in viewport.start..viewport.end) {
                                                setViewport(ChartViewport(
                                                    (fraction - viewport.span / 2).coerceIn(0.0, 1.0 - viewport.span), viewport.span,
                                                ))
                                            }
                                            latestOnSelect(points[index].measuredAt.toEpochMilli())
                                            return true
                                        }
                                        customActions = listOf(
                                            CustomAccessibilityAction("Earlier reading") { reveal(selectedIndex - 1) },
                                            CustomAccessibilityAction("Later reading") { reveal(selectedIndex + 1) },
                                            CustomAccessibilityAction("Earlier time") {
                                                setViewport(viewport.transform(0.5, 1.0, 0.5))
                                            },
                                            CustomAccessibilityAction("Later time") {
                                                setViewport(viewport.transform(-0.5, 1.0, 0.5))
                                            },
                                            CustomAccessibilityAction("Zoom in") {
                                                setViewport(viewport.transform(0.0, 2.0, 0.5))
                                            },
                                            CustomAccessibilityAction("Zoom out") {
                                                setViewport(viewport.transform(0.0, 0.5, 0.5))
                                            },
                                        )
                                    },
                            ) {
                                val inset = 16.dp.toPx()
                                val offsets = points.map { plotOffset(it, now, size, inset, viewport) }
                                fun y(value: Double): Float = inset + (size.height - 2 * inset) * chartYFraction(value).toFloat()
                                val plotBottom = size.height - inset
                                val lowY = y(limits.lowMgDl).coerceIn(inset, plotBottom)
                                val highY = y(limits.highMgDl).coerceIn(inset, plotBottom)
                                clipRect(top = inset, bottom = plotBottom) {
                                    drawRect(highColor.copy(alpha = 0.08f), Offset(0f, inset), Size(size.width, minOf(highY, lowY) - inset))
                                    drawRect(lowColor.copy(alpha = 0.08f), Offset(0f, lowY), Size(size.width, plotBottom - lowY))
                                    for (value in listOf(top, bottom)) {
                                        drawLine(gridColor, Offset(0f, y(value)), Offset(size.width, y(value)), strokeWidth = 1.dp.toPx())
                                    }
                                    for (tick in timeTicks) {
                                        val x = size.width * viewport.xFraction(timeFraction(tick, now))
                                        drawLine(gridColor, Offset(x, inset), Offset(x, plotBottom), strokeWidth = 1.dp.toPx())
                                    }
                                    for ((value, color) in listOf(limits.highMgDl to highColor, limits.lowMgDl to lowColor)) {
                                        if (value in bottom..top) drawLine(color, Offset(0f, y(value)), Offset(size.width, y(value)), strokeWidth = 1.dp.toPx())
                                    }
                                    fun trace(color: Color) {
                                        offsets.zipWithNext().forEachIndexed { index, (left, right) ->
                                            if (Duration.between(points[index].measuredAt, points[index + 1].measuredAt) <= gapLimit) {
                                                drawLine(color, left, right, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                            }
                                        }
                                    }
                                    trace(lineColor)
                                    clipRect(bottom = highY) { trace(highColor) }
                                    // Color clipping changes only presentation, never source observations.
                                    clipRect(top = lowY) { trace(lowColor) }
                                    offsets.getOrNull(selectedIndex)?.let { offset ->
                                        drawLine(selectedColor, Offset(offset.x, inset), Offset(offset.x, plotBottom), strokeWidth = 1.dp.toPx())
                                        if (points[selectedIndex].valueMgDl!! in bottom..top) {
                                            drawCircle(valueColor(points[selectedIndex].valueMgDl!!), radius = 6.dp.toPx(), center = offset)
                                        }
                                    }
                                }
                                // Out-of-scale source values get edge arrows, never a fabricated 40/350 reading.
                                clipRect {
                                    points.forEachIndexed { index, reading ->
                                        val value = reading.valueMgDl!!
                                        if (value !in bottom..top) {
                                            val x = offsets[index].x
                                            val above = value > top
                                            val edgeY = if (above) inset else plotBottom
                                            val radius = if (index == selectedIndex) 6.dp.toPx() else 3.dp.toPx()
                                            val inward = if (above) radius * 2 else -radius * 2
                                            val marker = Path().apply {
                                                moveTo(x, edgeY)
                                                lineTo(x - radius, edgeY + inward)
                                                lineTo(x + radius, edgeY + inward)
                                                close()
                                            }
                                            drawPath(marker, valueColor(value))
                                        }
                                    }
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
                                    val labelWidth = minOf(80.dp, chartWidth)
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
                                    Modifier.offset(x = (chartWidth - 80.dp).coerceAtLeast(0.dp)).width(minOf(80.dp, chartWidth)),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().selectableGroup().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (hours in listOf(6, 12, 24)) {
                        val target = ChartViewport.latest(hours)
                        val active = viewport == target
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                            Box(Modifier.heightIn(min = 48.dp).selectable(selected = active, role = Role.RadioButton,
                                onClick = { setViewport(target) }).semantics { contentDescription = "Show latest $hours hours" },
                                contentAlignment = Alignment.Center) {
                                Text("$hours hr", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                selected?.let { reading ->
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Selected reading", style = MaterialTheme.typography.labelMedium)
                            Text(GlucoseFormat.withUnit(reading, unit, locale), style = MaterialTheme.typography.headlineSmall,
                                color = valueColor(reading.valueMgDl!!))
                            when (limits.band(reading.valueMgDl)) {
                                ChartBand.LOW -> Text("Below Low limit", style = MaterialTheme.typography.bodySmall)
                                ChartBand.HIGH -> Text("Above High limit", style = MaterialTheme.typography.bodySmall)
                                ChartBand.IN_RANGE -> Unit
                            }
                            if (reading.valueMgDl !in bottom..top) Text("Outside graph scale · $scaleLegend", style = MaterialTheme.typography.bodySmall)
                            Text(timeFormat.format(reading.measuredAt), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (points.size == 1) {
                    Text("More readings are needed to draw a trend.", style = MaterialTheme.typography.bodySmall)
                }
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
    inset: Float,
    viewport: ChartViewport,
): Offset {
    val x = size.width * viewport.xFraction(timeFraction(reading.measuredAt, now))
    val yFraction = chartYFraction(reading.valueMgDl!!).toFloat()
    return Offset(x, inset + (size.height - 2 * inset) * yFraction)
}

private fun timeFraction(measuredAt: Instant, now: Instant): Double =
    (Duration.between(now.minus(chartWindow), measuredAt).toMillis().toDouble() /
        chartWindow.toMillis()).coerceIn(0.0, 1.0)
