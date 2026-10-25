package com.hisbaan.notiq

import android.Manifest
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisbaan.notiq.diagnostics.TestNotification
import com.hisbaan.notiq.listener.NotiqListener
import com.hisbaan.notiq.settings.NotiqSettings
import com.hisbaan.notiq.ui.DiagnosticsActions
import com.hisbaan.notiq.ui.DiagnosticsScreen
import com.hisbaan.notiq.ui.DiagnosticsState
import com.hisbaan.notiq.ui.HomeScreen
import com.hisbaan.notiq.ui.NotiqTheme
import com.hisbaan.notiq.ui.SettingsScreen
import com.hisbaan.notiq.ui.SetupActions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private enum class Screen { HOME, SETTINGS, DIAGNOSTICS }

    private val app get() = application as NotiqApp

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        app.refreshSetup()
    }

    private val associationLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        app.refreshSetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val setupActions = SetupActions(
            openListenerSettings = {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotiqListener.component(this).flattenToString()),
                )
            },
            requestNotifications = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            requestBattery = {
                // Notiq's only job is reacting to notifications in the background.
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", packageName, null)))
            },
            openAppInfo = {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            },
            requestShizuku = app.shizuku::requestPermission,
            setUpWithShizuku = { app.scope.launch { app.grants.setUp() } },
            copyAdbCommand = { app.clipboard.copyText("adb command", app.grants.adbCommand) },
        )
        val diagnosticsActions = DiagnosticsActions(
            postTestCode = ::postTestCode,
            rebindListener = { NotiqListener.rebind(app) },
            grantWithShizuku = { app.scope.launch { app.grants.setUp() } },
            revokeWithShizuku = { app.scope.launch { app.grants.revokeSensitive() } },
            associate = ::associate,
            disassociateAll = ::disassociateAll,
            copyLog = { app.clipboard.copyText("Notiq diagnostics", app.diagnostics.export()) },
            clearLog = app.diagnostics::clear,
        )
        val update = { transform: (NotiqSettings) -> NotiqSettings -> app.scope.launch { app.settings.update(transform) }; Unit }
        setContent {
            NotiqTheme {
                val setup by app.setup.collectAsStateWithLifecycle()
                val shizuku by app.shizuku.state.collectAsStateWithLifecycle()
                val granting by app.grants.running.collectAsStateWithLifecycle()
                val settings by app.settings.settings.collectAsStateWithLifecycle()
                val codes by app.pipeline.recent.collectAsStateWithLifecycle()
                val notifications by app.diagnostics.notifications.collectAsStateWithLifecycle()
                val events by app.diagnostics.events.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        setup,
                        shizuku,
                        granting,
                        app.grants.adbCommand,
                        codes,
                        setupActions,
                        copyCode = { app.clipboard.copyCode(it, settings?.clearAfterSeconds ?: 0) },
                        openSettings = { screen = Screen.SETTINGS },
                        openDiagnostics = { screen = Screen.DIAGNOSTICS },
                    )
                    Screen.SETTINGS -> settings?.let { SettingsScreen(it, update, onBack = { screen = Screen.HOME }) }
                    Screen.DIAGNOSTICS -> DiagnosticsScreen(
                        DiagnosticsState(setup, shizuku, granting, notifications, events),
                        diagnosticsActions,
                        onBack = { screen = Screen.HOME },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.refreshSetup()
        app.shizuku.refresh()
        app.pipeline.prune()
    }

    private fun postTestCode() {
        Toast.makeText(this, "Posting a test code in 5 s", Toast.LENGTH_SHORT).show()
        app.scope.launch {
            delay(5_000)
            val code = TestNotification.post(app)
            app.diagnostics.event(if (code != null) "Posted test code $code" else "Test code not posted: no notification permission")
        }
    }

    /** Phase 0 experiment: does a companion association make the listener trusted? */
    private fun associate() {
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
        val request = AssociationRequest.Builder().setSingleDevice(false).build()
        manager.associate(
            request,
            mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    associationLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    app.diagnostics.event("Companion association created: ${associationInfo.displayName ?: associationInfo.id}")
                    app.refreshSetup()
                }

                override fun onFailure(error: CharSequence?) {
                    app.diagnostics.event("Companion association failed: $error")
                }
            },
        )
    }

    private fun disassociateAll() {
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
        manager.myAssociations.forEach { manager.disassociate(it.id) }
        app.diagnostics.event("Companion associations removed")
        app.refreshSetup()
    }
}
