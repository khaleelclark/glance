package com.khaleel.glance.data

/** A failed store must not prevent removal of health data already shown in notifications. */
internal object LocalHealthDataCleanup {
    fun run(
        clearReadings: () -> Unit,
        clearHistory: () -> Unit,
        clearAlertSnapshot: () -> Unit,
        clearAlertNotifications: () -> Unit,
        updateMonitoring: (readingsCleared: Boolean) -> Unit,
    ): Boolean {
        var succeeded = true
        fun attempt(action: () -> Unit): Boolean = runCatching(action)
            .onFailure { succeeded = false }.isSuccess

        val readingsCleared = attempt(clearReadings)
        attempt(clearHistory)
        attempt(clearAlertSnapshot)
        attempt(clearAlertNotifications)
        // Rerender the cleared state, or hide the old card if reading cleanup itself failed.
        attempt { updateMonitoring(readingsCleared) }
        return succeeded
    }
}
