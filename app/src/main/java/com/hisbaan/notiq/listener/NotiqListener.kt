package com.hisbaan.notiq.listener

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.probe.Clipboard
import com.hisbaan.notiq.probe.NotificationReader
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

    override fun onListenerConnected() {
        instance = this
        posted.clear()
        runCatching { activeNotifications.orEmpty() }.getOrDefault(emptyArray()).forEach { posted.add(it.key) }
        app.probe.event("Listener connected (${posted.size} notifications already posted)")
        app.refreshSetup()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        app.probe.event("Listener disconnected")
        app.refreshSetup()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        // Media players, navigation, downloads: never codes, and they update constantly.
        if (sbn.isOngoing) return
        val update = !posted.add(sbn.key)
        val ranking = Ranking().takeIf { rankingMap?.getRanking(sbn.key, it) == true }
        val entry = NotificationReader.read(this, sbn, update, ranking?.let(::sensitive))
        app.probe.add(entry)
        val code = entry.code ?: return
        app.scope.launch {
            if (!app.settings.current().copyCodes) return@launch
            val result = runCatching { Clipboard.copyCode(this@NotiqListener, code) }
            app.probe.event(result.fold({ "Copied code from ${entry.app}" }, { "Clipboard write failed: $it" }))
        }
    }

    /**
     * Ranking.hasSensitiveContent(): whether the system flagged this notification as containing
     * an OTP, i.e. whether there was anything to redact. A @SystemApi, so through reflection;
     * null if hidden-API restrictions block it.
     */
    private fun sensitive(ranking: Ranking): Boolean? =
        runCatching { Ranking::class.java.getMethod("hasSensitiveContent").invoke(ranking) as Boolean }
            .onFailure {
                if (!sensitiveUnreadableLogged) app.probe.event("Can't read hasSensitiveContent: $it")
                sensitiveUnreadableLogged = true
            }
            .getOrNull()

    private var sensitiveUnreadableLogged = false

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        posted.remove(sbn.key)
    }

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
                app.probe.event("Listener unbind requested")
                delay(1_000)
                requestRebind(component(app))
                app.probe.event("Listener rebind requested")
            }
        }
    }
}
