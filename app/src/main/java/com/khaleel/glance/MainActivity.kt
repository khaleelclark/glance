package com.khaleel.glance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GlanceApp() }
    }
}

@Composable
private fun GlanceApp() {
    MaterialTheme {
        Scaffold { padding ->
            Column(Modifier.padding(padding)) {
                Text("Glance")
                Text("Sign in to your LibreLinkUp follower account to begin.")
            }
        }
    }
}
