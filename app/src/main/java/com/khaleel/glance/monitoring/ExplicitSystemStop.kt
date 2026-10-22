package com.khaleel.glance.monitoring

/** Never consume system Stop recovery before its disabled intent is durable. */
internal object ExplicitSystemStop {
    fun handle(disable: () -> Boolean, hide: () -> Unit, markHandled: () -> Unit) {
        val saved = try { disable() } finally { hide() }
        if (saved) markHandled()
    }
}
