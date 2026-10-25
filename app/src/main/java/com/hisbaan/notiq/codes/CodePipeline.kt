package com.hisbaan.notiq.codes

import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.actions.CodeNotification
import com.hisbaan.notiq.core.Code
import com.hisbaan.notiq.core.CodeFilter
import com.hisbaan.notiq.notification.ReadNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** From a read notification to the actions: source policy, redaction alarm, filter, copy, notify. */
class CodePipeline(private val app: NotiqApp) {
    /** Touched only on the main thread (app.scope). */
    private val filter = CodeFilter()

    private val _recent = MutableStateFlow<List<Code>>(emptyList())

    /** Codes acted on in the last [RECENT_MS], newest first. In memory only. */
    val recent: StateFlow<List<Code>> = _recent

    fun onNotification(notification: ReadNotification) {
        // Settings may still be loading when the listener starts the process.
        app.scope.launch {
            val settings = app.settings.current()
            if (!notification.test && !settings.sources.accepts(notification.packageName, notification.sensitive)) return@launch
            if (notification.redacted) {
                app.access.onRedacted(notification)
                return@launch
            }
            val message = notification.message ?: return@launch
            val code = notification.code ?: return@launch
            val now = System.currentTimeMillis()
            if (!filter.accept(message, code, now)) return@launch

            val found = Code(code, message, now)
            _recent.update { listOf(found) + it.filter { c -> now - c.at < RECENT_MS } }
            if (settings.copyCodes) app.clipboard.copyCode(code, settings.clearAfterSeconds)
            if (settings.showNotification) CodeNotification.post(app, found, copied = settings.copyCodes)
            app.diagnostics.event("Code from ${message.app}${if (settings.copyCodes) ", copied" else ""}")
        }
    }

    /** Drops expired codes; the home screen calls it when shown. */
    fun prune() {
        val now = System.currentTimeMillis()
        _recent.update { it.filter { c -> now - c.at < RECENT_MS } }
    }

    private companion object {
        const val RECENT_MS = 10 * 60_000L
    }
}
