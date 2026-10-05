package com.khaleel.glance.debug

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Bundle
import android.os.Process
import com.khaleel.glance.GlanceApplication
import com.khaleel.glance.LauncherAccent
import com.khaleel.glance.core.AccentPresets
import java.io.File
import org.json.JSONObject

/** Actual framework icon and component checks on a dedicated synthetic emulator only. */
class LauncherAccentInstrumentation : Instrumentation() {
    private var keepAccent: String? = null
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        keepAccent = arguments?.getString("keepAccent")
        start()
    }

    override fun onStart() {
        val context = targetContext
        check(android.os.Build.MODEL.contains("sdk"))
        val app = context.applicationContext as GlanceApplication
        val pm = context.packageManager
        val old = app.controller.settings.accentHex
        val pid = Process.myPid()
        val checks = JSONObject()
        fun component(name: String) = ComponentName(context.packageName, "${context.packageName}.Launcher$name")
        fun launchers() = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0,
        )
        fun expect(name: String) {
            val matches = launchers()
            check(matches.size == 1 && matches.single().activityInfo.name == component(name).className)
            check(matches.single().activityInfo.targetActivity == "${context.packageName}.MainActivity")
        }
        try {
            for (preset in AccentPresets.all) {
                check(LauncherAccent.apply(context, preset.hex) != LauncherAccent.ApplyResult.FAILED)
                expect(preset.name)
                check(LauncherAccent.apply(context, preset.hex) == LauncherAccent.ApplyResult.UNCHANGED)
                val icon = launchers().single().loadIcon(pm) as AdaptiveIconDrawable
                val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
                icon.foreground.setBounds(0, 0, 108, 108)
                icon.foreground.draw(Canvas(bitmap))
                val wanted = Color.parseColor(preset.hex)
                var count = 0
                for (x in 0..107) for (y in 0..107) if (bitmap.getPixel(x, y) == wanted) count++
                check(count > 500) { "${preset.name} foreground color" }
                File(context.filesDir, "launcher-${preset.name.lowercase()}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                checks.put("${preset.name}SoleLauncherExactPixelsAndRepeatedNoOp", true)
            }
            checks.put("presetSwitchesKeepSameProcess", Process.myPid() == pid)
            check(LauncherAccent.apply(context, "#UNKNOWN") == LauncherAccent.ApplyResult.APPLIED)
            expect("Lilac")
            checks.put("legacyUnsupportedColorFallsBackToLilac", true)
            pm.setComponentEnabledSetting(component("Teal"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            check(launchers().size == 2)
            check(LauncherAccent.apply(context, AccentPresets.DEFAULT) == LauncherAccent.ApplyResult.APPLIED)
            expect("Lilac")
            checks.put("refreshRepairsMultipleEnabledAliases", true)
            // Exercise the API30-32 enable-first helper against the actual framework. This does
            // not claim API30 emulator coverage; the installed runtime is recorded in the receipt.
            val legacy = LauncherAccent::class.java.getDeclaredMethod(
                "applyEnableFirst", PackageManager::class.java, String::class.java,
                com.khaleel.glance.core.AccentPreset::class.java,
            ).apply { isAccessible = true }
            legacy.invoke(LauncherAccent, pm, context.packageName, AccentPresets.all.first { it.name == "Teal" })
            expect("Teal")
            checks.put("enableFirstHelperWorksOnInstalledFramework", true)
            val broken = object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = error("Synthetic recoverable failure")
            }
            check(LauncherAccent.apply(broken, AccentPresets.DEFAULT) == LauncherAccent.ApplyResult.FAILED)
            expect("Teal")
            checks.put("runtimeFailureReturnsFailedWithoutCrashingCaller", true)
            val main = pm.getActivityInfo(ComponentName(context.packageName, "${context.packageName}.MainActivity"), 0)
            check(main.enabled && main.exported)
            checks.put("explicitMainActivityRemainsAvailable", true)
            val selected = keepAccent?.let { name -> AccentPresets.all.single { it.name == name }.hex } ?: old
            app.controller.settings.accentHex = selected
            // Instrumentation terminates its process immediately; flush the synthetic selection
            // before the shell performs a force-stop/cold-start persistence check.
            check(context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().commit())
            check(LauncherAccent.apply(context, selected) != LauncherAccent.ApplyResult.FAILED)
            val report = JSONObject().put("allPassed", true).put("checks", checks)
                .put("synthetic", true).put("api", android.os.Build.VERSION.SDK_INT)
            File(context.filesDir, "launcher-accent-probe.json").writeText(report.toString())
            finish(Activity.RESULT_OK, Bundle().apply { putString("checks", checks.toString()) })
        } catch (failure: Throwable) {
            app.controller.settings.accentHex = old
            LauncherAccent.apply(context, old)
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("failure", failure.toString()) })
        }
    }
}
