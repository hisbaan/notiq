package com.hisbaan.notiq.diagnostics

import com.hisbaan.notiq.notification.ReadNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LoggedNotification(val at: Long, val notification: ReadNotification) {
    fun describe(): String = "${time(at)} ${notification.describe()}"
}

/** What happened besides notifications: listener binding, grants, codes acted on. */
data class LoggedEvent(val at: Long, val text: String) {
    fun describe(): String = "${time(at)} ${text.indent()}"
}

/**
 * Every notification the listener reads, and events, for the Diagnostics screen. In memory; also
 * appended to [file] when set (debuggable builds:
 * `adb shell run-as com.hisbaan.notiq cat files/diagnostics.txt`). It holds codes and message
 * text, so nothing here goes to logcat.
 */
class DiagnosticsLog(private val file: File?) {
    private val _notifications = MutableStateFlow<List<LoggedNotification>>(emptyList())

    /** Newest first. */
    val notifications: StateFlow<List<LoggedNotification>> = _notifications

    private val _events = MutableStateFlow<List<LoggedEvent>>(emptyList())

    /** Newest first. */
    val events: StateFlow<List<LoggedEvent>> = _events

    fun add(notification: ReadNotification) {
        val logged = LoggedNotification(System.currentTimeMillis(), notification)
        _notifications.update { (listOf(logged) + it).take(MAX) }
        append(logged.describe())
    }

    fun event(text: String) {
        val event = LoggedEvent(System.currentTimeMillis(), text)
        _events.update { (listOf(event) + it).take(MAX) }
        append(event.describe() + "\n")
    }

    fun clear() {
        _notifications.value = emptyList()
        _events.value = emptyList()
        file?.delete()
    }

    fun export(): String = buildString {
        appendLine("== Events")
        _events.value.forEach { appendLine(it.describe()) }
        appendLine()
        appendLine("== Notifications")
        _notifications.value.forEach { appendLine(it.describe()) }
    }

    private fun append(text: String) {
        val file = file ?: return
        runCatching {
            file.appendText(text)
            if (file.length() > MAX_FILE_CHARS) trim(file)
        }
    }

    /**
     * Keeps the newest half of the file, from the first whole entry. Every entry starts with its
     * time at the start of a line; continuation lines are indented ([indent]).
     */
    private fun trim(file: File) {
        val text = file.readText()
        val start = ENTRY_START.find(text, (text.length - MAX_FILE_CHARS / 2).coerceAtLeast(0))?.range?.first ?: text.length
        file.writeText(text.substring(start))
    }

    private companion object {
        /** Per list, in memory. */
        const val MAX = 300

        /** The file is cut back to half this when it grows past it; chars, about bytes. */
        const val MAX_FILE_CHARS = 512 * 1024

        val ENTRY_START = Regex("""(?m)^\d\d:\d\d:\d\d """)
    }
}

/** Indents every line after the first, so only an entry's first line starts at column 0. */
fun String.indent(): String = replace("\n", "\n    ")

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun time(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis))
