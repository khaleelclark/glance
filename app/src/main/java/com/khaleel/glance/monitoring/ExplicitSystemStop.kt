package com.khaleel.glance.monitoring

/** Never consume system Stop recovery before durable disable or a later explicit Start. */
internal class ExplicitSystemStop {
    private var supersededThrough = 0L

    fun handle(timestamp: Long, lastHandled: Long, disable: () -> Boolean, hide: () -> Unit, markHandled: () -> Unit) {
        if (timestamp <= maxOf(lastHandled, supersededThrough)) return
        val saved = try { disable() } finally { hide() }
        if (saved) markHandled()
    }

    fun afterStart(timestamp: Long, explicit: Boolean, saved: Boolean, markHandled: () -> Unit) {
        if (!explicit || !saved) return
        supersededThrough = maxOf(supersededThrough, timestamp)
        markHandled()
    }
}
