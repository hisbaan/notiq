package com.hisbaan.notiq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hisbaan.notiq.diagnostics.TestNotification
import kotlinx.coroutines.launch

/**
 * Drives Notiq from adb, for testing without the UI. Results go to the diagnostics log
 * (`adb shell run-as com.hisbaan.notiq cat files/diagnostics.txt`).
 *
 *   adb shell am broadcast -n com.hisbaan.notiq/.DebugReceiver --es action copy --es code 482913 --ei clear 10
 *       Copies a code as the pipeline would, clearing after `clear` seconds (0: never).
 *   adb shell am broadcast -n com.hisbaan.notiq/.DebugReceiver --es action text --es text hello
 *       Copies plain text, standing in for the user copying something else.
 *   adb shell am broadcast -n com.hisbaan.notiq/.DebugReceiver --es action test
 *       Posts the test code notification, which goes through the whole pipeline.
 *   adb shell am broadcast -n com.hisbaan.notiq/.DebugReceiver --es action email \
 *           --es sender GitHub --es subject "[GitHub] Please verify your device" --es body "Verification code: 482913"
 *       Posts a Gmail-shaped test notification (test channel, so it counts as a source).
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as NotiqApp
        when (val action = intent.getStringExtra("action")) {
            "copy" -> {
                val code = intent.getStringExtra("code") ?: "482913"
                app.clipboard.copyCode(code, intent.getIntExtra("clear", 0))
                app.diagnostics.event("Debug: copied $code")
            }
            "text" -> {
                app.clipboard.copyText("Debug", intent.getStringExtra("text") ?: "hello")
                app.diagnostics.event("Debug: copied text")
            }
            "email" -> {
                val posted = TestNotification.postEmail(
                    app,
                    intent.getStringExtra("sender") ?: "Notiq Test",
                    intent.getStringExtra("subject") ?: "Your verification code",
                    intent.getStringExtra("body") ?: "Your verification code is 482913.",
                )
                app.diagnostics.event("Debug: ${if (posted) "posted" else "couldn't post"} test email")
            }
            "test" -> app.scope.launch {
                app.diagnostics.event("Debug: posted test code ${TestNotification.post(app)}")
            }
            else -> app.diagnostics.event("Debug: unknown action $action")
        }
    }
}
