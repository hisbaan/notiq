package com.hisbaan.notiq.shizuku

import android.content.ClipDescription
import android.os.Bundle
import android.os.IBinder
import kotlin.system.exitProcess

/**
 * Shizuku user service: Shizuku starts this in its own process, as the shell user, so code here
 * can do what `adb shell` can. Runs outside Notiq's app process: no NotiqApp here.
 */
class ShellService : IShellService.Stub() {
    override fun run(command: Array<String>): Bundle {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return Bundle().apply {
            putInt(EXIT, process.waitFor())
            putString(OUTPUT, output.trim())
        }
    }

    /**
     * The shell holds READ_CLIPBOARD_IN_BACKGROUND, which apps can't get, and there's no `cmd
     * clipboard`, so this calls the hidden IClipboard service directly.
     */
    override fun clipDescriptionExtra(userId: Int, key: String): String? {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "clipboard") as IBinder
        val clipboard = Class.forName("android.content.IClipboard\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
        val method = clipboard.javaClass.methods.first { it.name == "getPrimaryClipDescription" }
        // (callingPackage, attributionTag, userId) on Android 13; deviceId was added after.
        val args: Array<Any?> = when (method.parameterCount) {
            3 -> arrayOf(SHELL_PACKAGE, null, userId)
            else -> arrayOf(SHELL_PACKAGE, null, userId, DEVICE_ID_DEFAULT)
        }
        val description = method.invoke(clipboard, *args) as ClipDescription?
        return description?.extras?.getString(key)
    }

    override fun destroy() {
        exitProcess(0)
    }

    companion object {
        const val EXIT = "exit"
        const val OUTPUT = "output"

        /** The clipboard service checks the calling package belongs to the caller's uid. */
        private const val SHELL_PACKAGE = "com.android.shell"

        /** Context.DEVICE_ID_DEFAULT: the phone itself, not a virtual device. */
        private const val DEVICE_ID_DEFAULT = 0
    }
}
