package com.venunair.wisma.data

/**
 * Sprint 9 bugfix: a heuristic match AutoDetectWorker found, persisted so
 * discovery doesn't depend solely on the (easy-to-miss, permission- and
 * channel-dependent — see NotificationHelper.showAutoDetectSuggestion) push
 * notification. Surfaced in AutoDetectSuggestionsScreen, reachable from a
 * badge on HomeScreen, so the user has a durable place to review every
 * match and decide per-photo whether it's real. Removed from this list the
 * moment the user acts on it either way (added to the database, or
 * dismissed as not actually a receipt) — the underlying fingerprint is
 * added to autoDetectSuggestedFingerprints regardless (see
 * AutoDetectWorker), so a handled photo is never re-suggested even after
 * its entry here is gone.
 */
data class PendingAutoDetectSuggestion(
    val fingerprint: String,
    val imageUri: String,
    /** MediaStore's own DATE_ADDED for this photo (millis), not "when the
     *  scan ran" — a more meaningful timestamp to show the user than the
     *  time of a background job they never saw run. */
    val detectedAtMillis: Long
) {
    /**
     * DataStore<Preferences> only stores primitive sets, not structured
     * data — encode as one ':'-delimited string per set entry rather than
     * adding a JSON dependency for three fields. Order matters: fingerprint
     * (fixed-format hex SHA-256, see AutoDetectWorker.contentFingerprint)
     * and detectedAtMillis (all-digit) come first, in fields that can never
     * themselves contain ':' — imageUri comes LAST and is decoded with a
     * bounded split so its own colons (every MediaStore content:// URI has
     * at least two) can never be mistaken for field boundaries.
     */
    fun encode(): String = "$fingerprint:$detectedAtMillis:$imageUri"

    companion object {
        /** Null for a malformed entry rather than throwing — a corrupted or
         *  future-format prefs value should silently drop just that one
         *  suggestion, not crash the screen reading the whole set. */
        fun decode(raw: String): PendingAutoDetectSuggestion? {
            val parts = raw.split(":", limit = 3)
            if (parts.size != 3) return null
            val millis = parts[1].toLongOrNull() ?: return null
            return PendingAutoDetectSuggestion(
                fingerprint = parts[0],
                imageUri = parts[2],
                detectedAtMillis = millis
            )
        }
    }
}
