package com.khaleel.glance.widget

import android.content.Context
import java.io.IOException

internal interface WidgetVisibilityStorage {
    fun read(): Boolean
    fun write(value: Boolean): Boolean
    fun remove(): Boolean
}

/** A failed write never grants visibility; revocation can remove the old grant without a disk write. */
internal class WidgetVisibility(private val storage: WidgetVisibilityStorage) {
    private var blocked = false
    var visible: Boolean
        @Synchronized get() = !blocked && runCatching { storage.read() }.getOrDefault(false)
        @Synchronized set(value) {
            blocked = true
            if (runCatching { storage.write(value) }.getOrDefault(false)) {
                blocked = !value
                return
            }
            val removed = runCatching { storage.remove() }.getOrDefault(false)
            if (!value && removed) return
            throw IOException("Could not persist widget visibility")
        }
}

/** Placing a widget does not silently opt into sharing readings on another screen. */
class WidgetPreferences(context: Context) {
    private val visibility = WidgetVisibility(object : WidgetVisibilityStorage {
        private fun preferences() = context.getSharedPreferences("widget_display", Context.MODE_PRIVATE)
        override fun read() = preferences().getBoolean("values_visible", false)
        override fun write(value: Boolean) = preferences().edit().putBoolean("values_visible", value).commit()
        override fun remove() = context.deleteSharedPreferences("widget_display")
    })
    var valuesVisible: Boolean
        get() = visibility.visible
        set(value) { visibility.visible = value }
}
