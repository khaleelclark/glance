package com.khaleel.glance.notification

/** Orders notification publication with Stop, sign-out, and person-change cancellation. */
internal class NotificationPublication {
    private val lock = Any()
    private var generation = 0L

    fun generation(): Long = synchronized(lock) { generation }

    fun clear(cancel: () -> Unit) = synchronized(lock) {
        generation++
        cancel()
    }

    fun publish(
        expectedGeneration: Long,
        stillCurrent: () -> Boolean,
        invalidatePending: Boolean = false,
        notify: () -> Unit,
    ): Boolean =
        synchronized(lock) {
            if (generation != expectedGeneration || !stillCurrent()) false
            else {
                if (invalidatePending) generation++
                notify()
                true
            }
        }
}
