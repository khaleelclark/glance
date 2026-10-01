package com.khaleel.glance.notification

internal enum class DismissalAction { IGNORE, HIDE, RESTORE_REGULAR }

/** Delayed delete intents cannot restore a stopped session or affect a newer session. */
internal object NotificationDismissal {
    fun action(
        dismissedGeneration: Long,
        activeGeneration: Long?,
        monitoringEnabled: Boolean,
        running: Boolean,
        keepVisible: Boolean,
    ): DismissalAction = when {
        activeGeneration == null || dismissedGeneration != activeGeneration ||
            !monitoringEnabled || !running -> DismissalAction.IGNORE
        keepVisible -> DismissalAction.RESTORE_REGULAR
        else -> DismissalAction.HIDE
    }
}
