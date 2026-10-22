package com.khaleel.glance.data

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class UserSettingsTest {
    @Test fun retryingFailedStopRequiresAnotherDurableCommit() {
        val memory = mutableMapOf<String, Any?>(
            "monitoring_enabled" to true, "accent_default_025_migrated" to true,
        )
        var diskMonitoring = true
        var canCommit = false
        var attempts = 0
        val preferences = proxy<SharedPreferences> { name, args ->
            when (name) {
                "getBoolean" -> memory[args[0]] ?: args[1]
                "edit" -> {
                    val pending = mutableMapOf<String, Any?>()
                    lateinit var editor: SharedPreferences.Editor
                    editor = proxy { method, values ->
                        when (method) {
                            "putBoolean" -> { pending[values[0] as String] = values[1]; editor }
                            "apply" -> { memory.putAll(pending); null }
                            "commit" -> {
                                attempts++
                                memory.putAll(pending)
                                if (canCommit) diskMonitoring = memory["monitoring_enabled"] as Boolean
                                canCommit
                            }
                            else -> error("Unexpected editor call: $method")
                        }
                    }
                    editor
                }
                else -> error("Unexpected preferences call: $name")
            }
        }
        val context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int) = preferences
        }
        val settings = UserSettings(context)
        assertFalse(settings.setMonitoringEnabled(false))
        assertFalse(settings.monitoringEnabled)
        assertTrue(diskMonitoring)
        assertFalse(settings.setMonitoringEnabled(false))
        assertEquals(2, attempts)
        canCommit = true
        assertTrue(settings.setMonitoringEnabled(false))
        assertFalse(diskMonitoring)
        assertEquals(3, attempts)
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args ?: emptyArray())
        } as T
}
