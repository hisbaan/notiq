package com.hisbaan.notiq.listener

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.notification.NotificationReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Notiq's notification listener. The class name is permanent: the user's notification-access
 * grant is tied to it, and so is the system's "trusted listener" check (PLAN.md §1), which runs
 * when this binds.
 */
class NotiqListener : NotificationListenerService() {
    private val app get() = application as NotiqApp

    /** Keys currently posted, to tell a new notification from an update to one. */
    private val posted = HashSet<String>()

    private var sensitiveUnreadableLogged = false

    override fun onListenerConnected() {
        instance = this
        posted.clear()
        active().forEach { posted.add(it.key) }
        app.diagnostics.event("Listener connected (${posted.size} notifications already posted)")
        app.refreshSetup()
        if (app.access.rescanPending) app.access.rescan(this)
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        app.diagnostics.event("Listener disconnected")
        app.refreshSetup()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        handle(sbn, rankingMap, update = !posted.add(sbn.key))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        posted.remove(sbn.key)
    }

    /**
     * Reads everything in the shade again, after access to sensitive notifications comes back.
     * The system checks trust when it hands these out, so they arrive in full now; the pipeline's
     * filter drops anything already acted on or too old.
     */
    fun rescan() {
        val notifications = active()
        val ranking = runCatching { currentRanking }.getOrNull()
        notifications.forEach { handle(it, ranking, update = true) }
        app.diagnostics.event("Rescanned ${notifications.size} notifications")
    }

    private fun handle(sbn: StatusBarNotification, rankingMap: RankingMap?, update: Boolean) {
        if (NotificationReader.skipped(this, sbn)) return
        val ranking = Ranking().takeIf { rankingMap?.getRanking(sbn.key, it) == true }
        val notification = NotificationReader.read(this, sbn, update, ranking?.let(::sensitive))
        app.diagnostics.add(notification)
        app.pipeline.onNotification(notification)
    }

    private fun active(): List<StatusBarNotification> =
        runCatching { activeNotifications.orEmpty().toList() }.getOrDefault(emptyList())

    /**
     * Ranking.hasSensitiveContent(): whether the system flagged this notification as containing
     * an OTP. A @SystemApi, so through reflection; null if hidden-API restrictions block it.
     */
    private fun sensitive(ranking: Ranking): Boolean? =
        runCatching { Ranking::class.java.getMethod("hasSensitiveContent").invoke(ranking) as Boolean }
            .onFailure {
                if (!sensitiveUnreadableLogged) app.diagnostics.event("Can't read hasSensitiveContent: $it")
                sensitiveUnreadableLogged = true
            }
            .getOrNull()

    companion object {
        /** The bound listener, while the system has it connected. */
        @Volatile
        var instance: NotiqListener? = null
            private set

        val connected: Boolean get() = instance != null

        fun component(context: Context) = ComponentName(context, NotiqListener::class.java)

        /**
         * Unbind and bind again, so the system re-runs its trusted-listener check (after an
         * app-op change or a companion association).
         */
        fun rebind(app: NotiqApp) {
            app.scope.launch {
                instance?.requestUnbind()
                app.diagnostics.event("Listener unbind requested")
                delay(1_000)
                requestRebind(component(app))
                app.diagnostics.event("Listener rebind requested")
            }
        }
    }
}
