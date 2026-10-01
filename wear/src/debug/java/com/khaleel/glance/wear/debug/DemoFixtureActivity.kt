package com.khaleel.glance.wear.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khaleel.glance.wear.data.ApplyResult
import com.khaleel.glance.wear.data.SnapshotEnvelope
import com.khaleel.glance.wear.data.WatchRuntime
import com.khaleel.glance.wear.shared.WatchSnapshot
import com.khaleel.glance.wear.shared.WatchStatus

/** Debug manifest only. It never accepts credentials, identifiers, or real glucose values. */
class DemoFixtureActivity : ComponentActivity() {
    private var probe by mutableStateOf("status=idle accepted=false notification=false")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchRuntime.initialize(this)
        intent.getStringExtra(EXTRA_STATUS)?.let { raw ->
            runCatching { WatchStatus.valueOf(raw) }.getOrNull()?.let({ applySynthetic(it, intent.getIntExtra("demo_age_minutes", 1)) })
        }
        if (intent.hasExtra("demo_opt_in")) {
            WatchRuntime.setWatchNotificationOptIn(this, intent.getBooleanExtra("demo_opt_in", false))
        }
        val current = WatchRuntime.state.value
        val presentation = com.khaleel.glance.wear.shared.WatchPresentation.from(current.snapshot, System.currentTimeMillis())
        val timeline = com.khaleel.glance.wear.complication.WearComplicationFormatter.format(current.snapshot, System.currentTimeMillis())
        val notifications = getSystemService(android.app.NotificationManager::class.java).activeNotifications
        val report = org.json.JSONObject().put("status", presentation.heading)
            .put("hasValue", presentation.value.isNotEmpty()).put("fresh", presentation.fresh)
            .put("hasArrow", presentation.arrow.isNotEmpty()).put("revoked", current.revoked)
            .put("notificationCount", notifications.count { it.id == 2101 })
            .put("timelineFrames", timeline.frames.size).put("safeDefault", timeline.defaultText == "Open phone" || !presentation.fresh)
        java.io.File(filesDir, "watch-probe.json").writeText(report.toString())
        setContent {
            MaterialTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("DEMO · SYNTHETIC", textAlign = TextAlign.Center)
                    Text(probe, textAlign = TextAlign.Center)
                    Button(onClick = { applySynthetic(WatchStatus.READING) }) { Text("Fresh") }
                    Button(onClick = { applySynthetic(WatchStatus.READING, ageMinutes = 7) }) { Text("Delayed") }
                    Button(onClick = { applySynthetic(WatchStatus.ACTION_REQUIRED) }) { Text("Check phone") }
                    Button(onClick = { applySynthetic(WatchStatus.HIDDEN) }) { Text("Tombstone") }
                }
            }
        }
    }

    private fun applySynthetic(status: WatchStatus, ageMinutes: Int = 1) {
        val now = System.currentTimeMillis()
        val reading = status == WatchStatus.READING
        val requestedNotification = intent.getBooleanExtra(EXTRA_PHONE_NOTIFICATIONS, true)
        val snapshot = WatchSnapshot(
            revision = intent.getLongExtra("demo_revision", WatchRuntime.state.value.lastRevision + 1),
            sentAtMillis = now - intent.getIntExtra("demo_transport_minutes", 0) * 60_000L,
            status = status,
            measuredAtMillis = if (reading) now - ageMinutes * 60_000L else 0,
            value = if (reading) "123" else "",
            unit = if (reading) "mg/dL" else "",
            arrow = if (reading) "→" else "",
            trend = if (reading) "Steady" else "",
            notificationsEnabled = requestedNotification,
        )
        val result = WatchRuntime.applySnapshot(
            this,
            SnapshotEnvelope(DEMO_SOURCE, snapshot),
            allowSourceSwitch = true,
        )
        probe = "status=${status.name} accepted=${result == ApplyResult.ACCEPTED} " +
            "notification=$requestedNotification"
    }

    companion object {
        const val EXTRA_STATUS = "demo_status"
        const val EXTRA_PHONE_NOTIFICATIONS = "demo_phone_notifications"
        private const val DEMO_SOURCE = "debug-demo-phone"
    }
}
