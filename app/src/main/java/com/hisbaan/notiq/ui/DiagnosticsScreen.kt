package com.hisbaan.notiq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hisbaan.notiq.diagnostics.LoggedEvent
import com.hisbaan.notiq.diagnostics.LoggedNotification
import com.hisbaan.notiq.diagnostics.time
import com.hisbaan.notiq.setup.SetupStatus
import com.hisbaan.notiq.shizuku.ShizukuShell

class DiagnosticsState(
    val setup: SetupStatus,
    val shizuku: ShizukuShell.State,
    val granting: Boolean,
    val notifications: List<LoggedNotification>,
    val events: List<LoggedEvent>,
)

class DiagnosticsActions(
    val postTestCode: () -> Unit,
    val rebindListener: () -> Unit,
    val grantWithShizuku: () -> Unit,
    val revokeWithShizuku: () -> Unit,
    val associate: () -> Unit,
    val disassociateAll: () -> Unit,
    val copyLog: () -> Unit,
    val clearLog: () -> Unit,
)

/** Everything the listener reads, events, and the Phase 0 experiments. */
@Composable
fun DiagnosticsScreen(state: DiagnosticsState, actions: DiagnosticsActions, onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PageHeader("Diagnostics", onBack) }
            item { StatusSection(state.setup) }
            item { ExperimentsSection(state, actions) }
            item { EventsSection(state.events) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Notifications (${state.notifications.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = actions.copyLog) { Text("Copy log") }
                    TextButton(onClick = actions.clearLog) { Text("Clear") }
                }
            }
            if (state.notifications.isEmpty()) {
                item { Hint("Nothing yet. Notifications appear here as they're posted (ongoing ones, media players and group summaries are skipped).") }
            }
            items(state.notifications) { NotificationCard(it) }
        }
    }
}

@Composable
private fun StatusSection(setup: SetupStatus) {
    Section("Status") {
        Text("Listener: ${if (setup.listenerConnected) "connected" else if (setup.listenerEnabled) "enabled, not connected" else "not enabled"}")
        Text("Sensitive notifications app-op: ${setup.sensitive.name.lowercase().replace('_', ' ')}")
        Text("Companion associations: ${setup.companionAssociations}")
    }
}

@Composable
private fun ExperimentsSection(state: DiagnosticsState, actions: DiagnosticsActions) {
    Section("Experiments") {
        FilledTonalButton(onClick = actions.postTestCode) { Text("Post a test code in 5 s") }
        Hint("Goes through the whole pipeline. Android doesn't flag it as a code, so it never tests redaction; use a real SMS for that.")
        OutlinedButton(onClick = actions.rebindListener) { Text("Rebind listener") }
        if (state.shizuku == ShizukuShell.State.READY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.grantWithShizuku, enabled = !state.granting) { Text("Grant with Shizuku") }
                OutlinedButton(onClick = actions.revokeWithShizuku, enabled = !state.granting) { Text("Revoke sensitive") }
            }
            Hint("Revoke, then a real SMS code: Notiq should see it redacted, re-grant on its own and recover the code.")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.associate) { Text("Associate a device") }
            if (state.setup.companionAssociations > 0) {
                TextButton(onClick = actions.disassociateAll) { Text("Remove all") }
            }
        }
        Hint("A companion association also makes the listener trusted; rebind after.")
    }
}

@Composable
private fun EventsSection(events: List<LoggedEvent>) {
    Section("Events") {
        if (events.isEmpty()) Hint("Nothing yet.")
        events.take(30).forEach {
            Text(it.describe(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun NotificationCard(logged: LoggedNotification) {
    val n = logged.notification
    Section(n.app) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val flags = listOfNotNull(
                if (n.update) "update" else "new",
                n.template?.substringAfterLast('$'),
                n.category,
                "sensitive=${n.sensitive ?: "?"}",
            )
            Hint("${time(logged.at)} · ${flags.joinToString(" · ")}")
            if (n.redacted) Text("REDACTED", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            n.code?.let { Text("Code: $it", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            n.title?.let { Text(it, fontWeight = FontWeight.Medium) }
            n.text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            n.bigText?.takeIf { it != n.text }?.let { Text("Big: $it", style = MaterialTheme.typography.bodySmall) }
            n.messages.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
