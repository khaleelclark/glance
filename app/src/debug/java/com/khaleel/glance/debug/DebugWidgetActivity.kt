package com.khaleel.glance.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import com.khaleel.glance.GlanceApplication
import com.khaleel.glance.core.*
import com.khaleel.glance.widget.GlucoseWidgetContent
import com.khaleel.glance.widget.GlucoseWidgetReceiver
import com.khaleel.glance.widget.WidgetContentFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.sin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow

/** Actual Android AppWidgetHost for repeatable debug-only visuals; not an Android Auto emulator. */
class DebugWidgetActivity : Activity() {
    private val host by lazy { AppWidgetHost(this, 1700) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val light = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK !=
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        window.decorView.windowInsetsController?.setSystemBarsAppearance(
            if (light) android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        val mode = intent.getStringExtra("mode") ?: "fresh"
        val width = intent.getIntExtra("width", 360).coerceIn(180, 400)
        val height = intent.getIntExtra("height", 220).coerceIn(110, 400)
        val integration = mode == "integration"
        val prefs = getSharedPreferences("debug_widget", MODE_PRIVATE)
        prefs.edit().putString("mode", mode).putBoolean("mmol", intent.getBooleanExtra("mmol", false)).commit()
        DebugWidget.selection.value = mode to intent.getBooleanExtra("mmol", false)
        val component = ComponentName(this, if (integration) GlucoseWidgetReceiver::class.java else DebugWidgetReceiver::class.java)
        val manager = AppWidgetManager.getInstance(this)
        val key = if (integration) "real_id" else "fixture_id"
        var id = prefs.getInt(key, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (manager.getAppWidgetInfo(id) == null) {
            id = host.allocateAppWidgetId()
            if (!manager.bindAppWidgetIdIfAllowed(id, component)) {
                setContentView(TextView(this).apply { text = "DEMO host requires adb shell appwidget grantbind --package com.khaleel.glance" })
                host.deleteAppWidgetId(id)
                return
            }
            prefs.edit().putInt(key, id).commit()
        }
        val info = manager.getAppWidgetInfo(id)
        val view = host.createView(this, id, info)
        view.updateAppWidgetSize(Bundle(), width, height, width, height)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(24), dp(12), 0)
            addView(TextView(context).apply {
                text = "DEMO · Android AppWidget host\n$mode · ${width}×${height} dp\nThis is not an Android Auto screen"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })
            addView(view, LinearLayout.LayoutParams(dp(width), dp(height)))
        }
        setContentView(layout)
        if (integration) {
            val app = application as GlanceApplication
            app.widgetPreferences.valuesVisible = intent.getBooleanExtra("visible", true)
            if (intent.hasExtra("mmol")) app.controller.settings.unit =
                if (intent.getBooleanExtra("mmol", false)) GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL
            app.updateWidgets()
        } else scope.launch { DebugWidget().updateAll(this@DebugWidgetActivity) }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onResume() { super.onResume(); host.startListening() }
    override fun onPause() { host.stopListening(); super.onPause() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

class DebugWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = DebugWidget()
}

class DebugWidget : GlanceAppWidget() {
    companion object { val selection = MutableStateFlow("fresh" to false) }
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("debug_widget", Context.MODE_PRIVATE)
        selection.value = prefs.getString("mode", "fresh").orEmpty() to prefs.getBoolean("mmol", false)
        provideContent {
        val selected by selection.collectAsState()
        val mode = selected.first
        val now = Instant.now()
        val age = when (mode) { "delayed" -> 7L; "stale" -> 18L; else -> 1L }
        val reading = Reading("synthetic-widget", now.minusSeconds(age * 60), 130.0, 130.0,
            GlucoseUnit.MG_DL, if (mode == "special") SpecialValue.HIGH else null,
            Trend.STEADY, "synthetic-current", now)
        val history = (1..48).filterNot { it in 20..25 }.map { step ->
            val value = if (step == 1) 137.0 else 120 + 18 * sin(step / 6.0)
            reading.copy(measuredAt = reading.measuredAt.minusSeconds(step * 300L),
                valueMgDl = value, sourceValue = value, sourceRecordId = "synthetic-$step")
        }
        val state = DisplayState(mode != "stopped", RuntimeState.RUNNING,
            if (mode == "offline") ConnectionState.OFFLINE else ConnectionState.READY,
            Connection(if (mode == "wrong-person") "other" else "synthetic-widget", "DEMO · Example"),
            reading, ReadingFreshness.FRESH, now, null, history)
        val content = WidgetContentFormatter.format(state,
            if (selected.second) GlucoseUnit.MMOL_L else GlucoseUnit.MG_DL,
            mode != "hidden", true, now)
        GlanceTheme { GlucoseWidgetContent(content) }
        }
    }
}
