package com.hisbaan.notiq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.hisbaan.notiq.core.FillMode
import com.hisbaan.notiq.settings.NotiqSettings

/** The fill service's state and what the settings screen can do about it. */
class FillSetup(
    val serviceOn: Boolean,
    val shizukuReady: Boolean,
    val granting: Boolean,
    val enableWithShizuku: () -> Unit,
    val openAccessibilitySettings: () -> Unit,
    val openTryIt: () -> Unit,
)

@Composable
fun SettingsScreen(settings: NotiqSettings, fill: FillSetup, update: ((NotiqSettings) -> NotiqSettings) -> Unit, onBack: () -> Unit) {
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
        Section("Fill codes into apps") {
            Check(
                "Fill service",
                fill.serviceOn,
                detail = if (fill.serviceOn) {
                    null
                } else {
                    "An accessibility service. It only looks at the screen for a few minutes after a code arrives."
                },
            ) {
                if (fill.shizukuReady) {
                    Button(onClick = fill.enableWithShizuku, enabled = !fill.granting) { Text("Turn on") }
                } else {
                    FilledTonalButton(onClick = fill.openAccessibilitySettings) { Text("Open") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FILL_MODES.forEach { (mode, label) ->
                    FilterChip(
                        selected = settings.fillMode == mode,
                        onClick = { update { it.copy(fillMode = mode) } },
                        label = { Text(label) },
                    )
                }
            }
            Hint(
                when (settings.fillMode) {
                    FillMode.CHIP -> "A \"Fill\" chip appears above the keyboard when a field looks like it wants the code. Nothing is typed until you tap it."
                    FillMode.AUTO_WHEN_SURE ->
                        "Fills fields that are clearly code fields (labelled as one, or one box per digit). For less certain ones, the chip."
                    FillMode.AUTO -> "Fills any field that looks like it wants the code. If Notiq guesses wrong, the code lands in the wrong field."
                },
            )
            SwitchRow(
                "Press Enter after filling",
                settings.submitAfterFill,
                hint = "If the app hasn't moved on by itself a quarter second later. Uses the field's Done or Go action, " +
                    "or presses Enter through Shizuku when the field has none (web pages).",
            ) { v -> update { it.copy(submitAfterFill = v) } }
            OutlinedButton(onClick = fill.openTryIt) { Text("Try it") }
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

private val FILL_MODES = listOf(FillMode.CHIP to "Tap to fill", FillMode.AUTO_WHEN_SURE to "Auto when sure", FillMode.AUTO to "Always auto")

private val CLEAR_OPTIONS = listOf(0 to "Never", 30 to "30 s", 60 to "1 min", 120 to "2 min")
