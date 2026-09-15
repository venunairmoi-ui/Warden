package com.venunair.wisma

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.venunair.wisma.autodetect.AutoDetectWorker
import com.venunair.wisma.backup.DriveBackupManager
import com.venunair.wisma.data.DigestFrequency
import com.venunair.wisma.data.ItemRepository
import com.venunair.wisma.data.SettingsRepository
import com.venunair.wisma.data.WardenDatabase
import com.venunair.wisma.reminders.DigestNotificationWorker
import com.venunair.wisma.reminders.NotificationHelper
import com.venunair.wisma.reminders.ReminderScheduler
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

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

    // Sprint 9: settings persistence (DataStore-backed).
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    // Application has no built-in coroutine scope the way a ViewModel or a
    // lifecycle-aware component does. SupervisorJob so one collector
    // failing (shouldn't happen — DataStore's Flow doesn't throw for a
    // missing/default value) can't cancel the whole scope; this lives for
    // the entire process, same as `database`/`repository` above.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Password-protected PDF support: PdfBox-Android needs its resource
        // loader initialized once, before any PDDocument.load call, or it
        // can't find its bundled font/glyph assets -- see PdfDecryptor.kt,
        // the only caller. Cheap, safe to run unconditionally every cold
        // start even on the (large majority of) sessions that never touch
        // an encrypted PDF.
        PDFBoxResourceLoader.init(this)
        // Phase 2: MUST run before `database` (below) or anything that
        // touches it is ever accessed -- see DriveBackupManager's
        // applyPendingRestoreIfAny doc comment for why a restored backup
        // is staged, not applied live, and finished off here at the one
        // moment it's safe to swap the underlying database file: before
        // Room has opened anything. A no-op fast file-existence check on
        // every other cold start where no restore is pending.
        DriveBackupManager.applyPendingRestoreIfAny(this)
        // Both are safe/cheap to call on every process start: creating an
        // already-existing NotificationChannel is a no-op, and
        // ReminderScheduler.schedule uses KEEP so it won't reset an
        // already-registered periodic job's timing.
        NotificationHelper.ensureChannel(this)
        ReminderScheduler.schedule(this)
        observeSettingsAndReschedule()
    }

    /**
     * Sprint 9: digest frequency and the auto-detect toggle are both
     * user-configurable in Settings — this collector is the one place that
     * reacts to either changing and updates WorkManager's schedule to
     * match, for as long as the process lives. distinctUntilChanged on
     * each derived value (not the whole UserPreferences object) so an
     * unrelated setting change (e.g. reminder defaults) doesn't
     * re-enqueue work that hasn't actually changed.
     */
    private fun observeSettingsAndReschedule() {
        applicationScope.launch {
            settingsRepository.preferences
                .map { it.digestFrequency }
                .distinctUntilChanged()
                .collect { frequency -> applyDigestSchedule(frequency) }
        }
        applicationScope.launch {
            settingsRepository.preferences
                .map { it.autoDetectEnabled }
                .distinctUntilChanged()
                .collect { enabled -> applyAutoDetectSchedule(enabled) }
        }
    }

    /**
     * Sprint 8 originally scheduled this unconditionally, weekly, with
     * KEEP. Sprint 9 makes it settings-driven: OFF cancels the periodic
     * work entirely, WEEKLY/MONTHLY (re)enqueues with REPLACE so an actual
     * frequency change takes effect immediately rather than waiting for
     * the previous interval to lapse.
     */
    private fun applyDigestSchedule(frequency: DigestFrequency) {
        val workManager = WorkManager.getInstance(this)
        if (frequency == DigestFrequency.OFF) {
            workManager.cancelUniqueWork(DIGEST_WORK_NAME)
            return
        }
        val intervalDays = when (frequency) {
            DigestFrequency.WEEKLY -> 7L
            DigestFrequency.MONTHLY -> 30L
            DigestFrequency.OFF -> return // unreachable, handled above
        }
        val digestWork = PeriodicWorkRequestBuilder<DigestNotificationWorker>(
            intervalDays, TimeUnit.DAYS
        ).build()
        workManager.enqueueUniquePeriodicWork(
            DIGEST_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            digestWork
        )
    }

    /**
     * Auto-detect is opt-in and off by default (Settings toggle) — this
     * only ever enqueues the periodic scan once the user turns it on, and
     * cancels it the moment they turn it off. Runtime READ_MEDIA_IMAGES
     * permission is requested from the Settings screen itself, before the
     * toggle can be flipped on; AutoDetectWorker also no-ops defensively
     * if that permission is somehow missing when it runs.
     */
    private fun applyAutoDetectSchedule(enabled: Boolean) {
        val workManager = WorkManager.getInstance(this)
        if (!enabled) {
            workManager.cancelUniqueWork(AUTO_DETECT_WORK_NAME)
            return
        }
        val autoDetectWork = PeriodicWorkRequestBuilder<AutoDetectWorker>(
            AUTO_DETECT_INTERVAL_HOURS, TimeUnit.HOURS
        ).build()
        workManager.enqueueUniquePeriodicWork(
            AUTO_DETECT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            autoDetectWork
        )
    }

    companion object {
        private const val DIGEST_WORK_NAME = "warden_digest"
        private const val AUTO_DETECT_WORK_NAME = "warden_auto_detect"
        // Battery-friendly cadence for an opt-in "quietly notice new
        // photos" scan — not time-critical the way expiry reminders are.
        // WorkManager's own minimum periodic interval is 15 minutes; 6
        // hours is a deliberate choice, not a platform constraint.
        private const val AUTO_DETECT_INTERVAL_HOURS = 6L
    }
}
