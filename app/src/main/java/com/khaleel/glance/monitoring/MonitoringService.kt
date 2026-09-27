package com.khaleel.glance.monitoring

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.khaleel.glance.core.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicLong

class MonitoringService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var dependencies: MonitoringDependencies? = null
    private var coordinator: PollCoordinator? = null
    private var activeGeneration: Long? = null
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        dependencies = resolveDependencies(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deps = dependencies ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val action = intent?.action
        val expectedGeneration = intent?.generationExtra()

        if (action == ACTION_PAUSE) {
            if (activeGeneration == null || expectedGeneration == activeGeneration) {
                coordinator?.stop()
                deps.controller.setMonitoringEnabled(false)
                runCatching { deps.notifications.hide() }
                if (foreground) stopForeground(STOP_FOREGROUND_REMOVE)
                foreground = false
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (!deps.controller.display.value.monitoringEnabled) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val actionGeneration = when {
            action == ACTION_START && expectedGeneration != null -> expectedGeneration
            activeGeneration == null -> expectedGeneration ?: nextActionGeneration()
            expectedGeneration == null || expectedGeneration == activeGeneration -> activeGeneration!!
            else -> {
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        if (!promoteToForeground(deps, actionGeneration)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        activeGeneration = actionGeneration
        val activeCoordinator = coordinator ?: PollCoordinator(deps, serviceScope).also { coordinator = it }
        activeCoordinator.start(actionGeneration)
        if (action == ACTION_REFRESH) activeCoordinator.requestRefresh()
        return START_STICKY
    }

    private fun promoteToForeground(
        deps: MonitoringDependencies,
        actionGeneration: Long,
    ): Boolean {
        if (foreground) {
            deps.controller.setRuntime(RuntimeState.RUNNING)
            render(deps, actionGeneration)
            return true
        }
        val starting = deps.controller.display.value.copy(runtime = RuntimeState.STARTING)
        val notification = try {
            deps.notifications.startingNotification(
                this,
                MonitoringPresentation(starting, actionGeneration),
            )
        } catch (_: Exception) {
            publishUnableToStart(deps, actionGeneration, StartFailure.NOTIFICATION)
            return false
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
            deps.controller.setRuntime(RuntimeState.RUNNING)
            render(deps, actionGeneration)
        } catch (error: Exception) {
            publishUnableToStart(deps, actionGeneration, error.toStartFailure())
            return false
        }
        return true
    }

    private fun publishUnableToStart(
        deps: MonitoringDependencies,
        actionGeneration: Long,
        reason: StartFailure,
    ) {
        deps.controller.setRuntime(RuntimeState.UNABLE_TO_START)
        runCatching {
            deps.notifications.render(
                MonitoringPresentation(
                    display = deps.controller.display.value,
                    actionGeneration = actionGeneration,
                    startFailure = reason,
                ),
            )
        }
    }

    private fun render(deps: MonitoringDependencies, actionGeneration: Long) {
        runCatching {
            deps.notifications.render(
                MonitoringPresentation(deps.controller.display.value, actionGeneration),
            )
        }
    }

    override fun onDestroy() {
        coordinator?.stop()
        val deps = dependencies
        if (deps != null && deps.controller.display.value.monitoringEnabled) {
            deps.controller.setRuntime(RuntimeState.STOPPED)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 4101
        const val ACTION_START = "com.khaleel.glance.monitoring.START"
        const val ACTION_REFRESH = "com.khaleel.glance.monitoring.REFRESH"
        const val ACTION_PAUSE = "com.khaleel.glance.monitoring.PAUSE"
        const val EXTRA_GENERATION = "com.khaleel.glance.monitoring.GENERATION"

        private val generationCounter = AtomicLong(SystemClock.elapsedRealtimeNanos())

        /** Call only from a visible, explicit user Start/Resume action. */
        fun start(context: Context): Boolean {
            val deps = resolveDependencies(context) ?: return false
            deps.controller.setMonitoringEnabled(true)
            deps.controller.setRuntime(RuntimeState.STARTING)
            val actionGeneration = nextActionGeneration()
            val intent = serviceIntent(context, ACTION_START, actionGeneration)
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (error: Exception) {
                deps.controller.setRuntime(RuntimeState.UNABLE_TO_START)
                runCatching {
                    deps.notifications.render(
                        MonitoringPresentation(
                            display = deps.controller.display.value,
                            actionGeneration = actionGeneration,
                            startFailure = error.toStartFailure(),
                        ),
                    )
                }
                false
            }
        }

        fun refreshPendingIntent(context: Context, actionGeneration: Long): PendingIntent =
            PendingIntent.getForegroundService(
                context,
                REQUEST_REFRESH,
                serviceIntent(context, ACTION_REFRESH, actionGeneration),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun pausePendingIntent(context: Context, actionGeneration: Long): PendingIntent =
            PendingIntent.getService(
                context,
                REQUEST_PAUSE,
                serviceIntent(context, ACTION_PAUSE, actionGeneration),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun serviceIntent(context: Context, action: String, actionGeneration: Long) =
            Intent(context, MonitoringService::class.java)
                .setAction(action)
                .putExtra(EXTRA_GENERATION, actionGeneration)

        private fun nextActionGeneration(): Long = generationCounter.incrementAndGet()

        private const val REQUEST_REFRESH = 4102
        private const val REQUEST_PAUSE = 4103
    }
}

private fun resolveDependencies(context: Context): MonitoringDependencies? =
    (context.applicationContext as? MonitoringDependencyProvider)?.monitoringDependencies()

private fun Intent.generationExtra(): Long? =
    if (hasExtra(MonitoringService.EXTRA_GENERATION)) {
        getLongExtra(MonitoringService.EXTRA_GENERATION, Long.MIN_VALUE)
    } else {
        null
    }

private fun Exception.toStartFailure(): StartFailure = when {
    Build.VERSION.SDK_INT >= 31 && this is ForegroundServiceStartNotAllowedException ->
        StartFailure.BACKGROUND_START_NOT_ALLOWED
    this is SecurityException -> StartFailure.SECURITY
    this is IllegalArgumentException -> StartFailure.NOTIFICATION
    else -> StartFailure.UNKNOWN
}
