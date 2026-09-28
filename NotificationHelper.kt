package com.mtracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {
    private const val CHANNEL_SUMMARY_ID = "daily_summary_channel"
    private const val CHANNEL_SUMMARY_NAME = "Daily Summaries"

    private const val CHANNEL_REMINDER_ID = "reminder_channel"
    private const val CHANNEL_REMINDER_NAME = "Usage Reminders"

    private const val SUMMARY_NOTIFICATION_ID = 1001
    private const val REMINDER_NOTIFICATION_ID = 1002

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SUMMARY_ID,
                    CHANNEL_SUMMARY_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Morning summary notifications" }
            )

            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REMINDER_ID,
                    CHANNEL_REMINDER_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Reminders to log something" }
            )
        }
    }

    fun showSummaryNotification(context: Context, summaryText: String, date: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_summary_date", date)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Daily Summary — $date")
            .setContentText(summaryText.take(100) + if (summaryText.length > 100) "..." else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    fun showReminderNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("MTracker Reminder")
            .setContentText("Nothing has been logged in the previous timeframe.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Nothing has been logged in the previous timeframe.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(REMINDER_NOTIFICATION_ID, notification)
    }
}
