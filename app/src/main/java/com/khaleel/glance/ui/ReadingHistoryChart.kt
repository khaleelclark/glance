package com.khaleel.glance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.khaleel.glance.core.GlucoseFormat
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
// Ten dp between five-minute observations keeps the trace readable without dropping data.
private val chartWidth = 2880.dp
private val chartHeight = 220.dp

internal fun chartPoints(history: List<Reading>, connectionId: String?, now: Instant): List<Reading> =
    history.filter {
        it.connectionId == connectionId &&
            it.valueMgDl?.isFinite() == true &&
            it.valueMgDl > 0.0 &&
            !it.measuredAt.isBefore(now.minus(chartWindow)) &&
            !it.measuredAt.isAfter(now)
    }.sortedBy { it.measuredAt }

/** Select only a real observation; never interpolate a value through a gap. */
internal fun nearestChartPointIndex(points: List<Reading>, now: Instant, widthPx: Float, x: Float): Int? =
    points.indices.minByOrNull { index ->
        kotlin.math.abs(widthPx * timeFraction(points[index].measuredAt, now) - x)
    }

/** Round local clock hours make the long timeline easier to scan. */
internal fun chartTimeTicks(now: Instant, zoneId: ZoneId): List<Instant> {
    val start = now.minus(chartWindow)
    var local = now.atZone(zoneId).truncatedTo(ChronoUnit.HOURS)
    if (local.hour % 2 != 0) local = local.minusHours(1)
    val result = mutableListOf<Instant>()
    while (!local.toInstant().isBefore(start)) {
        result += local.toInstant()
        local = local.minusHours(2)
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
) {
    val points = chartPoints(history, connectionId, now)
    val selectedIndex = points.indexOfFirst { it.measuredAt.toEpochMilli() == selectedTime }
        .takeIf { it >= 0 } ?: points.lastIndex
    val selected = points.getOrNull(selectedIndex)
    val latestOnSelect by rememberUpdatedState(onSelectedTimeChange)
    val timeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale).withZone(zoneId)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Glucose history", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("Last 24 hours · source readings", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.isEmpty()) {
                Text("The graph will appear after Glance receives readings for this person.")
            } else {
                val minPoint = points.minBy { it.valueMgDl!! }
                val maxPoint = points.maxBy { it.valueMgDl!! }
                val low = minPoint.valueMgDl!!
                val high = maxPoint.valueMgDl!!
                val padding = maxOf(15.0, (high - low) * 0.15)
                val bottom = (low - padding).coerceAtLeast(0.0)
                val top = high + padding
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val selectedColor = MaterialTheme.colorScheme.tertiary
                val scrollState = rememberScrollState()
                val timeTicks = chartTimeTicks(now, zoneId)
                val chartDescription = "${points.size} source readings over 24 hours. " +
                    "Observed range ${GlucoseFormat.withUnit(minPoint, unit, locale)} to " +
                    "${GlucoseFormat.withUnit(maxPoint, unit, locale)}. Drag across the graph for reading details."

                Text(
                    "Drag across the graph to see each reading. Swipe the time labels to browse 24 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.width(48.dp).height(chartHeight),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(axisValue(top, points[0], unit, locale), style = MaterialTheme.typography.labelSmall)
                        Text(axisValue((top + bottom) / 2, points[0], unit, locale), style = MaterialTheme.typography.labelSmall)
                        Text(axisValue(bottom, points[0], unit, locale), style = MaterialTheme.typography.labelSmall)
                    }
                    Box(Modifier.weight(1f)) {
                        LaunchedEffect(scrollState.maxValue) {
                            if (scrollState.value == 0 && scrollState.maxValue > 0) {
                                scrollState.scrollTo(scrollState.maxValue)
                            }
                        }
                        Column(Modifier.horizontalScroll(scrollState)) {
                            Canvas(
                                Modifier.width(chartWidth).height(chartHeight)
                                    .pointerInput(points, now) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            fun select(x: Float) {
                                                nearestChartPointIndex(points, now, size.width.toFloat(), x)
                                                    ?.let { latestOnSelect(points[it].measuredAt.toEpochMilli()) }
                                            }
                                            select(down.position.x)
                                            do {
                                                val change = awaitPointerEvent().changes
                                                    .firstOrNull { it.id == down.id }
                                                if (change?.pressed == true) select(change.position.x)
                                            } while (change?.pressed == true)
                                        }
                                    }
                                    .semantics {
                                        contentDescription = chartDescription
                                        customActions = listOf(
                                            CustomAccessibilityAction("Earlier reading") {
                                                if (selectedIndex <= 0) false else {
                                                    latestOnSelect(points[selectedIndex - 1].measuredAt.toEpochMilli())
                                                    true
                                                }
                                            },
                                            CustomAccessibilityAction("Later reading") {
                                                if (selectedIndex >= points.lastIndex) false else {
                                                    latestOnSelect(points[selectedIndex + 1].measuredAt.toEpochMilli())
                                                    true
                                                }
                                            },
                                        )
                                    },
                            ) {
                                val inset = 16.dp.toPx()
                                val offsets = points.map { plotOffset(it, now, size, bottom, top, inset) }
                                for (fraction in listOf(0f, 0.5f, 1f)) {
                                    val y = inset + (size.height - 2 * inset) * fraction
                                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                }
                                for (tick in timeTicks) {
                                    val x = size.width * timeFraction(tick, now)
                                    drawLine(gridColor, Offset(x, inset), Offset(x, size.height - inset), strokeWidth = 1.dp.toPx())
                                }
                                offsets.zipWithNext().forEachIndexed { index, (left, right) ->
                                    if (Duration.between(points[index].measuredAt, points[index + 1].measuredAt) <= gapLimit) {
                                        drawLine(lineColor, left, right, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                    }
                                }
                                offsets.getOrNull(selectedIndex)?.let { offset ->
                                    drawLine(
                                        selectedColor,
                                        Offset(offset.x, inset),
                                        Offset(offset.x, size.height - inset),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                    drawCircle(selectedColor, radius = 6.dp.toPx(), center = offset)
                                }
                            }
                            val axisTimeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                                .withLocale(locale).withZone(zoneId)
                            Box(Modifier.width(chartWidth).height(22.dp)) {
                                for (tick in timeTicks) {
                                    if (Duration.between(tick, now) < Duration.ofMinutes(45)) continue
                                    val labelWidth = 80.dp
                                    val x = (chartWidth * timeFraction(tick, now) - labelWidth / 2)
                                        .coerceIn(0.dp, chartWidth - labelWidth)
                                    Text(
                                        axisTimeFormat.format(tick),
                                        Modifier.offset(x = x).width(labelWidth),
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    "Now",
                                    Modifier.offset(x = chartWidth - 80.dp).width(80.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
                selected?.let { reading ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Selected reading", style = MaterialTheme.typography.labelMedium)
                            Text(GlucoseFormat.withUnit(reading, unit, locale), style = MaterialTheme.typography.headlineSmall)
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
    bottom: Double,
    top: Double,
    inset: Float,
): Offset {
    val x = size.width * timeFraction(reading.measuredAt, now)
    val yFraction = ((top - reading.valueMgDl!!) / (top - bottom)).toFloat().coerceIn(0f, 1f)
    return Offset(x, inset + (size.height - 2 * inset) * yFraction)
}

private fun timeFraction(measuredAt: Instant, now: Instant): Float =
    (Duration.between(now.minus(chartWindow), measuredAt).toMillis().toDouble() /
        chartWindow.toMillis()).toFloat().coerceIn(0f, 1f)
