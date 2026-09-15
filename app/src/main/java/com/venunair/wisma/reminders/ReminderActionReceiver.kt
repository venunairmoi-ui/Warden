package com.venunair.wisma.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Handles the two notification action buttons. Deliberately does no direct
 * database work here — a BroadcastReceiver's onReceive() only has a short
 * guaranteed lifetime, so this just cancels the visible notification
 * (synchronous, no DB access needed) and hands the actual repository
 * mutation off to WorkManager as a one-off job, consistent with how the
 * rest of this sprint schedules background work rather than juggling
 * goAsync()/PendingResult lifecycle manually.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getLongExtra(NotificationHelper.EXTRA_ITEM_ID, -1L)
        val ruleId = intent.getLongExtra(NotificationHelper.EXTRA_RULE_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        if (itemId == -1L) return

        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }

        val data = Data.Builder()
            .putString(ReminderActionWorker.KEY_ACTION, intent.action.orEmpty())
            .putLong(ReminderActionWorker.KEY_ITEM_ID, itemId)
            .putLong(ReminderActionWorker.KEY_RULE_ID, ruleId)
            .build()

        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<ReminderActionWorker>().setInputData(data).build()
        )
    }
}
