package com.hisbaan.notiq.actions

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import com.hisbaan.notiq.NotiqApp
import com.hisbaan.notiq.shizuku.ShizukuShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Copies codes, and clears them later only if the clipboard still holds that copy. Android lets a
 * background app write and clear the clipboard but not read it, so the check goes through
 * Shizuku: each copy carries a random tag in its description, and the shell reads the
 * description back. Without Shizuku the clipboard is left alone.
 */
class Clipboard(private val app: NotiqApp) {
    private val manager = app.getSystemService(ClipboardManager::class.java)
    private val keyguard = app.getSystemService(KeyguardManager::class.java)
    private var clear: Job? = null

    /** Marked sensitive, so the system's clipboard preview and keyboards hide it. */
    fun copyCode(code: String, clearAfterSeconds: Int) {
        val tag = UUID.randomUUID().toString()
        val clip = ClipData.newPlainText("One-time code", code)
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            putString(EXTRA_TAG, tag)
        }
        manager.setPrimaryClip(clip)
        clear?.cancel()
        if (clearAfterSeconds > 0) {
            clear = app.scope.launch {
                delay(clearAfterSeconds * 1_000L)
                clearIfStill(tag)
            }
        }
    }

    fun copyText(label: String, text: String) {
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    private suspend fun clearIfStill(tag: String) {
        val giveUpAt = System.currentTimeMillis() + LOCKED_RETRY_MS
        while (true) {
            if (app.shizuku.state.value != ShizukuShell.State.READY) {
                app.diagnostics.event("Clipboard not cleared: Shizuku isn't running, so Notiq can't check it still holds the code")
                return
            }
            val current = try {
                app.shizuku.clipDescriptionExtra(EXTRA_TAG)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                app.diagnostics.event("Clipboard not cleared: couldn't read it through Shizuku: $e")
                return
            }
            when {
                current == tag -> {
                    manager.clearPrimaryClip()
                    app.diagnostics.event("Clipboard cleared")
                    return
                }
                // Locked: the system hides the clipboard from everyone. Try again once unlocked.
                current == null && keyguard.isDeviceLocked && System.currentTimeMillis() < giveUpAt -> delay(LOCKED_POLL_MS)
                else -> {
                    app.diagnostics.event("Clipboard not cleared: something else was copied since")
                    return
                }
            }
        }
    }

    private companion object {
        const val EXTRA_TAG = "com.hisbaan.notiq.COPY_TAG"
        const val LOCKED_POLL_MS = 15_000L
        const val LOCKED_RETRY_MS = 10 * 60_000L
    }
}
