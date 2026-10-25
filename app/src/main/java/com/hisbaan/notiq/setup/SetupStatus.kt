package com.hisbaan.notiq.setup

import android.Manifest
import android.app.AppOpsManager
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import com.hisbaan.notiq.listener.NotiqListener

/** What Notiq needs from the user, as far as the app can tell. */
data class SetupStatus(
    val listenerEnabled: Boolean = false,
    val listenerConnected: Boolean = false,
    val postNotifications: Boolean = false,
    val sensitive: Sensitive = Sensitive.UNKNOWN,
    val battery: Boolean = false,
    val companionAssociations: Int = 0,
) {
    /**
     * The RECEIVE_SENSITIVE_NOTIFICATIONS app-op. ALLOWED isn't the whole story: the system only
     * re-checks it when the listener binds, and a companion association also counts.
     */
    enum class Sensitive { ALLOWED, NOT_ALLOWED, UNKNOWN }

    /** Unknown counts as fine: that Android version doesn't hide codes from listeners. */
    val sensitiveOk: Boolean get() = sensitive != Sensitive.NOT_ALLOWED

    val complete: Boolean get() = listenerEnabled && sensitiveOk && postNotifications && battery

    companion object {
        fun check(context: Context): SetupStatus {
            fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            return SetupStatus(
                listenerEnabled = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
                listenerConnected = NotiqListener.connected,
                postNotifications = granted(Manifest.permission.POST_NOTIFICATIONS),
                sensitive = sensitive(context),
                battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
                companionAssociations = context.getSystemService(CompanionDeviceManager::class.java)?.myAssociations?.size ?: 0,
            )
        }

        private fun sensitive(context: Context): Sensitive {
            val mode = try {
                context.getSystemService(AppOpsManager::class.java)
                    .checkOpNoThrow(SENSITIVE_OPSTR, Process.myUid(), context.packageName)
            } catch (_: IllegalArgumentException) {
                // The op doesn't exist on this Android version.
                return Sensitive.UNKNOWN
            }
            // The op's default mode is MODE_IGNORED, so anything but ALLOWED is the untouched state.
            return if (mode == AppOpsManager.MODE_ALLOWED) Sensitive.ALLOWED else Sensitive.NOT_ALLOWED
        }

        /** AppOpsManager.OPSTR_RECEIVE_SENSITIVE_NOTIFICATIONS, not in the public SDK. */
        private const val SENSITIVE_OPSTR = "android:receive_sensitive_notifications"
    }
}
