package com.venunair.warden.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.venunair.warden.WardenApplication
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.computeAmcServiceStatus
import kotlinx.coroutines.flow.first
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

        val itemsWithRules = repository.getActiveItemsWithEnabledRules()

        itemsWithRules.forEach { (item, rules) ->
            val daysLeft = ChronoUnit.DAYS.between(today, item.expiryDate)
            rules.forEach { rule ->
                val due = daysLeft <= rule.daysBeforeExpiry
                val alreadyFired = rule.lastFiredDate != null
                val stillSnoozed = rule.snoozedUntil?.isAfter(today) == true
                if (due && !alreadyFired && !stillSnoozed) {
                    // Sprint 8: build smart suffix based on repair cost ratio.
                    // Feedback, 2026-08-26: also appends an AMC unused-visits
                    // warning here when applicable -- see buildAmcRemainingWarning.
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

        // AMC service-visit tracking, 2026-08-26: "notify the user when a
        // service is due... so they can take appropriate action" --
        // independent of the expiry-based ReminderRules above; this fires
        // off the computed next-expected-service date instead (see
        // computeAmcServiceStatus). Self-resetting: serviceDueNotifiedForDate
        // only suppresses a repeat for the SAME expected date, so logging a
        // new service or renewing the contract (both change what "next
        // expected" computes to) makes a fresh nudge eligible again with no
        // explicit clear step needed.
        itemsWithRules.map { it.first }
            .filter { it.category == ItemCategory.AMC }
            .forEach { item ->
                val events = repository.observeServiceHistory(item.id).first()
                val status = computeAmcServiceStatus(item, events)
                val nextExpected = status?.nextExpectedDate
                if (status != null && nextExpected != null) {
                    val due = !nextExpected.isAfter(today)
                    val alreadyNotified = item.serviceDueNotifiedForDate == nextExpected
                    if (due && status.remaining > 0 && !alreadyNotified) {
                        val posted = NotificationHelper.showServiceDueReminder(
                            applicationContext, item, status.remaining
                        )
                        if (posted) {
                            repository.markServiceDueNotified(item.id, nextExpected)
                        }
                    }
                }
            }

        return Result.success()
    }

    /**
     * If total service costs exceed 20% of the item's purchase price,
     * appends a repair-cost warning. AMC items with unused included
     * services also get a second line reminding them before expiry --
     * feedback, 2026-08-26: "inform them... one or two services remain,
     * so they can take appropriate action" before the contract lapses and
     * those visits are gone for good. Returns null if neither applies.
     */
    private suspend fun buildSmartSuffix(
        repository: com.venunair.warden.data.ItemRepository,
        item: Item
    ): String? {
        val lines = listOfNotNull(
            buildRepairWarning(repository, item),
            buildAmcRemainingWarning(repository, item)
        )
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private suspend fun buildRepairWarning(
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

    private suspend fun buildAmcRemainingWarning(
        repository: com.venunair.warden.data.ItemRepository,
        item: Item
    ): String? {
        if (item.category != ItemCategory.AMC) return null
        val events = repository.observeServiceHistory(item.id).first()
        val status = computeAmcServiceStatus(item, events) ?: return null
        if (status.remaining <= 0) return null
        return "You still have ${status.remaining} unused service${if (status.remaining != 1) "s" else ""} on this AMC"
    }
}
