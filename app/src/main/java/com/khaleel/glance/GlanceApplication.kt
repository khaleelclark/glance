package com.khaleel.glance

import android.app.Application
import com.khaleel.glance.auth.AuthStore
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.alerts.AlertEngine
import com.khaleel.glance.alerts.AlertPreferences
import com.khaleel.glance.alerts.GlucoseAlertNotifier
import com.khaleel.glance.alerts.EncryptedAlertHistoryStore
import com.khaleel.glance.data.AppController
import com.khaleel.glance.data.DeviceClock
import com.khaleel.glance.data.DiagnosticsStore
import com.khaleel.glance.data.LocalStateStore
import com.khaleel.glance.data.LocalNicknameStore
import com.khaleel.glance.data.UserSettings
import com.khaleel.glance.monitoring.MonitoringDependencies
import com.khaleel.glance.monitoring.MonitoringAlerts
import com.khaleel.glance.monitoring.MonitoringDependencyProvider
import com.khaleel.glance.monitoring.MonitoringNotifications
import com.khaleel.glance.monitoring.MonitoringPresentation
import com.khaleel.glance.monitoring.MonitoringService
import com.khaleel.glance.notification.GlucoseNotificationRenderer
import com.khaleel.glance.notification.NotificationVisibility
import com.khaleel.glance.notification.NotificationPublication
import com.khaleel.glance.notification.NotificationDismissal
import com.khaleel.glance.notification.DismissalAction
import com.khaleel.glance.core.RuntimeState
import android.app.Notification
import android.app.Service
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import okhttp3.OkHttpClient
import com.khaleel.glance.core.Reading
import com.khaleel.glance.core.SourceResult
import java.util.concurrent.TimeUnit
import com.khaleel.glance.widget.WidgetUpdates
import com.khaleel.glance.widget.WidgetPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.khaleel.glance.wear.WatchPreferences
import com.khaleel.glance.wear.WatchPublisher

class GlanceApplication : Application(), MonitoringDependencyProvider {
    override fun onCreate() {
        super.onCreate()
        // Register all four categories before the user opens Android notification settings.
        alertNotifier.ensureChannels()
        LauncherAccent.apply(this, controller.settings.accentHex)
        // Observe the existing state, including stop/sign-out/person-switch transitions.
        // Widgets have no network poller and never start monitoring on their own.
        widgetScope.launch {
            controller.display.collect {
                if (BuildConfig.DEBUG) android.util.Log.d("GlanceWidget", "Display update: ${it.runtime.name}, monitoring=${it.monitoringEnabled}")
                widgetRenderer.render()
                watchPublisher.refresh()
            }
        }.invokeOnCompletion {
            if (BuildConfig.DEBUG) android.util.Log.d("GlanceWidget", "Display observer ended: ${it?.javaClass?.simpleName ?: "normal"}")
        }
    }

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val widgetPreferences by lazy { WidgetPreferences(this) }
    val widgetRenderer by lazy { WidgetUpdates(this) }
    val watchPreferences by lazy { WatchPreferences(this) }
    val watchPublisher by lazy { WatchPublisher(this, controller, watchPreferences) }
    fun updateWidgets() {
        widgetScope.launch { widgetRenderer.render() }
    }

    private val diagnosticStore by lazy { DiagnosticsStore(this) }
    private val encryptedStore by lazy { SecureFileStore(this) }
    val alertHistory by lazy { EncryptedAlertHistoryStore(encryptedStore, DeviceClock) }
    val alertPreferences by lazy { AlertPreferences(this) }
    val alertNotifier by lazy { GlucoseAlertNotifier(this) { controller.settings.numericStatusIcon } }
    val alertEngine by lazy {
        AlertEngine(
            alertPreferences, alertNotifier, DeviceClock,
            currentDisplay = { controller.display.value },
            currentPrivacy = { controller.settings.showOnLockScreen },
            history = alertHistory,
        )
    }
    val controller: AppController by lazy {
        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        AppController(
            source = SourceFactory.create(this, http, DeviceClock),
            authStore = AuthStore(encryptedStore),
            patientStore = LocalStateStore(encryptedStore),
            settings = UserSettings(this),
            clock = DeviceClock,
            diagnostics = diagnosticStore,
            nicknameStore = LocalNicknameStore(encryptedStore),
        )
    }

    private val notificationPublication = NotificationPublication()

