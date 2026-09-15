package com.venunair.warden.data

/**
 * Which country's currency symbol/digit-grouping and date convention the
 * app displays with -- added 2026-09-01 for the first non-India testers.
 *
 * Deliberately scoped to English-speaking markets only for now (the user's
 * own call: "concentrate only on English speaking countries only for the
 * time being"). Adding a language/translation is a separate, much larger
 * project (every string in the app, not just these five) -- this enum
 * only ever changes how numbers and dates are FORMATTED, never what
 * language any label is written in, so it's safe to ship without touching
 * strings.xml at all.
 *
 * INDIA is first and is [UserPreferences]'s default deliberately -- every
 * existing install (and every fresh one, unless the user goes into
 * Settings and changes it) keeps exactly today's ₹ / dd-MM-yyyy behaviour,
 * bit-for-bit. See ui/common/CurrencyFormat.kt and DateFormat.kt: the
 * INDIA branch in each is the literal same Locale("en","IN") that was
 * hardcoded before this enum existed, not a re-derived approximation of
 * it -- that equivalence is what makes this change safe to ship without
 * disturbing the India launch.
 *
 * OCR/receipt-scanning is NOT region-aware yet (see ReceiptFieldParser /
 * AutoDetectWorker) -- it still only recognises ₹/Rs/INR and day-first
 * dates, regardless of this setting. A US/UK/Canada/Australia user gets
 * full manual entry with correctly-formatted currency and dates, but
 * scan-to-autofill won't reliably read a $/£ receipt yet. Flagged here so
 * this doesn't read as an oversight later -- it's a deliberately deferred,
 * separate, larger piece of work.
 */
enum class Region {
    INDIA, UNITED_STATES, UNITED_KINGDOM, CANADA, AUSTRALIA;

    val displayName: String
        get() = when (this) {
            INDIA -> "India"
            UNITED_STATES -> "United States"
            UNITED_KINGDOM -> "United Kingdom"
            CANADA -> "Canada"
            AUSTRALIA -> "Australia"
        }
}
