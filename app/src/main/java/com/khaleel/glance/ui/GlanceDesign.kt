package com.khaleel.glance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GlanceShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(13.dp),
    medium = RoundedCornerShape(17.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp,
)

internal val GlanceTypography = Typography(
    displayLarge = type(64, 68, FontWeight.Bold),
    displayMedium = type(42, 48, FontWeight.SemiBold),
    headlineLarge = type(34, 40, FontWeight.Bold),
    headlineMedium = type(27, 33, FontWeight.SemiBold),
    headlineSmall = type(22, 28, FontWeight.SemiBold),
    titleLarge = type(20, 26, FontWeight.SemiBold),
    titleMedium = type(16, 23, FontWeight.SemiBold),
    bodyLarge = type(16, 24),
    bodyMedium = type(14, 21),
    bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.SemiBold),
    labelSmall = type(12, 16, FontWeight.Medium),
)

@Composable
internal fun GlanceButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp),
    enabled = enabled,
    shape = GlanceShapes.medium,
    contentPadding = ButtonDefaults.ContentPadding,
    content = content,
)

@Composable
internal fun GlanceOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick,
    modifier = modifier.heightIn(min = 48.dp),
    enabled = enabled,
    shape = GlanceShapes.medium,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    content = content,
)

/** The standard layered surface used across setup, the reading hero, and settings groups. */
@Composable
internal fun GlanceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = GlanceShapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun SectionTitle(title: String, summary: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
        summary?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A small native mark used in-app; launcher and status variants live in vector resources. */
@Composable
internal fun GlanceMark(modifier: Modifier = Modifier, inverse: Boolean = false) {
    val background = if (inverse) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val foreground = if (inverse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = modifier.size(42.dp).background(background, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(27.dp)) {
            val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
            val inset = 3.dp.toPx()
            drawArc(
                color = foreground,
                startAngle = 38f,
                sweepAngle = 284f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = stroke,
            )
            drawLine(
                color = foreground,
                start = Offset(size.width * .54f, size.height * .55f),
                end = Offset(size.width * .83f, size.height * .55f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = foreground,
                start = Offset(size.width * .82f, size.height * .55f),
                end = Offset(size.width * .82f, size.height * .76f),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

internal enum class GlanceGlyph { NOW, LOGBOOK, SETTINGS, REFRESH, BACK }

/** Dependency-free line icons keep the UI crisp without importing a second icon system. */
@Composable
internal fun GlanceGlyphIcon(
    glyph: GlanceGlyph,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Canvas(modifier.size(24.dp)) {
        val line = 2.dp.toPx()
        val stroke = Stroke(line, cap = StrokeCap.Round)
        when (glyph) {
            GlanceGlyph.NOW -> {
                drawCircle(color, size.minDimension * .34f, center, style = stroke)
                drawCircle(color, line * .9f, center)
            }
            GlanceGlyph.LOGBOOK -> {
                drawRoundRect(color, Offset(size.width * .2f, size.height * .14f), Size(size.width * .6f, size.height * .72f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(line * 2), style = stroke)
                repeat(3) { index ->
                    val y = size.height * (.34f + index * .17f)
                    drawLine(color, Offset(size.width * .34f, y), Offset(size.width * .68f, y), line, StrokeCap.Round)
                }
            }
            GlanceGlyph.SETTINGS -> {
                listOf(.28f to .65f, .50f to .38f, .72f to .58f).forEach { (y, knob) ->
                    drawLine(color, Offset(size.width * .18f, size.height * y), Offset(size.width * .82f, size.height * y), line, StrokeCap.Round)
                    drawCircle(color, line * 1.45f, Offset(size.width * knob, size.height * y))
                }
            }
            GlanceGlyph.REFRESH -> {
                drawArc(color, -55f, 280f, false,
                    topLeft = Offset(size.width * .18f, size.height * .18f),
                    size = Size(size.width * .64f, size.height * .64f), style = stroke)
                val tip = Offset(size.width * .77f, size.height * .16f)
                drawLine(color, tip, Offset(size.width * .61f, size.height * .18f), line, StrokeCap.Round)
                drawLine(color, tip, Offset(size.width * .77f, size.height * .32f), line, StrokeCap.Round)
            }
            GlanceGlyph.BACK -> {
                drawLine(color, Offset(size.width * .72f, size.height * .18f), Offset(size.width * .30f, size.height * .50f), line, StrokeCap.Round)
                drawLine(color, Offset(size.width * .30f, size.height * .50f), Offset(size.width * .72f, size.height * .82f), line, StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun StatusMessage(text: String, isError: Boolean = false) {
    val background = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val foreground = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = GlanceShapes.medium,
        colors = CardDefaults.cardColors(containerColor = background),
        border = BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp).semantics {
                contentDescription = text
            },
            color = foreground,
        )
    }
}

/** Optional consolidated guidance; never changes a saved setting. */
@Composable
internal fun HelpDetails(title: String, text: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column {
        androidx.compose.material3.TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = title
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        ) {
            Text(if (expanded) "$title −" else "$title +", style = MaterialTheme.typography.labelLarge)
        }
        if (expanded) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
