package com.khaleel.glance.notification

import java.io.IOException

internal interface NotificationRetentionStorage {
    fun read(): Boolean
    fun write(value: Boolean): Boolean
}

/** A failed SharedPreferences commit can change memory; keep the previously accepted choice. */
internal class NotificationRetention(private val storage: NotificationRetentionStorage) {
    private var accepted = storage.read()

    var keepVisible: Boolean
        @Synchronized get() = accepted
        @Synchronized set(value) {
            if (!storage.write(value)) throw IOException("Could not save notification retention")
            accepted = value
        }
}
