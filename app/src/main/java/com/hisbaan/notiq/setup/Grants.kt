package com.hisbaan.notiq.setup

import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.listener.NotiqListener
import com.hisbaan.notiq.shizuku.ShizukuShell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Setup steps that need shell access: run through Shizuku, or shown as adb commands. */
class Grants(private val app: NotiqApp) {
    private val pkg = app.packageName
    private val user = ShizukuShell.currentUser().toString()
    private val listener = NotiqListener.component(app).flattenToString()

    /**
     * Everything setup needs, in order. The listener is (re)enabled after the app-op is set:
     * enabling it is what makes the system re-run its trusted-listener check (PLAN.md §1).
     */
    private val setUpCommands = listOf(
        listOf("cmd", "appops", "set", "--user", user, pkg, "ACCESS_RESTRICTED_SETTINGS", "allow"),
        listOf("pm", "grant", "--user", user, pkg, "android.permission.POST_NOTIFICATIONS"),
        listOf("cmd", "appops", "set", "--user", user, pkg, SENSITIVE_OP, "allow"),
        listOf("cmd", "notification", "allow_listener", listener, user),
        listOf("cmd", "deviceidle", "whitelist", "+$pkg"),
    )

    /**
     * For testing the "before the grant" state. `ignore` is the op's default mode. Enabling the
     * listener only ever adds trust; disabling it is what drops the cached trust.
     */
    private val revokeCommands = listOf(
        listOf("cmd", "appops", "set", "--user", user, pkg, SENSITIVE_OP, "ignore"),
        listOf("cmd", "notification", "disallow_listener", listener, user),
        listOf("cmd", "notification", "allow_listener", listener, user),
    )

    /** The fallback when Shizuku isn't available. */
    val adbCommand: String =
        "adb shell cmd appops set --user $user $pkg $SENSITIVE_OP allow && adb shell cmd notification allow_listener $listener $user"

    suspend fun setUp() = runAll(setUpCommands)

    suspend fun revokeSensitive() = runAll(revokeCommands)

    private val _running = MutableStateFlow(false)

    /** A set-up or revoke is in progress; another tap is ignored until it's done. */
    val running: StateFlow<Boolean> = _running

    private suspend fun runAll(commands: List<List<String>>) {
        if (_running.value) return
        _running.value = true
        try {
            runEach(commands)
        } finally {
            _running.value = false
        }
        app.refreshSetup()
    }

    private suspend fun runEach(commands: List<List<String>>) {
        for (command in commands) {
            val line = command.joinToString(" ")
            val result = runCatching { app.shizuku.run(command) }
            app.diagnostics.event(
                result.fold(
                    { "$ $line → exit ${it.exitCode}${if (it.output.isNotEmpty()) ": ${it.output}" else ""}" },
                    { "$ $line → failed: $it" },
                ),
            )
        }
    }

    private companion object {
        const val SENSITIVE_OP = "RECEIVE_SENSITIVE_NOTIFICATIONS"
    }
}
