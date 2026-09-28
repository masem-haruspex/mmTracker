package com.mtracker

import android.app.Application
import android.content.Context
import android.util.Log

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        NotificationHelper.createChannel(this)

        val prefs = getSharedPreferences("tracker_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_WM_CHAINS_RESET, false)) {
            Log.d(TAG, "Running WorkManager chain migration")
            runCatching {
                ReminderWorker.resetSchedule(this)
                DailySummaryWorker.resetSchedule(this)
            }.onFailure { Log.e(TAG, "WorkManager migration failed", it) }
            prefs.edit().putBoolean(KEY_WM_CHAINS_RESET, true).apply()
        } else {
            runCatching {
                ReminderWorker.schedule(this)
                DailySummaryWorker.schedule(this)
            }.onFailure { Log.e(TAG, "WorkManager schedule failed", it) }
        }

        runCatching { OcrEngine.init(this) }
            .onFailure { Log.e(TAG, "OcrEngine.init failed", it) }
        runCatching { Vosk.init(this) }
            .onFailure { Log.e(TAG, "Vosk.init failed", it) }
    }

    companion object {
        private const val TAG = "App"
        private const val KEY_WM_CHAINS_RESET = "wm_chains_reset_v1"

        lateinit var instance: App
            private set
    }
}
