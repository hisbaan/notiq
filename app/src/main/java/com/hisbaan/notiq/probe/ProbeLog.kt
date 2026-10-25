package com.hisbaan.notiq.probe

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One notification as the listener received it. */
data class ProbeEntry(
    /** Epoch millis, when the listener got it. */
    val at: Long,
    val packageName: String,
    val app: String,
    val key: String,
    /** Same key seen before (an update to a posted notification). */
    val update: Boolean,
    val groupSummary: Boolean,
    val template: String?,
    val category: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    /** MessagingStyle messages, oldest first: "sender @time: text". */
    val messages: List<String>,
    val redacted: Boolean,
    /** The system's "contains an OTP" flag (Ranking.hasSensitiveContent); null if unreadable. */
    val sensitive: Boolean?,
    val code: String?,
) {
    fun describe(): String = buildString {
        append("${time(at)} $app ($packageName) ${if (update) "UPDATE" else "NEW"}")
        if (groupSummary) append(" SUMMARY")
        if (redacted) append(" REDACTED")
        append(" sensitive=${sensitive ?: "?"}")
        appendLine()
        appendLine("  key=$key template=${template?.substringAfterLast('$')} category=$category")
        title?.let { appendLine("  title: $it") }
        text?.let { appendLine("  text: $it") }
        bigText?.takeIf { it != text }?.let { appendLine("  bigText: $it") }
        messages.forEach { appendLine("  msg: $it") }
        code?.let { appendLine("  code: $it") }
    }
}

/** What happened besides notifications: listener binding, grants, clipboard writes. */
data class ProbeEvent(val at: Long, val text: String) {
    fun describe(): String = "${time(at)} $text"
}

/**
 * Record of everything the listener sees, for Phase 0. Kept in memory and appended to [file]
 * in app-private storage (`adb shell run-as com.hisbaan.notiq cat files/probe.txt`). It holds
 * codes: Phase 1 drops the file, and nothing here goes to logcat.
 */
class ProbeLog(private val file: File) {
    private val _entries = MutableStateFlow<List<ProbeEntry>>(emptyList())

    /** Newest first. */
    val entries: StateFlow<List<ProbeEntry>> = _entries

    private val _events = MutableStateFlow<List<ProbeEvent>>(emptyList())

    /** Newest first. */
    val events: StateFlow<List<ProbeEvent>> = _events

    fun add(entry: ProbeEntry) {
        _entries.update { (listOf(entry) + it).take(MAX) }
        append(entry.describe())
    }

    fun event(text: String) {
        val event = ProbeEvent(System.currentTimeMillis(), text)
        _events.update { (listOf(event) + it).take(MAX) }
        append(event.describe() + "\n")
    }

    fun clear() {
        _entries.value = emptyList()
        _events.value = emptyList()
        file.delete()
    }

    private fun append(text: String) {
        runCatching { file.appendText(text) }
    }

    fun export(): String = buildString {
        appendLine("== Events")
        _events.value.forEach { appendLine(it.describe()) }
        appendLine()
        appendLine("== Notifications")
        _entries.value.forEach { appendLine(it.describe()) }
    }

    private companion object {
        const val MAX = 300
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

fun time(millis: Long): String = TIME.format(Instant.ofEpochMilli(millis))
