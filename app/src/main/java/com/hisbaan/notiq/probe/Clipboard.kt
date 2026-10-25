package com.hisbaan.notiq.probe

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

object Clipboard {
    /** Marked sensitive, so the system's clipboard preview and keyboards hide it. */
    fun copyCode(context: Context, code: String) {
        val clip = ClipData.newPlainText("One-time code", code)
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    fun copyText(context: Context, label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
