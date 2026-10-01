package com.khaleel.glance.widget

import android.content.Context

/** Placing a widget does not silently opt into sharing readings on another screen. */
class WidgetPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("widget_display", Context.MODE_PRIVATE)
    var valuesVisible: Boolean
        get() = preferences.getBoolean("values_visible", false)
        set(value) { preferences.edit().putBoolean("values_visible", value).commit() }
}
