package com.khaleel.glance

import android.app.Application
import com.khaleel.glance.auth.AuthStore
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.data.AppController
import com.khaleel.glance.data.DeviceClock
import com.khaleel.glance.data.DiagnosticsStore
import com.khaleel.glance.data.LocalStateStore
import com.khaleel.glance.data.UserSettings
import com.khaleel.glance.monitoring.MonitoringDependencies
import com.khaleel.glance.monitoring.MonitoringDependencyProvider
import com.khaleel.glance.monitoring.MonitoringNotifications
import com.khaleel.glance.monitoring.MonitoringPresentation
import com.khaleel.glance.notification.GlucoseNotificationRenderer
import android.app.Notification
import android.app.Service
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class GlanceApplication : Application(), MonitoringDependencyProvider {
    private val diagnosticStore by lazy { DiagnosticsStore(this) }
    val controller: AppController by lazy {
        val encrypted = SecureFileStore(this)
        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        AppController(
            source = SourceFactory.create(this, http, DeviceClock),
            authStore = AuthStore(encrypted),
            patientStore = LocalStateStore(encrypted),
            settings = UserSettings(this),
            clock = DeviceClock,
            diagnostics = diagnosticStore,
        )
    }

    private val notifications = object : MonitoringNotifications {
        private fun renderer(presentation: MonitoringPresentation) =
            GlucoseNotificationRenderer(
                this@GlanceApplication,
                presentation.display,
                controller.settings.unit,
                controller.settings.showOnLockScreen,
                controller.settings.enhancedNotification,
            )

        override fun startingNotification(service: Service, presentation: MonitoringPresentation): Notification =
            renderer(presentation).apply { createChannel() }.build()

        override fun render(presentation: MonitoringPresentation) {
            renderer(presentation).post()
        }

        override fun hide() {
            GlucoseNotificationRenderer(
                this@GlanceApplication,
                controller.display.value,
                controller.settings.unit,
                controller.settings.showOnLockScreen,
            ).hide()
        }
    }

    override fun monitoringDependencies() = MonitoringDependencies(controller, notifications, DeviceClock)

    fun showCurrentNotification() {
        notifications.render(MonitoringPresentation(controller.display.value, 0L))
    }

    fun hideCurrentNotification() = notifications.hide()

    /** A Task Manager Stop is an explicit stop, even if persisted intent was enabled. */
    fun honorExplicitSystemStop() {
        val latest = getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(packageName, 0, 1)
            .firstOrNull() ?: return
        if (latest.reason != ApplicationExitInfo.REASON_USER_REQUESTED) return
        val prefs = getSharedPreferences("runtime_recovery", MODE_PRIVATE)
        if (latest.timestamp <= prefs.getLong("last_handled_user_stop", 0L)) return
        prefs.edit().putLong("last_handled_user_stop", latest.timestamp).apply()
        controller.setMonitoringEnabled(false)
    }
}
