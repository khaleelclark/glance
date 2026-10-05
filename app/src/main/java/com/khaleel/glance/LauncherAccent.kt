package com.khaleel.glance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import com.khaleel.glance.core.AccentPreset
import com.khaleel.glance.core.AccentPresets

/** Keeps the native launcher droplet aligned with the selected in-app accent. */
object LauncherAccent {
    enum class ApplyResult {
        UNCHANGED,
        APPLIED,
        FAILED,
    }

    /**
     * Applies the closest curated preset. Unknown or legacy custom colors select Lilac, matching
     * [AccentPresets.selected]. Reapplying the active preset does not touch package-manager state.
     * Package-manager and OEM failures are recoverable: the caller may ignore [ApplyResult.FAILED]
     * during startup, record it in redacted diagnostics, and retry on the next settings refresh.
     */
    fun apply(context: Context, hex: String): ApplyResult = try {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val selected = AccentPresets.selected(hex)

        if (isApplied(packageManager, appContext.packageName, selected)) {
            ApplyResult.UNCHANGED
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applyAtomically(packageManager, appContext.packageName, selected)
            } else {
                applyEnableFirst(packageManager, appContext.packageName, selected)
            }
            ApplyResult.APPLIED
        }
    } catch (_: RuntimeException) {
        ApplyResult.FAILED
    }

    private fun isApplied(
        packageManager: PackageManager,
        packageName: String,
        selected: AccentPreset,
    ): Boolean = AccentPresets.all.all { preset ->
        val state = packageManager.getComponentEnabledSetting(component(packageName, preset))
        val enabled = when (state) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> preset.hex == AccentPresets.DEFAULT
            else -> false
        }
        enabled == (preset == selected)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyAtomically(
        packageManager: PackageManager,
        packageName: String,
        selected: AccentPreset,
    ) {
        packageManager.setComponentEnabledSettings(
            AccentPresets.all.map { preset ->
                PackageManager.ComponentEnabledSetting(
                    component(packageName, preset),
                    if (preset == selected) {
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    } else {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    },
                    PackageManager.DONT_KILL_APP,
                )
            },
        )
    }

    private fun applyEnableFirst(
        packageManager: PackageManager,
        packageName: String,
        selected: AccentPreset,
    ) {
        packageManager.setComponentEnabledSetting(
            component(packageName, selected),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AccentPresets.all.filterNot { it == selected }.forEach { preset ->
            packageManager.setComponentEnabledSetting(
                component(packageName, preset),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private fun component(packageName: String, preset: AccentPreset) =
        ComponentName(packageName, "$packageName.Launcher${preset.name}")
}
