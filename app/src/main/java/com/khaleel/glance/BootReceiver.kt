package com.khaleel.glance

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.khaleel.glance.monitoring.MonitoringService

/** BOOT_COMPLETED arrives after credential-encrypted storage becomes available. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? GlanceApplication ?: return
        app.honorExplicitSystemStop()
        val controller = app.controller
        if (!controller.settings.resumeAfterBoot ||
            !controller.display.value.monitoringEnabled ||
            !controller.hasAccount ||
            controller.display.value.person == null
        ) return
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            controller.setMonitoringEnabled(false)
            return
        }
        MonitoringService.start(context)
    }
}
