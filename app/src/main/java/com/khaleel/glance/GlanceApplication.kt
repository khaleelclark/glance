package com.khaleel.glance

import android.app.Application
import com.khaleel.glance.auth.AuthStore
import com.khaleel.glance.auth.SecureFileStore
import com.khaleel.glance.data.AppController
import com.khaleel.glance.data.DeviceClock
import com.khaleel.glance.data.LocalStateStore
import com.khaleel.glance.data.UserSettings
import com.khaleel.glance.librelinkup.LibreLinkUpSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class GlanceApplication : Application() {
    val controller: AppController by lazy {
        val encrypted = SecureFileStore(this)
        val http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
        AppController(
            source = LibreLinkUpSource(http, DeviceClock),
            authStore = AuthStore(encrypted),
            patientStore = LocalStateStore(encrypted),
            settings = UserSettings(this),
            clock = DeviceClock,
        )
    }
}
