package com.hisbaan.notiq

import android.app.Application
import android.content.pm.ApplicationInfo
import com.hisbaan.notiq.actions.Clipboard
import com.hisbaan.notiq.actions.CodeNotification
import com.hisbaan.notiq.codes.CodePipeline
import com.hisbaan.notiq.diagnostics.DiagnosticsLog
import com.hisbaan.notiq.diagnostics.TestNotification
import com.hisbaan.notiq.settings.SettingsRepository
import com.hisbaan.notiq.setup.AccessGuard
import com.hisbaan.notiq.setup.Grants
import com.hisbaan.notiq.setup.SetupStatus
import com.hisbaan.notiq.shizuku.ShizukuShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotiqApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _setup = MutableStateFlow(SetupStatus())
    val setup: StateFlow<SetupStatus> = _setup

    lateinit var diagnostics: DiagnosticsLog
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var shizuku: ShizukuShell
        private set
    lateinit var grants: Grants
        private set
    lateinit var access: AccessGuard
        private set
    lateinit var clipboard: Clipboard
        private set
    lateinit var pipeline: CodePipeline
        private set

    override fun onCreate() {
        super.onCreate()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        diagnostics = DiagnosticsLog(filesDir.resolve("diagnostics.txt").takeIf { debuggable })
        settings = SettingsRepository(this, scope)
        shizuku = ShizukuShell(this)
        grants = Grants(this)
        access = AccessGuard(this)
        clipboard = Clipboard(this)
        pipeline = CodePipeline(this)
        CodeNotification.createChannel(this)
        AccessGuard.createChannel(this)
        TestNotification.createChannel(this)
        refreshSetup()
    }

    fun refreshSetup() {
        val status = SetupStatus.check(this)
        _setup.value = status
        if (status.sensitive == SetupStatus.Sensitive.ALLOWED) access.cancelAlert()
    }
}
