package com.khaleel.glance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.khaleel.glance.ui.GlanceButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.khaleel.glance.ui.GlanceOutlinedButton as OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.khaleel.glance.core.Credentials
import com.khaleel.glance.core.DisplayState
import java.time.Instant

@Composable
internal fun SignInScreen(accountState: AccountUiState, onSignIn: (Credentials) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val busy = accountState.status == AccountStatus.SIGNING_IN

    ScreenColumn {
        Text("Glance", style = MaterialTheme.typography.headlineLarge)
        Text(
            "A shared reading, at a glance.",
            style = MaterialTheme.typography.titleLarge,
        )
        Text("Use your LibreLinkUp follower account.")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
        )
        accountProblemText(accountState.problem)?.let {
            StatusMessage(it, isError = true)
        }
        Button(
            onClick = {
                val submitted = Credentials(email.trim(), password)
                email = ""
                password = ""
                passwordVisible = false
                onSignIn(submitted)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Sign in")
            }
        }
        Text(
            "Sign-in is stored encrypted on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ConnectionScreen(state: ConnectionSetupUiState, callbacks: GlanceCallbacks) {
    val selected = state.connections.firstOrNull { it.id == state.selectedConnectionId }
    ScreenColumn {
        Text("Choose a shared person", style = MaterialTheme.typography.headlineMedium)
        Text("Choose whose readings to follow.")
        if (state.loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                Text("Loading shared people…")
            }
        }
        state.connections.forEach { connection ->
            val isSelected = connection.id == state.selectedConnectionId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { callbacks.onSelectConnection(connection) },
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Column {
                        Text(connection.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(if (isSelected) "Selected for confirmation" else "Tap to select")
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
        Text("Set up notifications", style = MaterialTheme.typography.headlineMedium)
        Text("Keep readings in your notifications. Updates are silent.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Preview", style = MaterialTheme.typography.labelLarge)
                Text(preview.first, style = MaterialTheme.typography.titleMedium)
                Text(preview.second, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PermissionStatusCard(setupState.notificationPermission, callbacks)
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { callbacks.onLockScreenVisibilityChanged(!settings.showOnLockScreen) },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show readings on lock screen", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (settings.showOnLockScreen) {
                        "Anyone holding the phone may see the person's name, value, and reading time."
                    } else {
                        "The lock screen will say only that a new glucose reading is available."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.showOnLockScreen,
                onCheckedChange = callbacks.onLockScreenVisibilityChanged,
            )
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Background access", style = MaterialTheme.typography.titleMedium)
                Text(batteryStatusText(setupState.batteryStatus))
                OutlinedButton(onClick = callbacks.onOpenBatterySettings) {
                    Text("Open battery settings")
                }
            }
        }
        if (setupState.startFailed) {
            StatusMessage("Monitoring did not start. Check notification and battery settings, then try again.", true)
        }
        Button(
            onClick = callbacks.onStartMonitoring,
            modifier = Modifier.fillMaxWidth(),
            enabled = setupState.notificationPermission == PermissionStatus.GRANTED && !setupState.startingMonitoring,
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Notification permission", style = MaterialTheme.typography.titleMedium)
            Text(
                when (status) {
                    PermissionStatus.UNKNOWN -> "Not requested yet"
                    PermissionStatus.GRANTED -> "Allowed"
                    PermissionStatus.DENIED -> "Not allowed. Glance cannot show its ongoing reading."
                },
            )
            when (status) {
                PermissionStatus.UNKNOWN -> Button(onClick = callbacks.onRequestNotificationPermission) {
                    Text("Allow notifications")
                }
                PermissionStatus.DENIED -> OutlinedButton(onClick = callbacks.onOpenNotificationSettings) {
                    Text("Open notification settings")
                }
                PermissionStatus.GRANTED -> Unit
            }
        }
    }
}

@Composable
internal fun ScreenColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
internal fun StatusMessage(text: String, isError: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .semantics { contentDescription = text },
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
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
