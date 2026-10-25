package com.hisbaan.notiq

import android.app.Application
import com.hisbaan.notiq.probe.ProbeLog
import com.hisbaan.notiq.probe.TestNotification
import com.hisbaan.notiq.settings.SettingsRepository
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
    val probe by lazy { ProbeLog(filesDir.resolve("probe.txt")) }

    private val _setup = MutableStateFlow(SetupStatus())
    val setup: StateFlow<SetupStatus> = _setup

    lateinit var settings: SettingsRepository
        private set
    lateinit var shizuku: ShizukuShell
        private set
    lateinit var grants: Grants
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this, scope)
        shizuku = ShizukuShell(this)
        grants = Grants(this)
        TestNotification.createChannel(this)
        refreshSetup()
    }

    fun refreshSetup() {
        _setup.value = SetupStatus.check(this)
    }
}
