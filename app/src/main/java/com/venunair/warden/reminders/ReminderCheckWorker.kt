package com.venunair.warden.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.venunair.warden.WardenApplication
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
                    // Only mark fired if a notification actually posted —
                    // otherwise (e.g. POST_NOTIFICATIONS denied) this rule
                    // would get silently, permanently marked "shown" despite
                    // the user never having seen anything. That's exactly
                    // what happened during Sprint 5 testing: the bug this
                    // guard fixes.
                    val posted = NotificationHelper.showReminder(applicationContext, item, rule, daysLeft)
                    if (posted) {
                        repository.markRuleFired(rule, today)
                    }
                }
            }
        }

        return Result.success()
    }
}
