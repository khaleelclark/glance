package com.khaleel.glance.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khaleel.glance.wear.data.WatchCacheState
import com.khaleel.glance.wear.data.WatchRuntime
import com.khaleel.glance.wear.data.WearDataSync
import com.khaleel.glance.wear.shared.WatchPresentation
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchRuntime.initialize(this)
        WearDataSync.query(this)
        setContent { GlanceWearApp() }
    }

    override fun onResume() {
        super.onResume()
        WearDataSync.query(this)
    }
}

@Composable
fun GlanceWearApp() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val cache by WatchRuntime.state.collectAsState()
            GlanceWearScreen(cache)
        }
    }
}

@Composable
fun GlanceWearScreen(cache: WatchCacheState) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var visible by remember {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) visible = true
            if (event == Lifecycle.Event.ON_STOP) visible = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(visible) {
        while (visible) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val presentation = WatchPresentation.from(cache.snapshot, now)
    val permissionGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        WatchRuntime.setWatchNotificationOptIn(context, granted)
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(24.dp))
        if (BuildConfig.DEBUG) Text("DEMO · SYNTHETIC", textAlign = TextAlign.Center)
        Text(
            text = presentation.heading,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (presentation.value.isNotEmpty()) {
            Text(
                text = if (presentation.fresh) presentation.value else "Last ${presentation.value}",
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    contentDescription = if (presentation.fresh) {
                        "${presentation.value} ${presentation.unit}, ${presentation.trend.ifBlank { "trend unavailable" }}"
                    } else {
                        "Last reading ${presentation.value} ${presentation.unit}"
                    }
                },
            )
            Text(
                text = listOf(presentation.unit, presentation.arrow).filter { it.isNotBlank() }.joinToString(" "),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (presentation.fresh && presentation.trend.isNotBlank()) {
                Text(presentation.trend, textAlign = TextAlign.Center)
            }
            Text(
                "Measured ${formatTime(presentation.measuredAtMillis)}",
                textAlign = TextAlign.Center,
            )
        } else {
            Text(statusDetail(presentation.heading), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Watch notification",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
        Switch(
            checked = cache.watchNotificationsOptIn && permissionGranted,
            onCheckedChange = { enabled ->
                if (!enabled) {
                    WatchRuntime.setWatchNotificationOptIn(context, false)
                } else if (permissionGranted) {
                    WatchRuntime.setWatchNotificationOptIn(context, true)
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            modifier = Modifier.semantics { contentDescription = "Native watch glucose notification" },
        )
        if (!permissionGranted) {
            Text("Notification permission is off", textAlign = TextAlign.Center)
        } else if (cache.snapshot?.notificationsEnabled != true) {
            Text("Enable watch notifications on the phone", textAlign = TextAlign.Center)
        }
        Button(
            onClick = { WearDataSync.query(context) },
            modifier = Modifier.fillMaxWidth(0.72f),
        ) {
            Text("Sync phone")
        }
        DebugFixtureEntry()
        Spacer(Modifier.height(24.dp))
    }
}

private fun statusDetail(heading: String): String = when (heading) {
    "Open phone" -> "No current phone update"
    "Hidden" -> "Reading hidden on phone"
    "Stopped" -> "Monitoring stopped on phone"
    "Check phone" -> "Action is required on phone"
    "Time unknown" -> "Reading time unavailable"
    else -> "Waiting for a current reading"
}

private fun formatTime(millis: Long): String = DateTimeFormatter.ofPattern("HH:mm").format(
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
)
