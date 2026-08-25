package com.venunair.warden.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.venunair.warden.WardenApplication
import com.venunair.warden.data.Item
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Runs once a day (schedule set up in ReminderScheduler, registered from
 * WardenApplication). For every active item's enabled ReminderRules, fires
 * a notification the first time daysLeft drops to or below that rule's
 * offset — "at or past the threshold and not yet fired" rather than
 * "exactly on the threshold day", so a reminder can't be silently skipped
 * just because the worker didn't happen to run on the exact day (phone off,
 * Doze deferral, etc.). No dependency injection framework in this project —
 * CoroutineWorker gives applicationContext directly, so the repository is
 * reached the same manual way everything else in this app reaches it.
 *
 * Sprint 8: smart notifications — when repair/service costs exceed 20% of
 * purchase price, appends a warning to the notification body so the user
 * can make an informed renew-or-replace decision.
 */
class ReminderCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as WardenApplication).repository
        val today = LocalDate.now()

        repository.getActiveItemsWithEnabledRules().forEach { (item, rules) ->
            val daysLeft = ChronoUnit.DAYS.between(today, item.expiryDate)
            rules.forEach { rule ->
                val due = daysLeft <= rule.daysBeforeExpiry
                val alreadyFired = rule.lastFiredDate != null
                val stillSnoozed = rule.snoozedUntil?.isAfter(today) == true
                if (due && !alreadyFired && !stillSnoozed) {
                    // Sprint 8: build smart suffix based on repair cost ratio
                    val smartSuffix = buildSmartSuffix(repository, item)

                    val posted = NotificationHelper.showReminder(
                        applicationContext, item, rule, daysLeft, smartSuffix
                    )
                    if (posted) {
                        repository.markRuleFired(rule, today)
                    }
                }
            }
        }

        return Result.success()
    }

    /**
     * If total service costs exceed 20% of the item's purchase price,
     * return a warning line for the notification body. Otherwise null.
     */
    private suspend fun buildSmartSuffix(
        repository: com.venunair.warden.data.ItemRepository,
        item: Item
    ): String? {
        val purchasePrice = item.cost ?: return null
        if (purchasePrice <= 0) return null
        val serviceCost = repository.getServiceCostForItem(item.id)
        if (serviceCost <= 0) return null
        val ratio = serviceCost / purchasePrice
        if (ratio < 0.20) return null
        val pct = (ratio * 100).toInt()
        return "Repair costs are $pct% of purchase price — consider replacing"
    }
}
