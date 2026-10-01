package com.khaleel.glance.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import com.khaleel.glance.GlanceApplication
import com.khaleel.glance.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.time.Instant

/** Snapshot rendering avoids a 45-second Glance/WorkManager session for every source update. */
class WidgetUpdates(private val app: GlanceApplication) {
    private val gate = Mutex()
    private val publication = WidgetPublication()
    private val manager get() = AppWidgetManager.getInstance(app)
    private fun ids() = manager.getAppWidgetIds(ComponentName(app, GlucoseWidgetReceiver::class.java))

    /** Clear the host's old sensitive view before an opt-out or identity transition. */
    fun redact() {
        runCatching {
            publication.redact {
                manager.updateAppWidget(ids(), RemoteViews(app.packageName, R.layout.widget_redacted))
            }
        }.onFailure {
            android.util.Log.w("GlanceWidget", "Widget redaction failed: ${it.javaClass.simpleName}")
        }
    }

    suspend fun render() {
        try {
            withTimeout(8_000L) {
                gate.withLock {
                    val idManager = GlanceAppWidgetManager(app)
                    for (id in ids()) {
                        if (com.khaleel.glance.BuildConfig.DEBUG) android.util.Log.d("GlanceWidget", "Snapshot composition started")
                        if (manager.getAppWidgetInfo(id) == null) continue
                        val generation = publication.generation()
                        val state = app.controller.display.value
                        val unit = app.controller.settings.unit
                        val visible = app.widgetPreferences.valuesVisible
                        val account = app.controller.hasAccount
                        val content = WidgetContentFormatter.format(state, unit, visible, account, Instant.now())
                        val views = GlucoseWidget(content).compose(
                            app, idManager.getGlanceIdBy(id), options = manager.getAppWidgetOptions(id)
                        )
                        // A person switch, opt-out or newer state may arrive during composition.
                        // Never publish its preceding snapshot after that transition.
                        val published = publication.publish(generation, stillCurrent = {
                            state == app.controller.display.value && unit == app.controller.settings.unit &&
                                visible == app.widgetPreferences.valuesVisible && account == app.controller.hasAccount
                        }) { manager.updateAppWidget(id, views) }
                        if (!published) {
                            redact()
                            if (com.khaleel.glance.BuildConfig.DEBUG) android.util.Log.d("GlanceWidget", "Snapshot discarded after transition")
                            continue
                        }
                        if (com.khaleel.glance.BuildConfig.DEBUG) android.util.Log.d("GlanceWidget", "Snapshot published")
                    }
                }
            }
        } catch (failure: Exception) {
            android.util.Log.w("GlanceWidget", "Widget rendering failed: ${failure.javaClass.simpleName}")
            if (com.khaleel.glance.BuildConfig.DEBUG) failure.stackTrace.forEach {
                android.util.Log.w("GlanceWidget", it.toString())
            }
            // An update failure must not leave a patient-bearing cached card in place.
            runCatching { redact() }
        }
    }
}

/** Ordinary AppWidget events render snapshots without starting monitoring or a network worker. */
class GlucoseWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context)

    private fun render(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { (context.applicationContext as GlanceApplication).widgetRenderer.render() }
            finally { pending.finish() }
        }
    }
}
