package com.venunair.wisma.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Top-level, not a class member: preferencesDataStore's Kotlin property
// delegate must be a singleton per file/process (multiple DataStore
// instances pointed at the same file corrupts it) — the standard pattern
// is a top-level extension property scoped to the whole app process,
// exactly like Room's WardenDatabase.getInstance() singleton below it.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "warden_settings")

/**
 * Sprint 9: reads/writes every user-configurable setting. Manual-DI
 * pattern (constructed once in WardenApplication), same as ItemRepository.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val REMINDER_OFFSETS = stringSetPreferencesKey("default_reminder_offsets")
        val DIGEST_FREQUENCY = stringPreferencesKey("digest_frequency")
        val AUTO_DETECT_ENABLED = booleanPreferencesKey("auto_detect_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REGION = stringPreferencesKey("region")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at_millis")
        val LAST_AUTO_DETECT_SCAN_AT = longPreferencesKey("last_auto_detect_scan_at_millis")
        val AUTO_DETECT_SUGGESTED_FINGERPRINTS = stringSetPreferencesKey("auto_detect_suggested_fingerprints")
        val PENDING_AUTO_DETECT_SUGGESTIONS = stringSetPreferencesKey("pending_auto_detect_suggestions")
        val INSTALLED_AT = longPreferencesKey("installed_at_millis")
        val PREMIUM_UNLOCKED = booleanPreferencesKey("premium_unlocked")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            defaultReminderOffsets = prefs[Keys.REMINDER_OFFSETS]
                ?.mapNotNull { it.toIntOrNull() }
                ?.sortedDescending()
                ?: DEFAULT_REMINDER_OFFSETS,
            digestFrequency = prefs[Keys.DIGEST_FREQUENCY]
                ?.let { runCatching { DigestFrequency.valueOf(it) }.getOrNull() }
                ?: DigestFrequency.WEEKLY,
            autoDetectEnabled = prefs[Keys.AUTO_DETECT_ENABLED] ?: false,
            themeMode = prefs[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            region = prefs[Keys.REGION]
                ?.let { runCatching { Region.valueOf(it) }.getOrNull() }
                ?: Region.INDIA,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            lastBackupAtMillis = prefs[Keys.LAST_BACKUP_AT],
            lastAutoDetectScanAtMillis = prefs[Keys.LAST_AUTO_DETECT_SCAN_AT] ?: 0L,
            autoDetectSuggestedFingerprints = prefs[Keys.AUTO_DETECT_SUGGESTED_FINGERPRINTS] ?: emptySet(),
            pendingAutoDetectSuggestions = prefs[Keys.PENDING_AUTO_DETECT_SUGGESTIONS]
                ?.mapNotNull { PendingAutoDetectSuggestion.decode(it) }
                ?.sortedByDescending { it.detectedAtMillis }
                ?: emptyList(),
            installedAtMillis = prefs[Keys.INSTALLED_AT] ?: 0L,
            premiumUnlocked = prefs[Keys.PREMIUM_UNLOCKED] ?: false,
        )
    }

    suspend fun setDefaultReminderOffsets(offsets: List<Int>) {
        context.dataStore.edit { it[Keys.REMINDER_OFFSETS] = offsets.map(Int::toString).toSet() }
    }

    suspend fun setDigestFrequency(frequency: DigestFrequency) {
        context.dataStore.edit { it[Keys.DIGEST_FREQUENCY] = frequency.name }
    }

    suspend fun setAutoDetectEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_DETECT_ENABLED] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** See Region's doc comment -- INDIA default keeps every existing
     *  install's formatting unchanged unless the user opens Settings and
     *  explicitly picks something else. */
    suspend fun setRegion(region: Region) {
        context.dataStore.edit { it[Keys.REGION] = region.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setLastBackupAtMillis(millis: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = millis }
    }

    suspend fun setLastAutoDetectScanAtMillis(millis: Long) {
        context.dataStore.edit { it[Keys.LAST_AUTO_DETECT_SCAN_AT] = millis }
    }

    /** Merges [fingerprints] into the persisted set -- never removes any (see UserPreferences doc). */
    suspend fun addAutoDetectSuggestedFingerprints(fingerprints: Set<String>) {
        if (fingerprints.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.AUTO_DETECT_SUGGESTED_FINGERPRINTS] ?: emptySet()
            prefs[Keys.AUTO_DETECT_SUGGESTED_FINGERPRINTS] = existing + fingerprints
        }
    }

    /** Adds newly-found matches to the reviewable queue (AutoDetectWorker,
     *  once per scan run). Encoded via PendingAutoDetectSuggestion.encode --
     *  a plain Set union, so re-adding an entry already present (shouldn't
     *  happen given the fingerprint dedup upstream, but harmless if it did)
     *  is a no-op rather than a duplicate row. */
    suspend fun addPendingAutoDetectSuggestions(suggestions: List<PendingAutoDetectSuggestion>) {
        if (suggestions.isEmpty()) return
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.PENDING_AUTO_DETECT_SUGGESTIONS] ?: emptySet()
            prefs[Keys.PENDING_AUTO_DETECT_SUGGESTIONS] = existing + suggestions.map { it.encode() }
        }
    }

    /** Removes one suggestion from the reviewable queue -- called once the
     *  user has acted on it, whichever way (AutoDetectSuggestionsScreen's
     *  Add or Dismiss). Matches by fingerprint (stable identity) rather
     *  than the full encoded string, so this doesn't silently no-op if
     *  detectedAtMillis or the URI were ever re-derived differently between
     *  read and write. */
    suspend fun removePendingAutoDetectSuggestion(fingerprint: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.PENDING_AUTO_DETECT_SUGGESTIONS] ?: emptySet()
            val filtered = existing.filter { PendingAutoDetectSuggestion.decode(it)?.fingerprint != fingerprint }.toSet()
            prefs[Keys.PENDING_AUTO_DETECT_SUGGESTIONS] = filtered
        }
    }

    /** Stamps [Keys.INSTALLED_AT] with the current time, but only the
     *  first time this ever runs for this install -- the trial clock (see
     *  com.venunair.wisma.license.LicenseState) must never move once set.
     *  Idempotent and safe to call unconditionally on every cold start;
     *  called from WardenApplication.onCreate. */
    suspend fun ensureInstalledAtStamped() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.INSTALLED_AT] == null) {
                prefs[Keys.INSTALLED_AT] = System.currentTimeMillis()
            }
        }
    }

    /** Flipped by the Play Billing purchase-confirmation flow once the
     *  one-time "Premium unlock" product is wired up -- no caller yet. */
    suspend fun setPremiumUnlocked(unlocked: Boolean) {
        context.dataStore.edit { it[Keys.PREMIUM_UNLOCKED] = unlocked }
    }
}
