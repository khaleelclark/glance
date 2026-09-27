package com.khaleel.glance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
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
import java.util.Locale

internal val chartWindow: Duration = Duration.ofHours(24)
private val gapLimit = Duration.ofMinutes(20)
private val chartWidth = 1440.dp
private val chartHeight = 220.dp

internal fun chartPoints(history: List<Reading>, connectionId: String?, now: Instant): List<Reading> =
    history.filter {
        it.connectionId == connectionId &&
            it.valueMgDl?.isFinite() == true &&
            it.valueMgDl > 0.0 &&
            !it.measuredAt.isBefore(now.minus(chartWindow)) &&
            !it.measuredAt.isAfter(now)
    }.sortedBy { it.measuredAt }

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
                val density = LocalDensity.current
                val scrollState = rememberScrollState()
                val chartDescription = "${points.size} source readings over 24 hours. " +
                    "Observed range ${GlucoseFormat.withUnit(minPoint, unit, locale)} to " +
                    "${GlucoseFormat.withUnit(maxPoint, unit, locale)}. Swipe to browse and tap a dot for details."

                Text(
                    "Swipe sideways to browse; tap a dot for its time and reading.",
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
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val viewportWidthPx = constraints.maxWidth
                        LaunchedEffect(selectedIndex, scrollState.maxValue, viewportWidthPx) {
                            if (selectedIndex < 0 || scrollState.maxValue == 0) return@LaunchedEffect
                            val x = with(density) {
                                chartWidth.toPx() * timeFraction(points[selectedIndex].measuredAt, now)
                            }
                            scrollState.animateScrollTo(
                                (x - viewportWidthPx / 2f).toInt().coerceIn(0, scrollState.maxValue),
                            )
                        }
                        Column(Modifier.horizontalScroll(scrollState)) {
                            Canvas(
                                Modifier.width(chartWidth).height(chartHeight)
                                    .pointerInput(points, now, bottom, top) {
                                        detectTapGestures { tap ->
                                            val plotSize = Size(size.width.toFloat(), size.height.toFloat())
                                            val offsets = points.map { point ->
                                                plotOffset(point, now, plotSize, bottom, top, 16.dp.toPx())
                                            }
                                            val radius = 28.dp.toPx()
                                            val nearest = offsets.indices.minByOrNull { index ->
                                                val dx = offsets[index].x - tap.x
                                                val dy = offsets[index].y - tap.y
                                                dx * dx + dy * dy
                                            }
                                            if (nearest != null) {
                                                val dx = offsets[nearest].x - tap.x
                                                val dy = offsets[nearest].y - tap.y
                                                if (dx * dx + dy * dy <= radius * radius) {
                                                    onSelectedTimeChange(points[nearest].measuredAt.toEpochMilli())
                                                }
                                            }
                                        }
                                    }
                                    .semantics { contentDescription = chartDescription },
                            ) {
                                val inset = 16.dp.toPx()
                                val offsets = points.map { plotOffset(it, now, size, bottom, top, inset) }
                                for (fraction in listOf(0f, 0.5f, 1f)) {
                                    val y = inset + (size.height - 2 * inset) * fraction
                                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                                }
                                for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                                    val x = size.width * fraction
                                    drawLine(gridColor, Offset(x, inset), Offset(x, size.height - inset), strokeWidth = 1.dp.toPx())
                                }
                                offsets.zipWithNext().forEachIndexed { index, (left, right) ->
                                    if (Duration.between(points[index].measuredAt, points[index + 1].measuredAt) <= gapLimit) {
                                        drawLine(lineColor, left, right, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                    }
                                }
                                offsets.forEachIndexed { index, offset ->
                                    drawCircle(
                                        if (index == selectedIndex) selectedColor else lineColor,
                                        radius = if (index == selectedIndex) 6.dp.toPx() else 3.dp.toPx(),
                                        center = offset,
                                    )
                                }
                                offsets.getOrNull(selectedIndex)?.let { offset ->
                                    drawLine(
                                        selectedColor,
                                        Offset(offset.x, inset),
                                        Offset(offset.x, size.height - inset),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                            }
                            Row(Modifier.width(chartWidth), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("24h ago", "18h", "12h", "6h", "Now").forEach {
                                    Text(it, style = MaterialTheme.typography.labelSmall)
                                }
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
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(
                                    onClick = { onSelectedTimeChange(points[selectedIndex - 1].measuredAt.toEpochMilli()) },
                                    enabled = selectedIndex > 0,
                                ) { Text("Earlier point") }
                                TextButton(
                                    onClick = { onSelectedTimeChange(points[selectedIndex + 1].measuredAt.toEpochMilli()) },
                                    enabled = selectedIndex < points.lastIndex,
                                ) { Text("Later point") }
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
