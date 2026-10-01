package com.khaleel.glance.widget

/** Orders host publication and synchronous privacy clearing without locking composition. */
internal class WidgetPublication {
    private val lock = Any()
    private var generation = 0L

    fun generation(): Long = synchronized(lock) { generation }

    fun redact(clearHost: () -> Unit) = synchronized(lock) {
        generation++
        clearHost()
    }

    fun publish(expectedGeneration: Long, stillCurrent: () -> Boolean, updateHost: () -> Unit): Boolean =
        synchronized(lock) {
            if (generation != expectedGeneration || !stillCurrent()) false
            else {
                updateHost()
                true
            }
        }
}
