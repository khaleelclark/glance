package com.khaleel.glance.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.khaleel.glance.GlanceApplication
import java.io.IOException

/** Applies the owner's retention choice; never restarts a stopped monitoring session. */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val generation = intent.getLongExtra(EXTRA_GENERATION, Long.MIN_VALUE)
        val presentationEpoch = intent.getLongExtra(EXTRA_PRESENTATION_EPOCH, Long.MIN_VALUE)
        val app = context.applicationContext as? GlanceApplication ?: return
        app.onNotificationDismissed(generation, presentationEpoch)
    }

    companion object {
        const val ACTION_DISMISSED = "com.khaleel.glance.notification.DISMISSED"
        const val EXTRA_GENERATION = "com.khaleel.glance.notification.GENERATION"
        const val EXTRA_PRESENTATION_EPOCH = "com.khaleel.glance.notification.PRESENTATION_EPOCH"
    }
}

internal data class NotificationPresentationState(val dismissed: Boolean, val epoch: Long)

internal interface NotificationVisibilityStorage {
    fun read(): NotificationPresentationState
    fun write(state: NotificationPresentationState): Boolean
}

/** A failed SharedPreferences commit can mutate its cache; retain only successfully saved state. */
internal class NotificationPresentation(private val storage: NotificationVisibilityStorage) {
    private var accepted = storage.read()

    @Synchronized
    fun current(): NotificationPresentationState = accepted

    @Synchronized
    fun dismiss(expectedEpoch: Long): Boolean {
        if (expectedEpoch != accepted.epoch) return false
        if (accepted.dismissed) return true
        val next = accepted.copy(dismissed = true)
        if (!storage.write(next)) throw IOException("Could not save notification dismissal")
        accepted = next
        return true
    }

    @Synchronized
    fun restore(): Long {
        if (accepted.epoch == Long.MAX_VALUE) throw IOException("Notification presentation epoch exhausted")
        val next = NotificationPresentationState(dismissed = false, epoch = accepted.epoch + 1)
        if (!storage.write(next)) throw IOException("Could not save notification restore")
        accepted = next
        return next.epoch
    }
}

/** Explicit retention overrides suppression, but never re-promotes a dismissed Live Update. */
object NotificationVisibility {
    private const val PREFS = "notification_visibility"
    private const val DISMISSED = "dismissed"
    private const val PRESENTATION_EPOCH = "presentation_epoch"
    private const val KEEP_VISIBLE = "keep_visible"
    private var retention: NotificationRetention? = null
    private var presentation: NotificationPresentation? = null

    @Synchronized
    private fun retention(context: Context): NotificationRetention = retention ?: NotificationRetention(
        object : NotificationRetentionStorage {
            private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            override fun read() = preferences.getBoolean(KEEP_VISIBLE, true)
            override fun write(value: Boolean) = preferences.edit().putBoolean(KEEP_VISIBLE, value).commit()
        },
    ).also { retention = it }

    @Synchronized
    private fun presentation(context: Context): NotificationPresentation = presentation ?: NotificationPresentation(
        object : NotificationVisibilityStorage {
            private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            override fun read() = NotificationPresentationState(
                dismissed = preferences.getBoolean(DISMISSED, false),
                epoch = preferences.getLong(PRESENTATION_EPOCH, 0L),
            )
            override fun write(state: NotificationPresentationState) = preferences.edit()
                .putBoolean(DISMISSED, state.dismissed)
                .putLong(PRESENTATION_EPOCH, state.epoch)
                .commit()
        },
    ).also { presentation = it }

    fun keepVisible(context: Context): Boolean = retention(context).keepVisible

    fun setKeepVisible(context: Context, value: Boolean) {
        retention(context).keepVisible = value
    }

    fun epoch(context: Context): Long = presentation(context).current().epoch

    fun current(context: Context, expectedEpoch: Long): Boolean = epoch(context) == expectedEpoch

    fun regularAfterDismissal(context: Context): Boolean =
        keepVisible(context) && presentation(context).current().dismissed

    fun isDismissed(context: Context): Boolean =
        !keepVisible(context) && presentation(context).current().dismissed

    fun dismiss(context: Context, expectedEpoch: Long): Boolean =
        presentation(context).dismiss(expectedEpoch)

    /** Rotates the identity so delayed callbacks cannot undo this explicit restore. */
    fun restore(context: Context): Long = presentation(context).restore()
}
