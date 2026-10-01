package com.khaleel.glance.wear

import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.khaleel.glance.wear.debug.DemoFixtureActivity

@Composable
internal fun DebugFixtureEntry() {
    val context = LocalContext.current
    Button(onClick = { context.startActivity(Intent(context, DemoFixtureActivity::class.java)) }) {
        Text("DEMO synthetic fixture")
    }
}
