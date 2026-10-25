package com.hisbaan.notiq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.hisbaan.notiq.settings.NotiqSettings

@Composable
fun SettingsScreen(settings: NotiqSettings, update: ((NotiqSettings) -> NotiqSettings) -> Unit, onBack: () -> Unit) {
    Page("Settings", onBack = onBack) {
        Section("When a code arrives") {
            SwitchRow("Copy it to the clipboard", settings.copyCodes) { v -> update { it.copy(copyCodes = v) } }
            SwitchRow(
                "Show a notification",
                settings.showNotification,
                hint = "The code and a Copy button. Silent; the message itself already alerted.",
            ) { v -> update { it.copy(showNotification = v) } }
        }
        Section("Clear the clipboard") {
            Hint(
                "Only if it still holds the code: anything you've copied since is left alone. Checking needs " +
                    "Shizuku running (Android doesn't let apps read the clipboard in the background); without it, " +
                    "nothing is cleared.",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CLEAR_OPTIONS.forEach { (seconds, label) ->
                    FilterChip(
                        selected = settings.clearAfterSeconds == seconds,
                        onClick = { update { it.copy(clearAfterSeconds = seconds) } },
                        label = { Text(label) },
                    )
                }
            }
        }
        Section("Read codes from") {
            SwitchRow(
                "Messaging apps",
                settings.messagingApps,
                hint = "Messages, Signal, WhatsApp, Telegram and other SMS apps",
            ) { v -> update { it.copy(messagingApps = v) } }
            SwitchRow("Email apps", settings.emailApps, hint = "Gmail, Outlook, Proton Mail, Fastmail, K-9, FairEmail") { v ->
                update { it.copy(emailApps = v) }
            }
            SwitchRow(
                "Any app, when Android spots a code",
                settings.systemFlagged,
                hint = "Uses Android's own code detection (Pixel), so banking and other apps work too",
            ) { v -> update { it.copy(systemFlagged = v) } }
            SwitchRow(
                "Any app",
                settings.allApps,
                hint = "Every notification goes through Notiq's code detection. More likely to copy something that isn't a code.",
            ) { v -> update { it.copy(allApps = v) } }
        }
    }
}

private val CLEAR_OPTIONS = listOf(0 to "Never", 30 to "30 s", 60 to "1 min", 120 to "2 min")
