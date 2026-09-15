package com.venunair.wisma.autodetect

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.venunair.wisma.WardenApplication
import com.venunair.wisma.data.PendingAutoDetectSuggestion
import com.venunair.wisma.ocr.recognizeText
import com.venunair.wisma.reminders.NotificationHelper
import com.venunair.wisma.ui.common.decodeBitmapForOcr
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Sprint 9: opt-in periodic scan for new gallery photos that look like a
 * receipt or warranty card — spec section 7 / SPRINT_PLAN_6_10.md's
 * "Auto-detect" feature. Runs only while the user has explicitly enabled
 * it in Settings (see WardenApplication.applyAutoDetectSchedule); the
 * media-images permission is requested from the Settings screen before
 * the toggle can be turned on, but this worker re-checks it defensively
 * (permission could be revoked from system Settings after the fact)
 * rather than assuming.
 *
 * Deliberately conservative: never opens the image, never auto-saves
 * anything, never touches the Room database. It only decides "does this
 * look receipt-like?" and, if so, (a) posts a low-priority suggestion
 * notification (NotificationHelper.showAutoDetectSuggestion) — tapping it
 * hands the image off to AddEditItemScreen through the exact same
 * pending-attachment path a Share-sheet hand-off uses, where the existing
 * OCR + field-prefill logic (handleNewAttachment) takes over — and (b)
 * queues it into a persisted, reviewable list (SettingsRepository's
 * pendingAutoDetectSuggestions, surfaced by AutoDetectSuggestionsScreen via
 * a HomeScreen badge). (b) exists because (a) alone can be missed entirely:
 * POST_NOTIFICATIONS or the auto-detect channel can be off, or the user can
 * simply not notice a low-priority shade notification — the persisted list
 * is the durable, always-checkable record of every match either way. This
 * worker's own OCR pass exists ONLY to evaluate the keyword heuristic
 * below, not to extract fields.
 *
 * Every run reports its outcome via output [Result.success] data (see the
 * KEY_* constants below) rather than a bare success/failure — this is the
 * only way to see WHY a run produced no notification (no media access?
 * zero candidates in the time window? candidates examined but none
 * matched the heuristic? matched but POST_NOTIFICATIONS wasn't granted?)
 * without device logcat access. HomeScreen's debug "run now" button reads
 * this back and shows it directly.
 */
class AutoDetectWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hasMediaAccess = hasMediaImageAccess(applicationContext)
        val hasNotificationPermission = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMediaAccess) {
            return Result.success(
                workDataOf(
                    KEY_MEDIA_PERMISSION_GRANTED to false,
                    KEY_NOTIFICATION_PERMISSION_GRANTED to hasNotificationPermission,
                    KEY_CANDIDATES_EXAMINED to 0,
                    KEY_HEURISTIC_MATCHES to 0,
                    KEY_DUPLICATES_SKIPPED to 0,
                    KEY_NOTIFICATIONS_POSTED to 0
                )
            )
        }

        val settingsRepository = (applicationContext as WardenApplication).settingsRepository
        // Single snapshot read, not a live collector — a worker run is a
        // one-shot unit of work, not something that should react to a
        // setting changing mid-run.
        val lastScanAtMillis = settingsRepository.preferences.first().lastAutoDetectScanAtMillis

        // The debug "run now" button (HomeScreen) sets this input flag so
        // repeat manual testing doesn't require a brand-new photo every
        // time: every completed run — match or not — advances the
        // watermark to "now", so a photo that was already examined once
        // (even if it didn't match, e.g. before the EXIF-rotation fix)
        // silently drops out of the normal incremental window on the next
        // run. The real periodic schedule (WardenApplication) never sets
        // this, so its incremental behavior is unaffected.
        val forceFullRescan = inputData.getBoolean(KEY_FORCE_FULL_RESCAN, false)

        // First-ever run (or a forced rescan): only look at the last 30
        // days, not the user's entire photo library. Without this bound,
        // enabling Auto-detect for the first time on a phone with years of
        // photos would queue an OCR pass over every image ever taken.
        val sinceMillis = if (forceFullRescan || lastScanAtMillis == 0L) {
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        } else {
            lastScanAtMillis
        }
        val nowMillis = System.currentTimeMillis()

        // A forced debug rescan is for "did THIS one photo just get
        // picked up", not a full backfill -- capping it well below
        // MAX_IMAGES_PER_RUN keeps the debug button's OCR pass fast
        // (combined with the DESC ordering above, the newest photos --
        // the ones someone is actually testing -- are examined first and
        // always included) instead of silently taking minutes to grind
        // through up to 50 unrelated photos before reporting back.
        val limit = if (forceFullRescan) FORCE_RESCAN_LIMIT else MAX_IMAGES_PER_RUN
        val candidates = queryImagesAddedSince(sinceMillis)
            .take(limit)

        // Fingerprints already suggested -- persisted (see UserPreferences
        // doc), not per-run, so the SAME receipt turning up again in a
        // LATER scan is caught too, not just two copies examined together
        // in one run.
        val knownFingerprints = settingsRepository.preferences.first().autoDetectSuggestedFingerprints
        val newFingerprints = mutableSetOf<String>()

        var heuristicMatches = 0
        var duplicatesSkipped = 0
        var notificationsPosted = 0
        var topCandidateResult: EvaluationResult? = null
        // Populated in the SAME loop as newFingerprints below, for every
        // genuine (non-duplicate) match — this is the persisted, reviewable
        // queue AutoDetectSuggestionsScreen reads, independent of whether a
        // notification was actually seen (see KEY_SUGGESTIONS_QUEUED and the
        // class doc's "silent notification" rationale).
        val newSuggestions = mutableListOf<PendingAutoDetectSuggestion>()
        for ((index, candidate) in candidates.withIndex()) {
            // A single corrupt image or OCR failure shouldn't abort the
            // rest of the scan — same "best-effort, never UI-critical"
            // rule the in-app OCR pipeline follows.
            val result = runCatching { evaluateAndMaybeNotify(candidate, knownFingerprints + newFingerprints) }
                .getOrDefault(EvaluationResult.FAILED)
            if (index == 0) topCandidateResult = result
            if (result.matchedHeuristic) heuristicMatches++
            if (result.duplicateSkipped) duplicatesSkipped++
            if (result.notificationPosted) notificationsPosted++
            if (result.matchedHeuristic && !result.duplicateSkipped && result.fingerprint != null) {
                newSuggestions.add(
                    PendingAutoDetectSuggestion(
                        fingerprint = result.fingerprint,
                        imageUri = candidate.uriString,
                        detectedAtMillis = candidate.dateAddedMillis
                    )
                )
            }
            // Added to the local set immediately (not just at the end) so
            // two duplicates examined in THIS SAME run only ever notify
            // once each other, not just against runs before this one.
            result.fingerprint?.let { newFingerprints.add(it) }
        }

        if (newFingerprints.isNotEmpty()) {
            settingsRepository.addAutoDetectSuggestedFingerprints(newFingerprints)
        }
        if (newSuggestions.isNotEmpty()) {
            settingsRepository.addPendingAutoDetectSuggestions(newSuggestions)
        }
        settingsRepository.setLastAutoDetectScanAtMillis(nowMillis)
        return Result.success(
            workDataOf(
                KEY_MEDIA_PERMISSION_GRANTED to true,
                KEY_NOTIFICATION_PERMISSION_GRANTED to hasNotificationPermission,
                KEY_CANDIDATES_EXAMINED to candidates.size,
                KEY_HEURISTIC_MATCHES to heuristicMatches,
                KEY_DUPLICATES_SKIPPED to duplicatesSkipped,
                KEY_NOTIFICATIONS_POSTED to notificationsPosted,
                KEY_SUGGESTIONS_QUEUED to newSuggestions.size,
                // Diagnostic breakdown for JUST the newest photo (index 0,
                // thanks to the DESC query order) -- when heuristicMatches
                // is 0 despite examining real candidates, this is the only
                // way to tell "OCR never read any usable text" apart from
                // "OCR read it fine but neither pattern matched" without
                // device logcat access.
                KEY_TOP_CANDIDATE_BITMAP_DECODED to (topCandidateResult?.bitmapDecoded ?: false),
                KEY_TOP_CANDIDATE_OCR_SUCCEEDED to (topCandidateResult?.ocrSucceeded ?: false),
                KEY_TOP_CANDIDATE_TEXT_LENGTH to (topCandidateResult?.textLength ?: 0),
                KEY_TOP_CANDIDATE_HAS_CURRENCY to (topCandidateResult?.hasCurrencyMarker ?: false),
                KEY_TOP_CANDIDATE_HAS_KEYWORD to (topCandidateResult?.hasKeyword ?: false)
            )
        )
    }

    private data class ImageCandidate(val id: Long, val uriString: String, val dateAddedMillis: Long)

    private data class EvaluationResult(
        val bitmapDecoded: Boolean,
        val ocrSucceeded: Boolean,
        val textLength: Int,
        val hasCurrencyMarker: Boolean,
        val hasKeyword: Boolean,
        val matchedHeuristic: Boolean,
        val duplicateSkipped: Boolean,
        val notificationPosted: Boolean,
        /** Non-null exactly when matchedHeuristic is true -- see contentFingerprint. */
        val fingerprint: String? = null
    ) {
        companion object {
            val FAILED = EvaluationResult(
                bitmapDecoded = false, ocrSucceeded = false, textLength = 0,
                hasCurrencyMarker = false, hasKeyword = false,
                matchedHeuristic = false, duplicateSkipped = false, notificationPosted = false
            )
        }
    }

    private fun queryImagesAddedSince(sinceMillis: Long): List<ImageCandidate> {
        val sinceSeconds = sinceMillis / 1000 // MediaStore.DATE_ADDED is epoch SECONDS, not millis
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(sinceSeconds.toString())
        // DESC, not ASC: the take(MAX_IMAGES_PER_RUN) below is a hard cap,
        // and a wide window (30 days on first run or a forced rescan) can
        // easily hold more than that many photos of any kind, receipts or
        // not. Newest-first guarantees the cap always keeps the most
        // recently added photos -- ascending order would silently drop the
        // photo someone JUST took off the end of a >50-photo month.
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val results = mutableListOf<ImageCandidate>()
        applicationContext.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAddedMillis = cursor.getLong(dateAddedColumn) * 1000 // seconds -> millis
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                results.add(ImageCandidate(id, uri.toString(), dateAddedMillis))
            }
        }
        return results
    }

    /**
     * [EvaluationResult.matchedHeuristic] and [EvaluationResult.notificationPosted]
     * are deliberately separate: a match with no notification posted almost
     * always means POST_NOTIFICATIONS isn't granted (see
     * NotificationHelper.showAutoDetectSuggestion), which is a different,
     * fixable problem from "the heuristic never matched this photo".
     */
    private suspend fun evaluateAndMaybeNotify(candidate: ImageCandidate, knownFingerprints: Set<String>): EvaluationResult {
        val bitmap = decodeBitmapForOcr(applicationContext, candidate.uriString)
            ?: return EvaluationResult.FAILED
        val text = runCatching { recognizeText(bitmap) }.getOrNull()?.text
            ?: return EvaluationResult.FAILED.copy(bitmapDecoded = true)

        // Whole-document existence checks -- informational only (surfaced
        // in the debug diagnostic), NOT what decides a match. See
        // looksLikeReceiptLine below for the actual, stricter criterion.
        val hasCurrencyMarker = CURRENCY_MARKER_REGEX.containsMatchIn(text) ||
            PLAIN_AMOUNT_REGEX.containsMatchIn(stripDateLikeSubstrings(text))
        val hasKeyword = KEYWORD_REGEX.containsMatchIn(text)

        // Requiring the two signals to land on the SAME LINE (not just
        // "somewhere in the document, independently") is what keeps
        // PLAIN_AMOUNT_REGEX's broad "any 2-decimal number" from matching
        // things that plainly aren't receipts -- confirmed against a real
        // false positive: a laptop's spec/serial-number scan, where
        // "Serial Number: ..." sits nowhere near any of the page's
        // incidental decimal-shaped numbers. A real total line ("Total
        // 2,750.00", "Total ₹2,750.00") always has both right next to
        // each other; a coincidental keyword elsewhere on an unrelated
        // document essentially never does.
        val matched = text.lineSequence().any { line -> looksLikeReceiptLine(line) }

        if (!matched) {
            return EvaluationResult(
                bitmapDecoded = true, ocrSucceeded = true, textLength = text.length,
                hasCurrencyMarker = hasCurrencyMarker, hasKeyword = hasKeyword,
                matchedHeuristic = false, duplicateSkipped = false, notificationPosted = false
            )
        }

        // Same real-world document photographed/saved more than once (the
        // reported case: the same invoice ended up in the gallery twice)
        // produces near-identical OCR text -- fingerprint it and skip
        // re-suggesting a duplicate rather than posting a second "add
        // this?" prompt (and, if acted on twice, a second duplicate item).
        val fingerprint = contentFingerprint(text)
        if (fingerprint in knownFingerprints) {
            return EvaluationResult(
                bitmapDecoded = true, ocrSucceeded = true, textLength = text.length,
                hasCurrencyMarker = true, hasKeyword = true,
                matchedHeuristic = true, duplicateSkipped = true, notificationPosted = false,
                fingerprint = fingerprint
            )
        }

        // AUTO_DETECT_NOTIFICATION_ID_BASE + a truncated MediaStore row ID
        // keeps every auto-detect notification distinct from reminder
        // notifications (which use item.id.toInt()) and from the digest
        // (a single fixed ID) — see NotificationHelper's constants.
        val notificationId = NotificationHelper.AUTO_DETECT_NOTIFICATION_ID_BASE + (candidate.id % 1_000_000).toInt()
        val posted = NotificationHelper.showAutoDetectSuggestion(applicationContext, candidate.uriString, notificationId)
        return EvaluationResult(
            bitmapDecoded = true, ocrSucceeded = true, textLength = text.length,
            hasCurrencyMarker = true, hasKeyword = true,
            matchedHeuristic = true, duplicateSkipped = false, notificationPosted = posted,
            fingerprint = fingerprint
        )
    }

    private fun looksLikeReceiptLine(line: String): Boolean {
        // PLAIN_AMOUNT_REGEX ([\d,]+\.\d{2}) matches the day.month portion
        // of any DD.MM.YYYY-style dotted date (e.g. "31.03" inside
        // "31.03.2010") -- confirmed against a real false positive, a BMC
        // property-tax "Outstanding Statement" whose "(A) Total Outstanding
        // Upto 31.03.2010" line has both a date shaped like a currency
        // amount AND the keyword "Total" on the very same line, so last
        // round's line-scoping fix alone didn't stop it. Stripping
        // date-shaped substrings before the plain-amount check closes that
        // hole without touching the CURRENCY_MARKER_REGEX (₹/Rs/INR) path,
        // which never had this ambiguity.
        val hasCurrency = CURRENCY_MARKER_REGEX.containsMatchIn(line) ||
            PLAIN_AMOUNT_REGEX.containsMatchIn(stripDateLikeSubstrings(line))
        val hasKeyword = KEYWORD_REGEX.containsMatchIn(line)
        return hasCurrency && hasKeyword
    }

    /** Blanks out DD/MM/YYYY-, DD-MM-YYYY-, or DD.MM.YYYY-shaped substrings
     *  (2-decimal year forms like "31.03.10" included) so PLAIN_AMOUNT_REGEX
     *  can't mistake a date for a currency amount -- see looksLikeReceiptLine.
     *  Replaces with a space, not empty, so it can never accidentally splice
     *  two unrelated numbers together into a new false match. */
    private fun stripDateLikeSubstrings(text: String): String = DATE_LIKE_REGEX.replace(text, " ")

    /**
     * Stable fingerprint for "is this the same document as another photo
     * I've already suggested" -- normalizes whitespace/case first so two
     * OCR passes of visually-identical images (JPEG re-compression, a
     * stray extra space ML Kit sometimes inserts) still hash the same.
     * Not robust to genuinely different OCR readings of the same physical
     * document (different crop, angle, lighting) -- an accepted gap, not
     * a claim of perfect dedup; still catches the common case (the exact
     * same image saved to the gallery more than once) this was written for.
     */
    private fun contentFingerprint(text: String): String {
        val normalized = text.lowercase().replace(Regex("""\s+"""), " ").trim()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_IMAGES_PER_RUN = 50
        private const val FORCE_RESCAN_LIMIT = 10

        // Precision-over-recall, same rule OCR field extraction already
        // follows elsewhere in this app (ReceiptFieldParser.AMOUNT_REGEX):
        // only flag an image when there's a currency signal AND a
        // receipt/warranty keyword, not either alone.
        //
        // The currency signal is deliberately two patterns, not one.
        // ReceiptFieldParser already had to add PLAIN_TOTAL_AMOUNT_REGEX as
        // a fallback after real-device testing found ML Kit's Latin-script
        // model doesn't reliably read the ₹ glyph, and that real Indian
        // e-invoices routinely print the total with no currency symbol
        // anywhere near it (the currency is named once, in a header far
        // above). Matching only CURRENCY_MARKER_REGEX would inherit that
        // exact same failure mode here; PLAIN_AMOUNT_REGEX is the same
        // fallback pattern, reused for the same reason.
        private val CURRENCY_MARKER_REGEX = Regex("""(?i)(₹|\brs\.?\b|\binr\b)""")
        private val PLAIN_AMOUNT_REGEX = Regex("""[\d,]+\.\d{2}""")

        // DD/MM/YYYY, DD-MM-YYYY, or DD.MM.YYYY (2- or 4-digit year) --
        // matched and blanked out of a line/document BEFORE PLAIN_AMOUNT_REGEX
        // runs against it (see stripDateLikeSubstrings), so a dotted date's
        // day.month portion (e.g. "31.03" inside "31.03.2010") can't be
        // mistaken for a currency amount. Requires all three date components
        // (day, month, year), so it never touches a genuine 2-decimal amount
        // like "2,750.00" or "31.00", which only ever has one separator.
        private val DATE_LIKE_REGEX = Regex("""\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b""")

        // invoices?/receipts?/warrant(y|ies): plural forms don't match a
        // singular \b...\b pattern (a trailing "s" breaks the word
        // boundary) -- confirmed directly against a real invoice whose
        // header read "Customer Invoices", not "Invoice".
        private val KEYWORD_REGEX = Regex(
            """(?i)\b(warrant(?:y|ies)|invoices?|amc|receipts?|total|serial)\b"""
        )

        // Output-data keys — read back by HomeScreen's debug "run now" button
        // via WorkManager.getWorkInfoByIdFlow(request.id).
        const val KEY_MEDIA_PERMISSION_GRANTED = "media_permission_granted"
        const val KEY_NOTIFICATION_PERMISSION_GRANTED = "notification_permission_granted"
        const val KEY_CANDIDATES_EXAMINED = "candidates_examined"
        const val KEY_HEURISTIC_MATCHES = "heuristic_matches"
        const val KEY_DUPLICATES_SKIPPED = "duplicates_skipped"
        const val KEY_NOTIFICATIONS_POSTED = "notifications_posted"
        const val KEY_TOP_CANDIDATE_BITMAP_DECODED = "top_candidate_bitmap_decoded"
        const val KEY_TOP_CANDIDATE_OCR_SUCCEEDED = "top_candidate_ocr_succeeded"
        const val KEY_TOP_CANDIDATE_TEXT_LENGTH = "top_candidate_text_length"
        const val KEY_TOP_CANDIDATE_HAS_CURRENCY = "top_candidate_has_currency"
        const val KEY_TOP_CANDIDATE_HAS_KEYWORD = "top_candidate_has_keyword"
        // How many of this run's matches were newly queued into the
        // persisted, reviewable suggestions list (AutoDetectSuggestionsScreen)
        // -- distinct from KEY_NOTIFICATIONS_POSTED, which can under-report
        // (permission/channel gaps) even when a match genuinely landed here.
        const val KEY_SUGGESTIONS_QUEUED = "suggestions_queued"

        // Input-data key — set by HomeScreen's debug "run now" button to
        // force a full 30-day rescan regardless of the stored watermark.
        const val KEY_FORCE_FULL_RESCAN = "force_full_rescan"
    }
}
