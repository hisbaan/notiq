package com.hisbaan.notiq.actions

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.hisbaan.notiq.MainActivity
import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.R
import com.hisbaan.notiq.core.Code

/** Notiq's own notification for a code: the code itself, where it came from, and Copy. */
object CodeNotification {
    private const val CHANNEL = "codes"
    private const val ID = 100

    /** Gone after this; the code has usually expired by then. */
    private const val TIMEOUT_MS = 10 * 60_000L

    fun createChannel(context: Context) {
        // Low: silent. The message that carried the code already alerted.
        val channel = NotificationChannel(CHANNEL, "One-time codes", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Each code Notiq finds, with a Copy button"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun post(context: Context, code: Code, copied: Boolean) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val from = listOfNotNull(code.message.app, code.message.sender?.takeIf { it != code.message.app }).joinToString(" · ")
        val copy = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CopyCodeReceiver::class.java).putExtra(CopyCodeReceiver.EXTRA_CODE, code.code),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // The lock screen shows this instead, so the code isn't readable there.
        val public = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("One-time code from ${code.message.app}")
            .build()
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(code.code)
            .setContentText(if (copied) "Copied · $from" else from)
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Copy", copy).build())
            .setTimeoutAfter(TIMEOUT_MS)
            .setShowWhen(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID, notification)
    }
}

/** The Copy button on [CodeNotification]. Not exported: only Notiq's own PendingIntent reaches it. */
class CopyCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NotiqApp
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        app.clipboard.copyCode(code, app.settings.settings.value?.clearAfterSeconds ?: 0)
    }

    companion object {
        const val EXTRA_CODE = "code"
    }
}
