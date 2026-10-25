package com.hisbaan.notiq.core

/** Which apps' notifications are read for codes. */
data class SourcePolicy(
    val messagingApps: Boolean = true,
    val emailApps: Boolean = false,
    /** Any app, when Android itself flagged the notification as containing a code. */
    val systemFlagged: Boolean = true,
    /** Any app at all; only the extractor decides. */
    val allApps: Boolean = false,
) {
    /** [systemFlagged] is null when the system's flag couldn't be read. */
    fun accepts(packageName: String, systemFlagged: Boolean?): Boolean =
        allApps ||
            (messagingApps && packageName in MESSAGING_APPS) ||
            (emailApps && packageName in EMAIL_APPS) ||
            (this.systemFlagged && systemFlagged == true)

    companion object {
        val MESSAGING_APPS = setOf(
            "com.google.android.apps.messaging", // Google Messages
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.google.android.apps.googlevoice",
            "com.microsoft.android.smsorganizer",
            "com.textra",
            "org.fossify.messages",
            "com.simplemobiletools.smsmessenger",
            "org.thoughtcrime.securesms", // Signal
            "com.whatsapp",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
        )

        val EMAIL_APPS = setOf(
            "com.google.android.gm", // Gmail
            "com.microsoft.office.outlook",
            "ch.protonmail.android",
            "com.fastmail.app",
            "com.fsck.k9",
            "net.thunderbird.android",
            "eu.faircode.email", // FairEmail
        )
    }
}
