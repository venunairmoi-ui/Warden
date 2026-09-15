package com.venunair.wisma.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val UNIQUE_WORK_NAME = "reminder-daily-check"

    /**
     * KEEP (not REPLACE) so calling this on every app start — which
     * WardenApplication.onCreate() does — doesn't reset the schedule's
     * next-run time each time; it only registers the periodic job the
     * first time it's ever missing.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderCheckWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
