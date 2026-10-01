package com.khaleel.glance.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.ContentScale
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.khaleel.glance.GlanceApplication
import java.time.Instant

/** Passive AppWidget: Android Auto can host it when its car-widget feature is available. */
class GlucoseWidget internal constructor(private val snapshot: WidgetContent? = null) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as GlanceApplication
        val content = snapshot ?: WidgetContentFormatter.format(
                app.controller.display.value, app.controller.settings.unit, app.widgetPreferences.valuesVisible,
                app.controller.hasAccount, Instant.now(),
            )
        provideContent {
            GlanceTheme {
                GlucoseWidgetContent(content)
            }
        }
    }
}

@Composable
internal fun GlucoseWidgetContent(model: WidgetContent) {
    val size = LocalSize.current
    val context = LocalContext.current
    val compact = size.height < 160.dp
    val valueSize = when {
        model.unit.isEmpty() -> if (compact) 18.sp else 26.sp
        compact -> 28.sp
        size.width < 260.dp -> 36.sp
        else -> 46.sp
    }
    val unitBesideValue = !compact && size.width >= 260.dp
    val footer = listOfNotNull(model.note, model.unit.takeIf { it.isNotEmpty() && !unitBesideValue }, model.measured).joinToString(" · ")
    val fontScale = context.resources.configuration.fontScale
    val footerLines = if (size.width < 280.dp || model.note != null) 2 else 1
    val neededHeight = (24 + (12 * 1.35f + valueSize.value * 1.2f + 12 * 1.3f * footerLines) * fontScale + 6 + 60).dp
    val graphFits = !compact && size.height >= neededHeight && size.width >= 200.dp
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground).cornerRadius(20.dp).padding(if (compact) 8.dp else 12.dp)
            .semantics { contentDescription = model.accessibility },
    ) {
        if (!compact) Text(model.heading, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(model.value, style = TextStyle(color = GlanceTheme.colors.primary,
                fontSize = valueSize, fontWeight = FontWeight.Bold), maxLines = 1)
            if (model.arrow.isNotEmpty()) {
                Spacer(GlanceModifier.width(4.dp))
                Text(model.arrow, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = valueSize), maxLines = 1)
            }
            if (model.unit.isNotEmpty() && unitBesideValue) {
                Spacer(GlanceModifier.width(6.dp))
                Column {
                    Text(model.unit, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), maxLines = 1)
                    if (size.width >= 280.dp) model.delta?.let {
                        Text(it, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp), maxLines = 1)
                    }
                }
            }
        }
        Text(footer, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = if (compact) 11.sp else 12.sp), maxLines = 2)
        if (graphFits) {
            WidgetHistoryGraph.create(
                model.samples, GlanceTheme.colors.primary.getColor(context).toArgb(),
                GlanceTheme.colors.onSurfaceVariant.getColor(context).toArgb(),
                GlanceTheme.colors.outline.getColor(context).copy(alpha = 0.3f).toArgb(),
            )?.let { graph ->
                Spacer(GlanceModifier.height(6.dp))
                Image(ImageProvider(graph), "Four hours of source glucose history ending at the displayed measurement. Gaps over 15 minutes are not joined.",
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentScale = ContentScale.FillBounds)
            }
        }
    }
}
