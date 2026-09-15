package com.venunair.wisma.data

/** How often the weekly-digest-style summary notification fires. */
enum class DigestFrequency {
    WEEKLY, MONTHLY, OFF;

    val displayName: String
        get() = when (this) {
            WEEKLY -> "Weekly"
            MONTHLY -> "Monthly"
            OFF -> "Off"
        }
}

/** Which palette the app renders in. SYSTEM follows the device setting. */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    val displayName: String
        get() = when (this) {
            SYSTEM -> "System"
            LIGHT -> "Light"
            DARK -> "Dark"
        }
}

/**
 * Sprint 9: all user-configurable app settings, persisted via
 * DataStore<Preferences> (see SettingsRepository). A single snapshot class
 * rather than exposing each preference as its own Flow keeps every
 * screen that reads settings (Settings itself, AddEditItemScreen's
 * reminder defaults, WardenTheme, WardenApplication's digest scheduling)
 * collecting one Flow instead of five.
 */
data class UserPreferences(
    /** Reminder day-offsets pre-selected when adding a new item. */
    val defaultReminderOffsets: List<Int> = DEFAULT_REMINDER_OFFSETS,
    val digestFrequency: DigestFrequency = DigestFrequency.WEEKLY,
    val autoDetectEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Currency/date display convention -- see Region's own doc comment.
     *  Defaults to INDIA so every existing install's behaviour is
     *  unchanged unless the user explicitly opens Settings and picks
     *  something else. */
    val region: Region = Region.INDIA,
    val onboardingCompleted: Boolean = false,
    /** Epoch millis of the last successful backup, or null if never run.
     *  Populated starting Sprint 10 — the field lives here now so the
     *  Settings screen's Backup section has something real to bind to
     *  the moment that lands, instead of a second migration later. */
    val lastBackupAtMillis: Long? = null,
    /** Epoch millis the auto-detect MediaStore scan last completed
     *  through, so each run only looks at images added since then. */
    val lastAutoDetectScanAtMillis: Long = 0L,
    /** SHA-256 fingerprints (see AutoDetectWorker.contentFingerprint) of
     *  every photo's OCR text that has already produced a suggestion
     *  notification, so the same receipt saved to the gallery more than
     *  once (screenshotted twice, saved from an email twice, ...) only
     *  ever prompts once -- persisted, not per-run, since the duplicate
     *  can just as easily turn up in a LATER scan as the same one. */
    val autoDetectSuggestedFingerprints: Set<String> = emptySet(),
    /** Sprint 9 bugfix: every heuristic match still awaiting the user's
     *  review — see PendingAutoDetectSuggestion and
     *  AutoDetectSuggestionsScreen. Newest first. Distinct from (and a
     *  subset of, timing-wise) autoDetectSuggestedFingerprints above: that
     *  set only ever grows, this list shrinks as items are reviewed. */
    val pendingAutoDetectSuggestions: List<PendingAutoDetectSuggestion> = emptyList(),
)
