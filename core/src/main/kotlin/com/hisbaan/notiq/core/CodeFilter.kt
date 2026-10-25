package com.hisbaan.notiq.core

/**
 * Decides whether a code is new enough to act on. Notifications get re-posted (a conversation
 * updates for every message, marking as read re-posts, the listener rebinds and rescans the
 * shade), and the same code can arrive twice (SMS and email). Not thread safe: call from one
 * thread.
 */
class CodeFilter(
    /** The same code within this window is acted on once. */
    private val dedupeWindowMs: Long = 2 * 60_000L,
    /** Older messages are ignored: re-posts of old conversations, or a rescan after a rebind. */
    private val maxAgeMs: Long = 10 * 60_000L,
) {
    /** Per notification key, the time of the newest message acted on. */
    private val newestByKey = HashMap<String, Long>()

    /** Codes acted on, oldest first, with when. */
    private val recent = ArrayDeque<Pair<String, Long>>()

    fun accept(message: Message, code: String, now: Long): Boolean {
        prune(now)
        if (now - message.time > maxAgeMs) return false
        val newest = newestByKey[message.key]
        if (newest != null && message.time <= newest) return false
        newestByKey[message.key] = message.time
        if (recent.any { it.first == code }) return false
        recent.addLast(code to now)
        return true
    }

    private fun prune(now: Long) {
        while (recent.isNotEmpty() && now - recent.first().second > dedupeWindowMs) recent.removeFirst()
        newestByKey.values.removeAll { now - it > maxAgeMs }
    }
}
