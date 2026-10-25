package com.hisbaan.notiq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hisbaan.notiq.probe.ProbeEntry
import com.hisbaan.notiq.probe.ProbeEvent
import com.hisbaan.notiq.probe.time
import com.hisbaan.notiq.setup.SetupStatus
import com.hisbaan.notiq.shizuku.ShizukuShell

class MainState(
    val setup: SetupStatus,
    val shizuku: ShizukuShell.State,
    /** A Shizuku set-up or revoke is running. */
    val granting: Boolean,
    val adbCommand: String,
    val copyCodes: Boolean,
    val entries: List<ProbeEntry>,
    val events: List<ProbeEvent>,
)

class MainActions(
    val openListenerSettings: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestBattery: () -> Unit,
    val openAppInfo: () -> Unit,
    val requestShizuku: () -> Unit,
    val setUpWithShizuku: () -> Unit,
    val revokeWithShizuku: () -> Unit,
    val copyAdbCommand: () -> Unit,
    val postTestCode: () -> Unit,
    val setCopyCodes: (Boolean) -> Unit,
    val rebindListener: () -> Unit,
    val associate: () -> Unit,
    val disassociateAll: () -> Unit,
    val copyLog: () -> Unit,
    val clearLog: () -> Unit,
)

@Composable
fun MainScreen(state: MainState, actions: MainActions) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Text("Notiq", style = MaterialTheme.typography.headlineMedium) }
            item { SetupSection(state.setup, actions) }
            item { ShizukuSection(state.shizuku, state.granting, state.adbCommand, actions) }
            item { ExperimentsSection(state, actions) }
            item { EventsSection(state.events) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications (${state.entries.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = actions.copyLog) { Text("Copy log") }
                    TextButton(onClick = actions.clearLog) { Text("Clear") }
                }
            }
            if (state.entries.isEmpty()) item { Hint("Nothing yet. Notifications appear here as they're posted (ongoing ones are skipped).") }
            items(state.entries) { EntryCard(it) }
        }
    }
}

@Composable
private fun SetupSection(setup: SetupStatus, actions: MainActions) {
    Section("Setup") {
        Check(
            "Notification access",
            setup.listenerEnabled,
            detail = if (setup.listenerEnabled) (if (setup.listenerConnected) "Connected" else "Enabled, not connected") else null,
        ) { FilledTonalButton(onClick = actions.openListenerSettings) { Text("Open") } }
        Check(
            "Sensitive notifications",
            setup.sensitive == SetupStatus.Sensitive.ALLOWED,
            detail = when (setup.sensitive) {
                SetupStatus.Sensitive.ALLOWED -> "App-op allowed. Takes effect when the listener binds."
                SetupStatus.Sensitive.NOT_ALLOWED -> "Not granted: codes may arrive hidden. Use Shizuku or adb below."
                SetupStatus.Sensitive.UNKNOWN -> "This Android version has no such app-op."
            },
        )
        Check("Post notifications", setup.postNotifications) {
            FilledTonalButton(onClick = actions.requestNotifications) { Text("Allow") }
        }
        Check("Unrestricted battery", setup.battery) {
            FilledTonalButton(onClick = actions.requestBattery) { Text("Allow") }
        }
        Hint("Notification access greyed out? Sideloaded apps need App info → ⋮ → Allow restricted settings first.")
        OutlinedButton(onClick = actions.openAppInfo) { Text("App info") }
    }
}

@Composable
private fun ShizukuSection(state: ShizukuShell.State, granting: Boolean, adbCommand: String, actions: MainActions) {
    Section("Shizuku") {
        when (state) {
            ShizukuShell.State.NOT_INSTALLED -> Hint("Not installed. Install Shizuku to grant everything with one tap, or use the adb command.")
            ShizukuShell.State.NOT_RUNNING -> Hint("Installed but not running. Start it from the Shizuku app (wireless debugging), then come back.")
            ShizukuShell.State.NO_PERMISSION -> Button(onClick = actions.requestShizuku) { Text("Allow Notiq in Shizuku") }
            ShizukuShell.State.READY -> {
                Hint("Grants restricted settings, notifications, sensitive notifications, notification access and unrestricted battery.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = actions.setUpWithShizuku, enabled = !granting) { Text("Set up with Shizuku") }
                    OutlinedButton(onClick = actions.revokeWithShizuku, enabled = !granting) { Text("Revoke sensitive") }
                }
            }
        }
        Hint("Without Shizuku, run this from a computer:")
        Text(adbCommand, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        OutlinedButton(onClick = actions.copyAdbCommand) { Text("Copy command") }
    }
}

@Composable
private fun ExperimentsSection(state: MainState, actions: MainActions) {
    Section("Experiments") {
        FilledTonalButton(onClick = actions.postTestCode) { Text("Post a test code in 5 s") }
        Hint("Leave the app or lock the phone before it arrives to test the background case.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Copy codes to clipboard")
                Hint("Every extracted code, from any app.")
            }
            Switch(checked = state.copyCodes, onCheckedChange = actions.setCopyCodes)
        }
        OutlinedButton(onClick = actions.rebindListener) { Text("Rebind listener") }
        Hint("The system checks whether the listener is trusted when it binds; rebind after changing a grant.")
        Text("Companion associations: ${state.setup.companionAssociations}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.associate) { Text("Associate a device") }
            if (state.setup.companionAssociations > 0) {
                TextButton(onClick = actions.disassociateAll) { Text("Remove all") }
            }
        }
        Hint("Pick any nearby device (headphones, a laptop). An association also makes the listener trusted; rebind after.")
    }
}

@Composable
private fun EventsSection(events: List<ProbeEvent>) {
    Section("Events") {
        if (events.isEmpty()) Hint("Nothing yet.")
        events.take(30).forEach {
            Text(it.describe(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun EntryCard(entry: ProbeEntry) {
    Section(entry.app) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val flags = listOfNotNull(
                if (entry.update) "update" else "new",
                "summary".takeIf { entry.groupSummary },
                entry.template?.substringAfterLast('$'),
                entry.category,
                "sensitive=${entry.sensitive ?: "?"}",
            )
            Hint("${time(entry.at)} · ${flags.joinToString(" · ")}")
            if (entry.redacted) Text("REDACTED", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            entry.code?.let { Text("Code: $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            entry.title?.let { Text(it, fontWeight = FontWeight.Medium) }
            entry.text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            entry.bigText?.takeIf { it != entry.text }?.let { Text("Big: $it", style = MaterialTheme.typography.bodySmall) }
            entry.messages.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