    private val notifications = object : MonitoringNotifications {
        private fun renderer(presentation: MonitoringPresentation) =
            GlucoseNotificationRenderer(
                this@GlanceApplication,
                presentation.display,
                controller.settings.unit,
                controller.settings.showOnLockScreen,
                controller.settings.enhancedNotification,
                presentation.actionGeneration.takeIf { it != 0L },
                numericStatusIcon = controller.settings.numericStatusIcon,
            )

        override fun startingNotification(service: Service, presentation: MonitoringPresentation): Notification =
            renderer(presentation).apply { createChannel() }.build()

        override fun render(presentation: MonitoringPresentation) {
            alertEngine.refreshIcons(presentation.display, controller.settings.unit, controller.settings.showOnLockScreen)
            watchPublisher.refresh()
            val generation = notificationPublication.generation()
            val unit = controller.settings.unit
            val lockScreenVisible = controller.settings.showOnLockScreen
            val enhanced = controller.settings.enhancedNotification
            val numericIcon = controller.settings.numericStatusIcon
            val regularAfterDismissal = NotificationVisibility.regularAfterDismissal(this@GlanceApplication)
            val output = GlucoseNotificationRenderer(
                this@GlanceApplication, presentation.display, unit, lockScreenVisible, enhanced,
                presentation.actionGeneration.takeIf { it != 0L }, numericStatusIcon = numericIcon,
            )
            output.createChannel()
            val notification = output.build()
            notificationPublication.publish(generation, {
                controller.display.value == presentation.display &&
                    presentation.display.monitoringEnabled &&
                    presentation.display.runtime != RuntimeState.STOPPED &&
                    (presentation.display.runtime != RuntimeState.RUNNING ||
                        MonitoringService.currentActionGeneration == presentation.actionGeneration) &&
                    controller.settings.unit == unit &&
                    controller.settings.showOnLockScreen == lockScreenVisible &&
                    controller.settings.enhancedNotification == enhanced &&
                    controller.settings.numericStatusIcon == numericIcon &&
                    NotificationVisibility.regularAfterDismissal(this@GlanceApplication) == regularAfterDismissal
            }) { output.post(notification) }
        }

        override fun hide() {
            watchPublisher.redact()
            notificationPublication.clear { cancelMonitoringNotification() }
            alertEngine.clearNotifications()
        }
    }

    override fun monitoringDependencies() = MonitoringDependencies(
        controller, notifications, DeviceClock,
        MonitoringAlerts { reading, display ->
            alertEngine.onPollResult(
                SourceResult.Success(reading), display,
                controller.settings.unit, controller.settings.showOnLockScreen,
            )
        },
    )

    fun onPollResult(result: SourceResult<Reading>) {
        alertEngine.onPollResult(
            result, controller.display.value,
            controller.settings.unit, controller.settings.showOnLockScreen,
        )
    }

    fun showCurrentNotification(restore: Boolean = false) {
        if (restore) NotificationVisibility.restore(this)
        notifications.render(MonitoringPresentation(controller.display.value, MonitoringService.currentActionGeneration ?: 0L))
    }

    fun hideCurrentNotification() = notifications.hide()

    private fun cancelMonitoringNotification() {
        GlucoseNotificationRenderer(
            this, controller.display.value, controller.settings.unit, controller.settings.showOnLockScreen,
        ).hide()
    }

    /** The privacy choice cannot change between an in-flight final check and notify. */
    fun setLockScreenValuesVisible(visible: Boolean) {
        notificationPublication.clear {
            cancelMonitoringNotification()
            alertEngine.changePrivacy { controller.settings.showOnLockScreen = visible }
        }
    }

    /** A delayed delete callback cannot republish across a cancellation or a new session. */
    fun onNotificationDismissed(actionGeneration: Long) {
        val generation = notificationPublication.generation()
        val state = controller.display.value
        val keepVisible = NotificationVisibility.keepVisible(this)
        fun action() = NotificationDismissal.action(
            actionGeneration, MonitoringService.currentActionGeneration,
            controller.display.value.monitoringEnabled,
            controller.display.value.runtime == RuntimeState.RUNNING,
            NotificationVisibility.keepVisible(this),
        )
        val dismissedAction = action()
        if (dismissedAction == DismissalAction.IGNORE) return
        val unit = controller.settings.unit
        val lockScreenVisible = controller.settings.showOnLockScreen
        val numericIcon = controller.settings.numericStatusIcon
        val output = GlucoseNotificationRenderer(
            this, state, unit, lockScreenVisible, enhanced = false,
            actionGeneration = actionGeneration, numericStatusIcon = numericIcon,
        )
        val notification = if (dismissedAction == DismissalAction.RESTORE_REGULAR) {
            output.createChannel()
            output.build()
        } else null
        notificationPublication.publish(generation, {
            action() == dismissedAction && controller.display.value == state &&
                NotificationVisibility.keepVisible(this) == keepVisible &&
                controller.settings.unit == unit &&
                controller.settings.showOnLockScreen == lockScreenVisible &&
                controller.settings.numericStatusIcon == numericIcon
        }, invalidatePending = true) {
            NotificationVisibility.dismiss(this)
            if (notification != null) output.post(notification)
        }
    }

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
        hideCurrentNotification()
    }
}
