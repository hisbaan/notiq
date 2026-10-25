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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisbaan.notiq.listener.NotiqListener
import com.hisbaan.notiq.probe.Clipboard
import com.hisbaan.notiq.probe.TestNotification
import com.hisbaan.notiq.ui.MainActions
import com.hisbaan.notiq.ui.MainScreen
import com.hisbaan.notiq.ui.MainState
import com.hisbaan.notiq.ui.NotiqTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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
        val actions = MainActions(
            openListenerSettings = {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, NotiqListener.component(this).flattenToString()),
                )
            },
            requestNotifications = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            requestBattery = {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", packageName, null)))
            },
            openAppInfo = {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            },
            requestShizuku = app.shizuku::requestPermission,
            setUpWithShizuku = { app.scope.launch { app.grants.setUp() } },
            revokeWithShizuku = { app.scope.launch { app.grants.revokeSensitive() } },
            copyAdbCommand = { Clipboard.copyText(this, "adb command", app.grants.adbCommand) },
            postTestCode = ::postTestCode,
            setCopyCodes = { value -> app.scope.launch { app.settings.update { it.copy(copyCodes = value) } } },
            rebindListener = { NotiqListener.rebind(app) },
            associate = ::associate,
            disassociateAll = ::disassociateAll,
            copyLog = { Clipboard.copyText(this, "Notiq probe log", app.probe.export()) },
            clearLog = app.probe::clear,
        )
        setContent {
            NotiqTheme {
                val setup by app.setup.collectAsStateWithLifecycle()
                val shizuku by app.shizuku.state.collectAsStateWithLifecycle()
                val granting by app.grants.running.collectAsStateWithLifecycle()
                val settings by app.settings.settings.collectAsStateWithLifecycle()
                val entries by app.probe.entries.collectAsStateWithLifecycle()
                val events by app.probe.events.collectAsStateWithLifecycle()
                MainScreen(MainState(setup, shizuku, granting, app.grants.adbCommand, settings?.copyCodes ?: false, entries, events), actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.refreshSetup()
        app.shizuku.refresh()
    }

    private fun postTestCode() {
        Toast.makeText(this, "Posting a test code in 5 s", Toast.LENGTH_SHORT).show()
        app.scope.launch {
            delay(5_000)
            val code = TestNotification.post(app)
            app.probe.event(if (code != null) "Posted test code $code" else "Test code not posted: no notification permission")
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
                    app.probe.event("Companion association created: ${associationInfo.displayName ?: associationInfo.id}")
                    app.refreshSetup()
                }

                override fun onFailure(error: CharSequence?) {
                    app.probe.event("Companion association failed: $error")
                }
            },
        )
    }

    private fun disassociateAll() {
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
        manager.myAssociations.forEach { manager.disassociate(it.id) }
        app.probe.event("Companion associations removed")
        app.refreshSetup()
    }
}
