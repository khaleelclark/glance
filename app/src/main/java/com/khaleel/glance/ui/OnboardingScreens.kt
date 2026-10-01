package com.khaleel.glance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.DisplayState
import com.khaleel.glance.ui.GlanceButton as Button
import com.khaleel.glance.ui.GlanceOutlinedButton as OutlinedButton
import java.time.Instant

@Composable
internal fun SignInScreen(accountState: AccountUiState, onSignIn: (Credentials) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val busy = accountState.status == AccountStatus.SIGNING_IN
    val canSubmit = !busy && email.isNotBlank() && password.isNotEmpty()

    fun submit() {
        if (!canSubmit) return
        val submitted = Credentials(email.trim(), password)
        email = ""
        password = ""
        passwordVisible = false
        onSignIn(submitted)
    }

    ScreenColumn {
        OnboardingHeader(
            eyebrow = "WELCOME TO GLANCE",
            title = "A shared reading, always close.",
            summary = "Sign in with the LibreLinkUp follower account you already use.",
        )
        GlanceCard(Modifier.fillMaxWidth()) {
            SectionTitle("Sign in", "Your credentials stay encrypted on this phone.")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                label = { Text("Email") },
                singleLine = true,
                shape = GlanceShapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                label = { Text("Password") },
                singleLine = true,
                shape = GlanceShapes.medium,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    TextButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Hide" else "Show")
                    }
                },
            )
            accountProblemText(accountState.problem)?.let { StatusMessage(it, isError = true) }
            Button(onClick = ::submit, modifier = Modifier.fillMaxWidth(), enabled = canSubmit) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign in")
                }
            }
        }
        Text(
            "Glance uses this account only to retrieve readings shared with you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ConnectionScreen(state: ConnectionSetupUiState, callbacks: GlanceCallbacks) {
    val selected = state.connections.firstOrNull { it.id == state.selectedConnectionId }
    ScreenColumn {
        OnboardingHeader(
            eyebrow = "STEP 2",
            title = "Choose a shared person",
            summary = "Glance will follow only the person you explicitly confirm.",
        )
        if (state.loading) {
            StatusMessage("Loading shared people…")
        }
        state.connections.forEach { connection ->
            val isSelected = connection.id == state.selectedConnectionId
            GlanceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { callbacks.onSelectConnection(connection) },
                    ),
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(connection.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (isSelected) "Selected — confirm below" else "Tap to select",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        connectionProblemText(state.problem)?.let { StatusMessage(it, isError = true) }
        if (state.problem != null || (!state.loading && state.connections.isEmpty())) {
            OutlinedButton(onClick = callbacks.onRetryConnections, modifier = Modifier.fillMaxWidth()) {
                Text("Try again")
            }
        }
        Button(
            onClick = callbacks.onConfirmConnection,
            modifier = Modifier.fillMaxWidth(),
            enabled = selected != null && !state.loading,
        ) {
            Text(selected?.let { "Confirm ${it.displayName}" } ?: "Select a person")
        }
    }
}

@Composable
internal fun NotificationSetupScreen(
    displayState: DisplayState,
    setupState: SetupUiState,
    settings: UserSettingsUi,
    callbacks: GlanceCallbacks,
    text: GlanceUiText,
    now: Instant,
) {
    val preview = text.notificationPreview(displayState, settings, now)
    ScreenColumn {
        OnboardingHeader(
            eyebrow = "FINAL STEP",
            title = "Keep the reading close",
            summary = "Glance updates one quiet notification and lets you decide what appears while locked.",
        )
        GlanceCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text("NOTIFICATION PREVIEW", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(preview.first, style = MaterialTheme.typography.titleLarge)
            Text(preview.second, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PermissionStatusCard(setupState.notificationPermission, callbacks)
        GlanceCard(Modifier.fillMaxWidth()) {
            SectionTitle("Lock-screen privacy")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .toggleable(
                        value = settings.showOnLockScreen,
                        role = Role.Switch,
                        onValueChange = callbacks.onLockScreenVisibilityChanged,
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Show readings while locked", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.showOnLockScreen) {
                            "Anyone holding the phone may see the name, value, and reading time."
                        } else {
                            "The lock screen says only that a glucose reading is available."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.showOnLockScreen, onCheckedChange = null)
            }
        }
        GlanceCard(Modifier.fillMaxWidth()) {
            SectionTitle("Background access", "Screen-off updates depend on Android's battery controls.")
            Text(batteryStatusText(setupState.batteryStatus), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = callbacks.onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Open battery settings")
            }
        }
        if (setupState.startFailed) {
            StatusMessage(
                "Monitoring did not start. Check notification and battery settings, then try again.",
                isError = true,
            )
        }
        Button(
            onClick = callbacks.onStartMonitoring,
            modifier = Modifier.fillMaxWidth(),
            enabled = setupState.notificationPermission == PermissionStatus.GRANTED &&
                !setupState.startingMonitoring,
        ) {
            Text(if (setupState.startingMonitoring) "Starting…" else "Start monitoring")
        }
        if (setupState.notificationPermission != PermissionStatus.GRANTED) {
            Text(
                "Allow notifications before monitoring can be confirmed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionStatusCard(status: PermissionStatus, callbacks: GlanceCallbacks) {
    GlanceCard(Modifier.fillMaxWidth()) {
        SectionTitle("Notification permission")
        Text(
            when (status) {
                PermissionStatus.UNKNOWN -> "Not requested yet"
                PermissionStatus.GRANTED -> "Allowed — Glance can show the ongoing reading."
                PermissionStatus.DENIED -> "Not allowed — Glance cannot show its ongoing reading."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (status == PermissionStatus.DENIED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        when (status) {
            PermissionStatus.UNKNOWN -> Button(
                onClick = callbacks.onRequestNotificationPermission,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Allow notifications")
            }
            PermissionStatus.DENIED -> OutlinedButton(
                onClick = callbacks.onOpenNotificationSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open notification settings")
            }
            PermissionStatus.GRANTED -> Unit
        }
    }
}

@Composable
private fun OnboardingHeader(eyebrow: String, title: String, summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(eyebrow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun accountProblemText(problem: AccountProblem?) = when (problem) {
    null -> null
    AccountProblem.INVALID_CREDENTIALS -> "Email or password was not accepted. Check them and try again."
    AccountProblem.NETWORK -> "No internet connection. Check the network and try again."
    AccountProblem.TERMS_REQUIRED -> "Open LibreLinkUp to review its terms, then try again."
    AccountProblem.STORAGE -> "Glance could not save sign-in on this device. Check local storage and try again."
    AccountProblem.PROTOCOL -> "LibreLinkUp returned an unexpected response. Try again later."
}

private fun connectionProblemText(problem: ConnectionProblem?) = when (problem) {
    null -> null
    ConnectionProblem.NONE_FOUND -> "No shared people found. Accept Patrick's invitation in LibreLinkUp, then try again."
    ConnectionProblem.NETWORK -> "No internet connection. Shared people could not be loaded."
    ConnectionProblem.REMOVED -> "The selected shared person is no longer available."
    ConnectionProblem.STORAGE -> "Glance could not save the selected person on this device. Try again."
    ConnectionProblem.PROTOCOL -> "LibreLinkUp returned an unexpected response."
}

internal fun batteryStatusText(status: BatteryStatus) = when (status) {
    BatteryStatus.UNKNOWN -> "Android has not reported the battery setting yet."
    BatteryStatus.STANDARD -> "Standard battery limits are active. Screen-off updates may be delayed."
    BatteryStatus.UNRESTRICTED -> "Unrestricted background use is enabled."
}
