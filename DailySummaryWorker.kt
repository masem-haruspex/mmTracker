package com.mtracker

import android.content.Context
import androidx.work.*
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DailySummaryWorker(
	context: Context,
	params: WorkerParameters
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		try {
			val ctx = applicationContext
			val db = Database(ctx)

			Log.d(TAG, "doWork started")

			val cal = Calendar.getInstance()
			cal.add(Calendar.DAY_OF_YEAR, -1)
			cal.set(Calendar.HOUR_OF_DAY, 0)
			cal.set(Calendar.MINUTE, 0)
			cal.set(Calendar.SECOND, 0)
			cal.set(Calendar.MILLISECOND, 0)
			val yesterdayStart = cal.timeInMillis
			cal.add(Calendar.DAY_OF_YEAR, 1)
			val yesterdayEnd = cal.timeInMillis - 1

			val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
			val yesterdayDateStr = dateFormat.format(Date(yesterdayStart))
			Log.d(TAG, "target date=$yesterdayDateStr")

			val existing = db.getDailySummaryForDate(yesterdayDateStr)
			if (existing != null) {
				Log.d(TAG, "summary already exists, skipping")
			} else {
				val allEntries = mutableListOf<Entry>()
				Type.values().forEach { type ->
					db.getEntriesSince(type, yesterdayStart).forEach { e ->
						if (e.timestamp in yesterdayStart..yesterdayEnd) allEntries.add(e)
					}
				}
				Log.d(TAG, "collected ${allEntries.size} entries")

				val summaryText = if (allEntries.isEmpty()) {
					"No entries found for yesterday."
				} else {
					try {
						AI.generateSummary(ctx, allEntries)
					} catch (e: Exception) {
						Log.e(TAG, "summary generation failed", e)
						"Failed to generate summary: ${e.message}"
					}
				}

				db.insertDailySummary(
					DailySummary(
						date = yesterdayDateStr,
						summaryText = summaryText,
						viewed = false
					)
				)

				runCatching {
					NotificationHelper.showSummaryNotification(ctx, summaryText, yesterdayDateStr)
				}.onFailure { Log.e(TAG, "showSummaryNotification failed", it) }
			}

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
		private const val TAG = "DailySummaryWorker"
		private const val WORK_NAME = "daily_summary_work"

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
			val target = Calendar.getInstance().apply {
				set(Calendar.HOUR_OF_DAY, 7)
				set(Calendar.MINUTE, 0)
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}
			if (target.before(now)) {
				target.add(Calendar.DAY_OF_YEAR, 1)
			}

			val delay = target.timeInMillis - now.timeInMillis
			val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
			.setInitialDelay(delay, TimeUnit.MILLISECONDS)
			.build()

			WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
		}
	}
}
