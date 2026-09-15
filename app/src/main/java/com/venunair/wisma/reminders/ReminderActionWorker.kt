package com.venunair.wisma.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.venunair.wisma.WardenApplication
import java.time.LocalDate

/** The actual DB mutation behind a notification action tap — see ReminderActionReceiver. */
class ReminderActionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as WardenApplication).repository
        val itemId = inputData.getLong(KEY_ITEM_ID, -1L)
        val ruleId = inputData.getLong(KEY_RULE_ID, -1L)
        if (itemId == -1L) return Result.failure()

        when (inputData.getString(KEY_ACTION)) {
            NotificationHelper.ACTION_MARK_SERVICED -> {
                val item = repository.getItem(itemId) ?: return Result.failure()
                // Default heuristic: push expiry forward a year from its
                // CURRENT date — the common AMC/subscription annual-renewal
                // case. Not always right for a one-off warranty repair;
                // there's no UI surface inside a notification action to ask,
                // so the fix is opening the item afterward and correcting
                // the date if this guess is wrong, rather than building a
                // full date picker into a notification for v1.
                repository.markServiced(itemId, newExpiry = item.expiryDate.plusYears(1))
            }
            NotificationHelper.ACTION_SNOOZE -> {
                if (ruleId != -1L) {
                    repository.snoozeRule(ruleId, until = LocalDate.now().plusDays(7))
                }
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_RULE_ID = "rule_id"
    }
}
