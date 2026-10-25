package com.hisbaan.notiq.probe

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.hisbaan.notiq.core.OtpExtractor

object NotificationReader {
    fun read(context: Context, sbn: StatusBarNotification, update: Boolean, sensitive: Boolean?): ProbeEntry {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val messages = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            ?.messages.orEmpty()
            .sortedBy { it.timestamp }
        val redactedText = redactedText()
        val redacted = text == redactedText || messages.any { it.text?.toString() == redactedText }
        // A conversation notification carries earlier messages too; only the newest is new.
        val newest = messages.lastOrNull()?.text?.toString() ?: bigText ?: text
        return ProbeEntry(
            at = System.currentTimeMillis(),
            packageName = sbn.packageName,
            app = appLabel(context, sbn),
            key = sbn.key,
            update = update,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            template = extras.getString(Notification.EXTRA_TEMPLATE),
            category = notification.category,
            title = title,
            text = text,
            bigText = bigText,
            messages = messages.map { "${it.person?.name ?: "(me)"} @${time(it.timestamp)}: ${it.text}" },
            redacted = redacted,
            sensitive = sensitive,
            code = newest?.takeUnless { redacted }?.let(OtpExtractor::extract),
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
