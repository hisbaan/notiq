package com.hisbaan.notiq.core

/** One incoming message, whatever it came from (a notification today; maybe IMAP later). */
data class Message(
    /** Stable for updates of the same notification or conversation. */
    val key: String,
    val packageName: String,
    /** The app's name, for display. */
    val app: String,
    /** Who it's from, when the source says (a contact, a number, an email sender). */
    val sender: String?,
    val text: String,
    /** Epoch millis the message was received (or posted, when the source has nothing better). */
    val time: Long,
)

/** A code found in a [Message]. */
data class Code(
    val code: String,
    val message: Message,
    /** Epoch millis Notiq acted on it. */
    val at: Long,
)
