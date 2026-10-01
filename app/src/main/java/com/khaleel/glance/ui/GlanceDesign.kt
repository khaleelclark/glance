package com.khaleel.glance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GlanceShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight,
    fontSize = size.sp, lineHeight = height.sp, letterSpacing = 0.sp,
)

internal val GlanceTypography = Typography(
    displayLarge = type(64, 72, FontWeight.Light),
    displayMedium = type(42, 50, FontWeight.Light),
    headlineLarge = type(36, 44, FontWeight.Medium),
    headlineMedium = type(28, 36, FontWeight.Medium),
    headlineSmall = type(23, 30, FontWeight.Medium),
    titleLarge = type(20, 28, FontWeight.Medium),
    titleMedium = type(16, 24, FontWeight.Medium),
    bodyLarge = type(16, 24), bodyMedium = type(14, 21), bodySmall = type(12, 18),
    labelLarge = type(14, 20, FontWeight.Medium), labelSmall = type(12, 16),
)

@Composable
internal fun GlanceButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(
    onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
    shape = GlanceShapes.medium, content = content,
)

@Composable
internal fun GlanceOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
    shape = GlanceShapes.medium,
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
    content = content,
)

/** Help is separate from setting controls: expanding it never changes a saved choice. */
@Composable
internal fun HelpDetails(title: String, text: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column {
        androidx.compose.material3.TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            },
        ) { Text(if (expanded) "$title −" else "$title +", style = MaterialTheme.typography.labelLarge) }
        if (expanded) Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
