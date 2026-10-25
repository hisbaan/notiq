package com.hisbaan.notiq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hisbaan.notiq.core.Code
import com.hisbaan.notiq.diagnostics.time
import com.hisbaan.notiq.setup.SetupStatus
import com.hisbaan.notiq.shizuku.ShizukuShell

class SetupActions(
    val openListenerSettings: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestBattery: () -> Unit,
    val openAppInfo: () -> Unit,
    val requestShizuku: () -> Unit,
    val setUpWithShizuku: () -> Unit,
    val copyAdbCommand: () -> Unit,
)

@Composable
fun HomeScreen(
    setup: SetupStatus,
    shizuku: ShizukuShell.State,
    granting: Boolean,
    adbCommand: String,
    codes: List<Code>,
    actions: SetupActions,
    copyCode: (String) -> Unit,
    openSettings: () -> Unit,
    openDiagnostics: () -> Unit,
) {
    Page(
        "Notiq",
        action = {
            TextButton(onClick = openDiagnostics) { Text("Diagnostics") }
            TextButton(onClick = openSettings) { Text("Settings") }
        },
    ) {
        if (!setup.complete) SetupSection(setup, shizuku, granting, adbCommand, actions)
        Section("Recent codes") {
            if (codes.isEmpty()) {
                Hint(
                    if (setup.complete) "Codes from your messages show up here for 10 minutes." else "Finish setup, then codes from your messages show up here.",
                )
            }
            codes.forEach { CodeRow(it, copyCode) }
        }
    }
}

@Composable
private fun SetupSection(
    setup: SetupStatus,
    shizuku: ShizukuShell.State,
    granting: Boolean,
    adbCommand: String,
    actions: SetupActions,
) {
    Section("Setup") {
        if (shizuku == ShizukuShell.State.READY) {
            Button(onClick = actions.setUpWithShizuku, enabled = !granting) { Text("Set up everything with Shizuku") }
        }
        Check("Notification access", setup.listenerEnabled) {
            FilledTonalButton(onClick = actions.openListenerSettings) { Text("Open") }
        }
        Check(
            "Codes in notifications",
            setup.sensitiveOk,
            detail = if (setup.sensitiveOk) null else "Android hides codes from Notiq until this is granted, with Shizuku or adb.",
        )
        Check("Post notifications", setup.postNotifications) {
            FilledTonalButton(onClick = actions.requestNotifications) { Text("Allow") }
        }
        Check("Unrestricted battery", setup.battery) {
            FilledTonalButton(onClick = actions.requestBattery) { Text("Allow") }
        }
        when (shizuku) {
            ShizukuShell.State.READY -> {}
            ShizukuShell.State.NO_PERMISSION -> Button(onClick = actions.requestShizuku) { Text("Allow Notiq in Shizuku") }
            ShizukuShell.State.NOT_RUNNING -> Hint("Shizuku is installed but not running. Start it, then come back to grant everything in one tap.")
            ShizukuShell.State.NOT_INSTALLED -> Hint("With Shizuku installed, Notiq can grant all of this in one tap.")
        }
        if (!setup.sensitiveOk) {
            Hint("Or from a computer:")
            Text(adbCommand, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            OutlinedButton(onClick = actions.copyAdbCommand) { Text("Copy command") }
        }
        if (!setup.listenerEnabled) {
            Hint("Notification access greyed out? Sideloaded apps need App info → ⋮ → Allow restricted settings first.")
            OutlinedButton(onClick = actions.openAppInfo) { Text("App info") }
        }
    }
}

@Composable
private fun CodeRow(code: Code, copy: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(code.code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
            val from = listOfNotNull(code.message.app, code.message.sender?.takeIf { it != code.message.app })
            Hint("${time(code.at)} · ${from.joinToString(" · ")}")
        }
        FilledTonalButton(onClick = { copy(code.code) }) { Text("Copy") }
    }
}
