package com.khaleel.glance.monitoring

import android.app.Notification
import android.app.Service
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.data.AppController

/**
 * Process-scoped integration surface for the service.
 *
 * [AppController] remains the sole owner of source sessions, selected-person
 * generation, single-flight polling, persisted monitoring intent, cooldowns,
 * and display truth. The notification adapter only presents controller state.
 */
data class MonitoringDependencies(
    val controller: AppController,
    val notifications: MonitoringNotifications,
    val clock: Clock,
)

fun interface MonitoringDependencyProvider {
    fun monitoringDependencies(): MonitoringDependencies
}

data class MonitoringPresentation(
    val display: DisplayState,
    val actionGeneration: Long,
    val startFailure: StartFailure? = null,
)

/** The starting notification must use local state only and must never poll. */
interface MonitoringNotifications {
    fun startingNotification(service: Service, presentation: MonitoringPresentation): Notification
    fun render(presentation: MonitoringPresentation)
    fun hide()
}

enum class StartFailure {
    BACKGROUND_START_NOT_ALLOWED,
    SECURITY,
    NOTIFICATION,
    DEPENDENCIES_UNAVAILABLE,
    UNKNOWN,
}

