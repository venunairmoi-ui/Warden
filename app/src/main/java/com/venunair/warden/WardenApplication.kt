package com.venunair.warden

import android.app.Application
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.WardenDatabase
import com.venunair.warden.reminders.NotificationHelper
import com.venunair.warden.reminders.ReminderScheduler

/**
 * Deliberately not using Hilt/Dagger — a single Application-level manual DI
 * container keeps a solo, nights-and-weekends build approachable. Revisit
 * only if the dependency graph grows enough to justify the tooling (see
 * README "Design decisions worth knowing").
 */
class WardenApplication : Application() {
    val database: WardenDatabase by lazy { WardenDatabase.getInstance(this) }
    val repository: ItemRepository by lazy {
        ItemRepository(
            itemDao = database.itemDao(),
            attachmentDao = database.attachmentDao(),
            reminderRuleDao = database.reminderRuleDao(),
            serviceEventDao = database.serviceEventDao()
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Both are safe/cheap to call on every process start: creating an
        // already-existing NotificationChannel is a no-op, and
        // ReminderScheduler.schedule uses KEEP so it won't reset an
        // already-registered periodic job's timing.
        NotificationHelper.ensureChannel(this)
        ReminderScheduler.schedule(this)
    }
}
