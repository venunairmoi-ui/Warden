package com.venunair.wisma.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.venunair.wisma.WardenApplication
import com.venunair.wisma.data.isRecurringPayment
import com.venunair.wisma.ui.common.toCurrencyString
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Sprint 8: Weekly digest notification — fires once a week (Monday 9 AM,
 * scheduled by WardenApplication via WorkManager PeriodicWorkRequest).
 *
 * Summarises:
 * - How many items are expiring within the next 30 days, and their total cost
 * - How many active subscriptions exist
 *
 * Uses the same manual-DI pattern as ReminderCheckWorker (no Hilt).
 */
class DigestNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val application = applicationContext as WardenApplication
        val repository = application.repository
        val today = LocalDate.now()

        val allItems = repository.observeActiveItems().first()

        val expiringItems = allItems.filter {
            val d = ChronoUnit.DAYS.between(today, it.expiryDate)
            d in 0..30
        }
        val subscriptions = allItems.filter { it.isRecurringPayment }

        val totalAtRisk = expiringItems.mapNotNull { it.cost }.sum()
        // Background worker, no composition to read LocalRegion from --
        // one-shot suspend read of the same DataStore every other setting
        // comes from, same pattern as WardenNavHost's collectAsState but
        // without needing a live subscription for a single digest run.
        val region = application.settingsRepository.preferences.first().region
        val totalAtRiskFormatted = totalAtRisk.toCurrencyString(region)

        NotificationHelper.showDigest(
            context = applicationContext,
            expiringCount = expiringItems.size,
            subscriptionCount = subscriptions.size,
            totalAtRisk = totalAtRiskFormatted
        )

        return Result.success()
    }
}
