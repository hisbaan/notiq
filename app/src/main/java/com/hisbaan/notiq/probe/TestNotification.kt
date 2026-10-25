package com.hisbaan.notiq.probe

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.hisbaan.notiq.R
import kotlin.random.Random

/**
 * A fake one-time-code message from Notiq itself, to test redaction and the clipboard without
 * waiting for a real SMS. Shaped like a messaging app's conversation notification.
 */
object TestNotification {
    private const val CHANNEL = "test"
    private const val ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Test codes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Fake one-time codes posted from Notiq's test button"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Returns the code posted, or null without notification permission. */
    fun post(context: Context): String? {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return null
        val code = Random.nextInt(100_000, 1_000_000).toString()
        val sender = Person.Builder().setName("Notiq Test").build()
        val text = "$code is your Notiq verification code. Don't share it with anyone."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setStyle(
                NotificationCompat.MessagingStyle(Person.Builder().setName("Me").build())
                    .addMessage(text, System.currentTimeMillis(), sender),
            )
            .setContentTitle("Notiq Test")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
        return code
    }
}
