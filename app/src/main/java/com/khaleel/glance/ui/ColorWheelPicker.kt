package com.khaleel.glance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun ColorWheelPicker(hex: String, onColorChange: (String) -> Unit) {
    val selected = ThemeAccent.pickerColor(hex)
    val currentHex by rememberUpdatedState(hex)
    val currentOnColorChange by rememberUpdatedState(onColorChange)
    val wheelColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan,
        Color.Blue, Color.Magenta, Color.Red,
    )
    fun selectAt(position: Offset, width: Float, height: Float) {
        val current = ThemeAccent.pickerColor(currentHex)
        val center = Offset(width / 2f, height / 2f)
        val dx = position.x - center.x
        val dy = position.y - center.y
        val radius = min(width, height) / 2f
        val distance = sqrt(dx * dx + dy * dy)
        val hue = if (distance < 1f) current.hue else
            ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 360f) % 360f)
        val saturation = (distance / radius).coerceIn(0f, 1f)
        currentOnColorChange(ThemeAccent.fromPicker(current.copy(hue = hue, saturation = saturation)))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Drag on the wheel to choose color", style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.size(220.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { selectAt(it, size.width.toFloat(), size.height.toFloat()) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { selectAt(it, size.width.toFloat(), size.height.toFloat()) },
                            onDrag = { change, _ ->
                                selectAt(change.position, size.width.toFloat(), size.height.toFloat())
                                change.consume()
                            },
                        )
                    }
                    .semantics {
                        contentDescription = "Color wheel. Drag to choose hue and saturation."
                        stateDescription = hex
                    },
            ) {
                val radius = size.minDimension / 2f
                val center = this.center
                drawCircle(Brush.sweepGradient(wheelColors, center = center), radius = radius)
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White, Color.White.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
                if (selected.brightness < 1f) {
                    drawCircle(Color.Black.copy(alpha = 1f - selected.brightness), radius = radius)
                }
                val angle = Math.toRadians(selected.hue.toDouble())
                val marker = Offset(
                    center.x + cos(angle).toFloat() * selected.saturation * radius,
                    center.y + sin(angle).toFloat() * selected.saturation * radius,
                )
                drawCircle(Color.Black, radius = 12.dp.toPx(), center = marker)
                drawCircle(Color.White, radius = 10.dp.toPx(), center = marker)
                drawCircle(ThemeAccent.color(hex), radius = 7.dp.toPx(), center = marker)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp)
                    .background(ThemeAccent.color(hex), MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .semantics { contentDescription = "Selected color $hex" },
            )
            Column(Modifier.weight(1f)) {
                Text("Brightness", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = selected.brightness,
                    onValueChange = {
                        onColorChange(ThemeAccent.fromPicker(selected.copy(brightness = it)))
                    },
                    modifier = Modifier.semantics { contentDescription = "Color brightness" },
                )
            }
        }
    }
}
