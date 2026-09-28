package com.mtracker

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val ctx = applicationContext
            val db = Database(ctx)
            val prefs = ctx.getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)

            Log.d(TAG, "doWork started")

            val storedLastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
            val lastCheck = if (storedLastCheck == 0L) {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            } else storedLastCheck

            val now = System.currentTimeMillis()
            val latestEntry = db.getLatestEntryTimestamp()
            Log.d(TAG, "lastCheck=$lastCheck latestEntry=$latestEntry")

            if (latestEntry <= lastCheck) {
                runCatching { NotificationHelper.showReminderNotification(ctx) }
                    .onFailure { Log.e(TAG, "showReminderNotification failed", it) }
            }

            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()

            Log.d(TAG, "doWork completed")
        } catch (e: Exception) {
            Log.e(TAG, "doWork crashed", e)
        } finally {
            if (!isStopped) {
                scheduleNext(applicationContext)
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ReminderWorker"
        private const val WORK_NAME = "reminder_work"
        private const val KEY_LAST_CHECK = "last_reminder_check"

        private val REMINDER_TIMES = listOf(
            12 to 0,
            15 to 0,
            18 to 0,
            23 to 30
        )

        fun schedule(context: Context) {
            enqueue(context, ExistingWorkPolicy.KEEP)
        }

        fun resetSchedule(context: Context) {
            enqueue(context, ExistingWorkPolicy.REPLACE)
        }

        private fun scheduleNext(context: Context) {
            enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }

        private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
            val now = Calendar.getInstance()
            var nextMillis = Long.MAX_VALUE

            for ((hour, minute) in REMINDER_TIMES) {
                val candidate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                if (candidate.timeInMillis <= now.timeInMillis) {
                    candidate.add(Calendar.DAY_OF_YEAR, 1)
                }
                if (candidate.timeInMillis < nextMillis) {
                    nextMillis = candidate.timeInMillis
                }
            }

            val delay = (nextMillis - now.timeInMillis).coerceAtLeast(0L)

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
