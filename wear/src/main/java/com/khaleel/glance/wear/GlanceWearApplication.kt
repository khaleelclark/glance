package com.khaleel.glance.wear

import android.app.Application
import com.khaleel.glance.wear.data.WatchRuntime

class GlanceWearApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WatchRuntime.initialize(this)
    }
}
