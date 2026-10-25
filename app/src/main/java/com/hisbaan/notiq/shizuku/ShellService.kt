package com.hisbaan.notiq.shizuku

import android.os.Bundle
import kotlin.system.exitProcess

/**
 * Shizuku user service: Shizuku starts this in its own process, as the shell user, so commands
 * run here can do what `adb shell` can. Runs outside Notiq's app process: no NotiqApp here.
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

    override fun destroy() {
        exitProcess(0)
    }

    companion object {
        const val EXIT = "exit"
        const val OUTPUT = "output"
    }
}
