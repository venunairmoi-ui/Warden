package com.venunair.wisma.license

import com.venunair.wisma.data.SettingsRepository
import com.venunair.wisma.data.UserPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Wisma's freemium model, decided 2026-09-16 (see the project memory
 * "warden-android-playstore-licensing" for the full discussion):
 *
 * - OCR (receipt/warranty auto-fill): free for a 30-day trial from first
 *   install, then requires [premiumUnlocked].
 * - Backup (Google Drive backup/restore): requires [premiumUnlocked] from
 *   day one -- deliberately NOT part of the trial. The trial clock is a
 *   local timestamp, resettable by clearing app data or reinstalling; if
 *   backup were trial-accessible, a user could back up during one trial
 *   window, reset the clock, then restore -- getting backup for free
 *   indefinitely by looping the trial. Excluding it entirely closes that
 *   hole. A reset only regains OCR access, which is low-stakes enough
 *   that no further anti-abuse hardening (Play Integrity, server-side
 *   trial state) was judged worth the complexity for v1.
 * - Everything else in the app is free forever, ungated.
 *
 * [premiumUnlocked] is a single one-time non-consumable Play Billing
 * purchase ("Premium unlock") that permanently unlocks both OCR and
 * Backup together -- not a subscription.
 */
data class LicenseState(
    val installedAtMillis: Long,
    val premiumUnlocked: Boolean,
) {
    /** 0 when [installedAtMillis] is unstamped (0L) -- a brand-new process
     *  reads as "trial just started", not "trial expired", since a real
     *  negative/huge elapsed value can't occur from an unset timestamp. */
    val trialDaysElapsed: Long
        get() = if (installedAtMillis <= 0L) {
            0L
        } else {
            TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installedAtMillis)
                .coerceAtLeast(0L)
        }

    val trialDaysRemaining: Long
        get() = (TRIAL_LENGTH_DAYS - trialDaysElapsed).coerceAtLeast(0L)

    val isTrialActive: Boolean
        get() = trialDaysElapsed < TRIAL_LENGTH_DAYS

    val isOcrUnlocked: Boolean
        get() = premiumUnlocked || isTrialActive

    val isBackupUnlocked: Boolean
        get() = premiumUnlocked

    companion object {
        const val TRIAL_LENGTH_DAYS = 30L
    }
}

fun UserPreferences.toLicenseState(): LicenseState =
    LicenseState(installedAtMillis = installedAtMillis, premiumUnlocked = premiumUnlocked)

/** Reactive gating -- for UI that should update live if premiumUnlocked
 *  flips while the screen is open (e.g. SettingsScreen's Backup section). */
val SettingsRepository.licenseState: Flow<LicenseState>
    get() = preferences.map { it.toLicenseState() }

/** One-shot gating -- for a suspend call site (an OCR capture path, or a
 *  WorkManager run) that just needs the current answer, not a live
 *  collector. */
suspend fun SettingsRepository.currentLicenseState(): LicenseState =
    preferences.first().toLicenseState()
