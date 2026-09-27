package com.khaleel.glance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.khaleel.glance.core.DisplayState
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Complete Glance UI boundary. The host owns data, persistence, permissions, and service effects.
 * Sign-in fields are deliberately held with remember rather than rememberSaveable.
 */
@Composable
fun GlanceRoot(
    displayState: DisplayState,
    accountState: AccountUiState,
    connectionState: ConnectionSetupUiState,
    setupState: SetupUiState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    modifier: Modifier = Modifier,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
) {
    val text = remember(zoneId, locale) { GlanceUiText(zoneId, locale) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(setupState.step) {
        if (setupState.step != SetupStep.COMPLETE) settingsVisible = false
    }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (setupState.step) {
                    SetupStep.SIGN_IN -> SignInScreen(accountState, callbacks.onSignIn)
                    SetupStep.CONNECTION -> ConnectionScreen(connectionState, callbacks)
                    SetupStep.NOTIFICATION_PRIVACY -> NotificationSetupScreen(
                        displayState = displayState,
                        setupState = setupState,
                        settings = settings,
                        callbacks = callbacks,
                        text = text,
                        now = now,
                    )
                    SetupStep.COMPLETE -> if (settingsVisible) {
                        SettingsScreen(
                            setupState = setupState,
                            settings = settings,
                            callbacks = callbacks,
                            onBack = { settingsVisible = false },
                        )
                    } else {
                        MainReadingScreen(
                            displayState = displayState,
                            settings = settings,
                            callbacks = callbacks,
                            text = text,
                            now = now,
                            onOpenSettings = { settingsVisible = true },
                        )
                    }
                }
            }
        }
    }
}
