package com.khaleel.glance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
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
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = height.sp,
    letterSpacing = 0.sp,
)

internal val GlanceTypography = Typography(
    displayLarge = type(68, 72, FontWeight.Bold),
    displayMedium = type(44, 50, FontWeight.SemiBold),
    headlineLarge = type(36, 42, FontWeight.Bold),
    headlineMedium = type(28, 34, FontWeight.SemiBold),
    headlineSmall = type(23, 29, FontWeight.SemiBold),
    titleLarge = type(20, 27, FontWeight.SemiBold),
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
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
