package com.khaleel.glance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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

private val chartWindow = Duration.ofHours(12)
private val gapLimit = Duration.ofMinutes(20)

@Composable
internal fun ReadingHistoryChart(
    history: List<Reading>,
    connectionId: String?,
    unit: GlucoseUnit,
    now: Instant,
    zoneId: ZoneId,
    locale: Locale,
) {
    val points = history.filter {
        it.connectionId == connectionId &&
            it.valueMgDl?.isFinite() == true &&
            it.measuredAt.isAfter(now.minus(chartWindow)) &&
            !it.measuredAt.isAfter(now)
    }.sortedBy { it.measuredAt }
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(locale).withZone(zoneId)

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Glucose history", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("Last 12 hours · source readings", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.isEmpty()) {
                Text("The graph will appear after Glance receives readings for this person.")
            } else {
                val first = points.first()
                val last = points.last()
                val minPoint = points.minBy { it.valueMgDl!! }
                val maxPoint = points.maxBy { it.valueMgDl!! }
                val low = minPoint.valueMgDl!!
                val high = maxPoint.valueMgDl!!
                val padding = maxOf(15.0, (high - low) * 0.15)
                val bottom = (low - padding).coerceAtLeast(0.0)
                val top = high + padding
                val lineColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                val chartDescription = "${points.size} readings from ${timeFormat.format(first.measuredAt)} " +
                    "to ${timeFormat.format(last.measuredAt)}. " +
                    "Observed range ${GlucoseFormat.withUnit(minPoint, unit, locale)} " +
                    "to ${GlucoseFormat.withUnit(maxPoint, unit, locale)}."

                Canvas(
                    Modifier.fillMaxWidth().height(180.dp).semantics {
                        contentDescription = chartDescription
                    },
                ) {
                    for (fraction in listOf(0.15f, 0.5f, 0.85f)) {
                        val y = size.height * fraction
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    val offsets = points.map { reading ->
                        val elapsed = Duration.between(now.minus(chartWindow), reading.measuredAt).toMillis()
                        val x = (elapsed.toDouble() / chartWindow.toMillis()).toFloat().coerceIn(0f, 1f)
                        val y = ((top - reading.valueMgDl!!) / (top - bottom)).toFloat().coerceIn(0f, 1f)
                        val inset = 4.dp.toPx()
                        Offset(inset + (size.width - 2 * inset) * x, size.height * (0.1f + 0.8f * y))
                    }
                    offsets.zipWithNext().forEachIndexed { index, (left, right) ->
                        if (Duration.between(points[index].measuredAt, points[index + 1].measuredAt) <= gapLimit) {
                            drawLine(lineColor, left, right, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                        }
                    }
                    offsets.forEach { drawCircle(lineColor, radius = 3.dp.toPx(), center = it) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("12 hours ago", style = MaterialTheme.typography.labelMedium)
                    Text("Now", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "Observed range: ${GlucoseFormat.withUnit(minPoint, unit, locale)}–" +
                        GlucoseFormat.withUnit(maxPoint, unit, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (points.size == 1) {
                    Text("More readings are needed to draw a trend.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
