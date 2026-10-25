package com.hisbaan.notiq.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.hisbaan.notiq.core.Message
import com.hisbaan.notiq.core.OtpExtractor
import com.hisbaan.notiq.diagnostics.TestNotification
import com.hisbaan.notiq.diagnostics.indent
import com.hisbaan.notiq.diagnostics.time

/** A notification as Notiq read it: what Diagnostics shows, and the [message] to look for a code in. */
data class ReadNotification(
    val key: String,
    val packageName: String,
    val app: String,
    /** Same key seen before (an update to a posted notification). */
    val update: Boolean,
    /** Notiq's own test code, which counts as a source whatever the settings say. */
    val test: Boolean,
    val template: String?,
    val category: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    /** MessagingStyle messages, oldest first: "sender @time: text". */
    val messages: List<String>,
    /** Android hid the content from Notiq (PLAN.md §1). */
    val redacted: Boolean,
    /** The system's "contains an OTP" flag (Ranking.hasSensitiveContent); null if unreadable. */
    val sensitive: Boolean?,
    /** What to look for a code in; null when redacted, or when the newest message is the user's own. */
    val message: Message?,
    val code: String?,
) {
    fun describe(): String = buildString {
        append("$app ($packageName) ${if (update) "UPDATE" else "NEW"}")
        if (redacted) append(" REDACTED")
        append(" sensitive=${sensitive ?: "?"}")
        appendLine()
        appendLine("  key=$key template=${template?.substringAfterLast('$')} category=$category")
        title?.let { appendLine("  title: ${it.indent()}") }
        text?.let { appendLine("  text: ${it.indent()}") }
        bigText?.takeIf { it != text }?.let { appendLine("  bigText: ${it.indent()}") }
        messages.forEach { appendLine("  msg: ${it.indent()}") }
        code?.let { appendLine("  code: $it") }
    }
}

object NotificationReader {
    /**
     * Notifications that never carry a code and would only add noise: ongoing ones, media players
     * (a paused player isn't ongoing and updates every second), group summaries (the child carries
     * the same text) and Notiq's own, except its test code.
     */
    fun skipped(context: Context, sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        return sbn.isOngoing ||
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0 ||
            notification.category == Notification.CATEGORY_TRANSPORT ||
            notification.extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("MediaStyle") == true ||
            (sbn.packageName == context.packageName && notification.channelId != TestNotification.CHANNEL)
    }

    fun read(context: Context, sbn: StatusBarNotification, update: Boolean, sensitive: Boolean?): ReadNotification {
        val notification = sbn.notification
        val extras = notification.extras
        val app = appLabel(context, sbn)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?.messages.orEmpty()
            .sortedBy { it.timestamp }
        val redactedText = redactedText()
        val redacted = text == redactedText || messages.any { it.text?.toString() == redactedText }

        val newest = messages.lastOrNull()
        val message = when {
            redacted -> null
            // A conversation carries earlier messages too; only the newest is new. No person
            // means the user sent it.
            newest != null -> newest.person?.let { person ->
                Message(sbn.key, sbn.packageName, app, person.name?.toString() ?: title, newest.text?.toString().orEmpty(), newest.timestamp)
            }
            else -> listOfNotNull(title, bigText ?: text).joinToString("\n").takeIf { it.isNotBlank() }?.let {
                Message(sbn.key, sbn.packageName, app, title, it, notification.`when`.takeIf { w -> w > 0 } ?: sbn.postTime)
            }
        }
        return ReadNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            app = app,
            update = update,
            test = sbn.packageName == context.packageName && notification.channelId == TestNotification.CHANNEL,
            template = extras.getString(Notification.EXTRA_TEMPLATE),
            category = notification.category,
            title = title,
            text = text,
            bigText = bigText,
            messages = messages.map { "${it.person?.name ?: "(me)"} @${time(it.timestamp)}: ${it.text}" },
            redacted = redacted,
            sensitive = sensitive,
            message = message,
            code = message?.text?.let(OtpExtractor::extract),
        )
    }

    /**
     * What Android puts in place of a notification's text when it hides sensitive content from an
     * untrusted listener. Read from the framework so it follows the system language.
     */
    @SuppressLint("DiscouragedApi")
    fun redactedText(): String {
        val system = Resources.getSystem()
        val id = system.getIdentifier("redacted_notification_message", "string", "android")
        return if (id != 0) system.getString(id) else "Sensitive notification content hidden"
    }

    /**
     * The posting app's name, from the ApplicationInfo the builder stores (no package visibility
     * needed). A redacted notification is rebuilt by the system and carries Android's own
     * ApplicationInfo, so it only counts when it's the posting package's.
     */
    private fun appLabel(context: Context, sbn: StatusBarNotification): String =
        sbn.notification.extras.getParcelable(EXTRA_BUILDER_APPLICATION_INFO, ApplicationInfo::class.java)
            ?.takeIf { it.packageName == sbn.packageName }
            ?.loadLabel(context.packageManager)?.toString()
            ?: sbn.packageName

    /** Notification.EXTRA_BUILDER_APPLICATION_INFO, hidden in the SDK. */
    private const val EXTRA_BUILDER_APPLICATION_INFO = "android.appInfo"
}
