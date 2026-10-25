package com.hisbaan.notiq.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

data class ShellResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Runs shell commands through Shizuku, for setup steps that otherwise need adb. Optional: when
 * Shizuku isn't installed or running, [state] says so and nothing else breaks.
 */
class ShizukuShell(private val context: Context) {
    enum class State { NOT_INSTALLED, NOT_RUNNING, NO_PERMISSION, READY }

    private val _state = MutableStateFlow(State.NOT_RUNNING)
    val state: StateFlow<State> = _state

    private val mutex = Mutex()
    private var service: IShellService? = null

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        .version(context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt())

    init {
        // Shizuku's listeners are called on the main thread.
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        _state.value = when {
            Shizuku.pingBinder() && !Shizuku.isPreV11() ->
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) State.READY else State.NO_PERMISSION
            installed() -> State.NOT_RUNNING
            else -> State.NOT_INSTALLED
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST)
    }

    /** Runs [command] as the shell user. Throws if Shizuku isn't ready. */
    suspend fun run(command: List<String>): ShellResult {
        val result = withContext(Dispatchers.IO) { service().run(command.toTypedArray()) }
        return ShellResult(result.getInt(ShellService.EXIT), result.getString(ShellService.OUTPUT).orEmpty())
    }

    private suspend fun service(): IShellService = mutex.withLock {
        service?.takeIf { it.asBinder().pingBinder() } ?: withContext(Dispatchers.Main) {
            withTimeout(BIND_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            if (binder == null || !binder.pingBinder()) return
                            val bound = IShellService.Stub.asInterface(binder)
                            service = bound
                            if (continuation.isActive) continuation.resume(bound)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            service = null
                        }
                    }
                    Shizuku.bindUserService(args, connection)
                }
            }
        }
    }

    private fun installed(): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST = 1
        const val BIND_TIMEOUT_MS = 10_000L
    }
}
