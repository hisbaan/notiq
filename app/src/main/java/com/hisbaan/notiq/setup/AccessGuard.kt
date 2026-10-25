package com.hisbaan.notiq.setup

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.hisbaan.notiq.MainActivity
import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.R
import com.hisbaan.notiq.listener.NotiqListener
import com.hisbaan.notiq.notification.ReadNotification
import com.hisbaan.notiq.shizuku.ShizukuShell
import kotlinx.coroutines.launch

/**
 * The redaction alarm. A redacted notification from a source Notiq reads means the sensitive
 * notification grant is gone (uninstall, OS update, revoked). With Shizuku, grant it again and
 * rescan the shade: the code is still there, and the listener now gets it in full. Without,
 * tell the user.
 */
class AccessGuard(private val app: NotiqApp) {
    private var lastRegrant = 0L

    /** Set while a re-grant is running; the listener rescans when it (re)connects. */
    var rescanPending = false
        private set

    fun onRedacted(notification: ReadNotification) {
        app.diagnostics.event("${notification.app} notification redacted: sensitive notification access is gone")
        val now = System.currentTimeMillis()
        if (app.shizuku.state.value != ShizukuShell.State.READY || now - lastRegrant < REGRANT_INTERVAL_MS) {
            postAlert()
            return
        }
        lastRegrant = now
        rescanPending = true
        app.scope.launch {
            app.grants.setUp()
            // Enabling an already-enabled listener doesn't rebind it, so rescan here. If it did
            // rebind, onListenerConnected rescans instead.
            NotiqListener.instance?.let { rescan(it) }
        }
    }

    fun rescan(listener: NotiqListener) {
        rescanPending = false
        listener.rescan()
    }

    fun cancelAlert() {
        app.getSystemService(NotificationManager::class.java).cancel(ALERT_ID)
    }

    private fun postAlert() {
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            app,
            0,
            Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Notiq can't read one-time codes")
            .setContentText("Android hid a code from Notiq. Tap to grant access again.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        app.getSystemService(NotificationManager::class.java).notify(ALERT_ID, notification)
    }

    companion object {
        private const val CHANNEL = "access"
        private const val ALERT_ID = 200
        private const val REGRANT_INTERVAL_MS = 10 * 60_000L

        fun createChannel(context: Context) {
            val channel = NotificationChannel(CHANNEL, "Access problems", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When Android stops letting Notiq read codes"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
