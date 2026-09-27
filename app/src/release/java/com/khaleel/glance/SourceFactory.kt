package com.khaleel.glance

import android.content.Context
import com.khaleel.glance.core.Clock
import com.khaleel.glance.core.GlucoseSource
import com.khaleel.glance.librelinkup.LibreLinkUpSource
import okhttp3.OkHttpClient

/** Release has no synthetic source or fixture selector. */
internal object SourceFactory {
    fun create(context: Context, http: OkHttpClient, clock: Clock): GlucoseSource =
        LibreLinkUpSource(http, clock)
}
