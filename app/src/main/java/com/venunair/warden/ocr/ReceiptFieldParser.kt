package com.venunair.warden.ocr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Best-effort field guesses pulled from an attachment's raw OCR text.
 * Every field is nullable, and "found nothing" is the ordinary, expected
 * outcome for a lot of real documents -- not an error condition. Nothing
 * here is meant to be trusted blindly: AddEditItemScreen only ever uses
 * these to pre-fill fields that are STILL BLANK, which the user then
 * reviews (and can freely overwrite) before the Save button that actually
 * commits anything. That's the same principle Sprint 4's own scope note
 * calls out directly: on messy Indian invoices, a wrong silent guess is
 * worse than a five-second manual confirm -- and it's also exactly what
 * this app's pending-attachment redesign already established for the
 * attach-then-review flow generally.
 *
 * Deliberately does NOT propose a Name -- unlike vendor/date/cost, there's
 * no reliable text-pattern signal for "what is this item actually called"
 * on a generic receipt or warranty card (no currency symbol, no keyword,
 * nothing to anchor a regex to). An earlier version of this file used "the
 * first substantial text line" as a low-confidence Name/vendor fallback;
 * dropped after checking it against a spread of realistic Indian invoice
 * layouts, where that line is at least as often a GSTIN, an invoice
 * number, or a header ("TAX INVOICE") as it is an actual vendor name --
 * exactly the kind of confidently-wrong guess this file exists to avoid.
 */
data class ParsedReceiptFields(
    val vendor: String? = null,
    val purchaseDate: LocalDate? = null,
    val cost: Double? = null,
    /** Sprint 6: serial number extracted from labeled text on the document. */
    val serialNumber: String? = null,
    /** Sprint 6: model number extracted from labeled text on the document. */
    val modelNumber: String? = null
)

// Deliberately narrow, high-precision candidate patterns over broad ones --
// see the class doc comment above for why a wrong guess here is worse than
// no guess. DATE_FORMATS assumes day-first (Indian convention), consistent
// with the rest of this app's date handling (ui/common's Indian date/
// currency formatters) and the "Purchase date" field, which is optional
// and never feeds reminder logic (only Item.expiryDate does, and OCR never
// touches that field) -- so a wrong parse here is low-stakes and always
// user-reviewable, not silently propagated into anything that fires a
// notification.
private val DATE_FORMATS = listOf(
    "d/M/yyyy", "d-M-yyyy", "d.M.yyyy",
    "d/M/yy", "d-M-yy",
    "d MMM yyyy", "d MMMM yyyy",
    "MMM d, yyyy", "MMMM d, yyyy",
    "yyyy-MM-dd"
).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

// Broad on purpose -- this only finds CANDIDATE substrings; DATE_FORMATS
// above does the real validation via an actual parse attempt, so a
// candidate that isn't really a date (e.g. a serial number that happens to
// look date-shaped) just fails every format and is silently skipped.
//
// Tolerates ONE stray space wedged inside a digit group -- confirmed via a
// real user's on-device "Scanned text" export (Warden's raw-OCR viewer,
// 2026-08-22): ML Kit read a printed "05/04/2025" back as "05/04/2 025",
// splitting the year after its first digit. Neither of this file's other
// two verification methods -- pdftotext's embedded-text extraction, or a
// desktop Tesseract pass on a page rendered the same way the app does --
// reproduced that corruption, which is exactly why this shipped looking
// correct twice before failing for real. A day or month group only ever
// needs to tolerate a split after its first digit (it's 1-2 printed
// digits); a year group needs a wider tail since the observed split lands
// after just the first of up to four digits. The matched substring
// (including any internal space) is stripped down to bare digits in
// findDateIn below, immediately before being handed to DATE_FORMATS --
// never anywhere broader than that one already-date-shaped substring, so
// this can't merge unrelated numbers sitting elsewhere on the same line.
private const val DAY_OR_MONTH_GROUP = """\d\s?\d?"""
private const val YEAR_GROUP = """\d\s?\d{1,3}"""
private val DATE_CANDIDATE_REGEX = Regex(
    """\b($DAY_OR_MONTH_GROUP[/\-.]\s?$DAY_OR_MONTH_GROUP[/\-.]\s?$YEAR_GROUP|\d{1,2}\s+[A-Za-z]{3,9}\s+\d{2,4}|[A-Za-z]{3,9}\s+\d{1,2},?\s+\d{2,4})\b"""
)

// A real Indian retail invoice routinely prints several OTHER dates that
// have nothing to do with when the item was bought -- confirmed against
// two real Reliance Digital invoices, where a plain "first date found"
// picked a delivery-window date on one and a coupon's "Redeem EndDate" on
// the other, instead of either invoice's own "Dt:"-labeled transaction
// date. Any line carrying one of these is excluded from date
// consideration entirely, in both passes of findDate below.
private val EXCLUDED_DATE_LINE_KEYWORDS = listOf(
    "delivery", "datetime", "redeem", "valid", "expiry", "expires", "due date"
)

// A line carrying one of these is a much stronger signal that ITS date is
// the actual transaction/purchase date, rather than some other date that
// merely happens to appear on the page. Checked before falling back to
// "any remaining date-shaped candidate" -- see findDate.
private val PREFERRED_DATE_LINE_KEYWORDS = listOf(
    "dt:", "date:", "invoice date", "bill date", "purchase date", "transaction date"
)

// Requires an explicit currency marker (Rs./Rs/INR or the literal Rupee
// sign), deliberately -- a bare number with no marker is exactly as likely
// to be a phone number, a warranty-card serial, or a GSTIN as it is a
// price, and this file's whole design principle is precision over recall.
private val AMOUNT_REGEX = Regex(
    "(?:₹|Rs\\.?|INR)\\s?([\\d,]+(?:\\.\\d{1,2})?)",
    RegexOption.IGNORE_CASE
)

// Used ONLY as a fallback restricted to a line that already matched
// TOTAL_LINE_KEYWORDS -- see findCost. Requires two decimal places
// specifically so it can't accidentally grab a bare quantity ("1EA"), a GST
// percentage ("9.00%" is excluded by requiring no trailing '%' context via
// the keyword-line restriction, not by this pattern alone), or an HSN/SAC
// code, while still matching a real amount column that has no currency
// symbol printed next to it -- confirmed necessary against a real Reliance
// Digital e-invoice where every amount in the itemized/total table is a
// bare "121011.31" with no Rs./₹ anywhere near it (the currency is only
// named once, in a distant column header).
private val PLAIN_TOTAL_AMOUNT_REGEX = Regex("""[\d,]+\.\d{2}""")

// "balance due" and "amount due" added after a real Reliance Digital
// invoice used "BALANCE DUE" as its only total-line label -- "total" alone
// didn't cover it. Order matters only in that these are all checked
// per-line as OR conditions, not tried in priority order against a single
// line; the first line (in document order) that matches ANY of these wins.
private val TOTAL_LINE_KEYWORDS = listOf(
    "grand total", "total amount", "amount paid", "net amount",
    "balance due", "amount due", "total"
)

// A short, high-confidence list of brands/vendors likely to show up on a
// real Indian household's warranty cards, AMC contracts, and subscription
// receipts -- matched literally against the OCR text, case-insensitive.
// Intentionally NOT exhaustive: recall is sacrificed for precision here,
// same reasoning as the rest of this file. Extend this list first if
// real-device testing (the Sprint 4 acceptance check) turns up common
// vendors it keeps missing.
private val KNOWN_VENDORS = listOf(
    "LG", "Samsung", "Whirlpool", "Voltas", "Godrej", "Bosch", "IFB", "Haier",
    "Blue Star", "Daikin", "Hitachi", "Panasonic", "Sony", "OnePlus", "Xiaomi",
    "Apple", "Croma", "Reliance Digital", "Amazon", "Flipkart", "Otis",
    "Kone", "Schindler", "HDFC ERGO", "ICICI Lombard", "Bajaj Allianz",
    "Netflix", "Airtel", "Jio", "Havells", "Crompton", "Philips"
)

fun parseReceiptFields(rawText: String): ParsedReceiptFields {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return ParsedReceiptFields(
        vendor = findVendor(rawText),
        purchaseDate = findDate(lines),
        cost = findCost(lines, rawText),
        serialNumber = findLabeledValue(lines, SERIAL_NUMBER_KEYWORDS),
        modelNumber = findLabeledValue(lines, MODEL_NUMBER_KEYWORDS)
    )
}

private fun findVendor(rawText: String): String? =
    KNOWN_VENDORS.firstOrNull { known -> rawText.contains(known, ignoreCase = true) }

private fun findDate(lines: List<String>): LocalDate? {
    // Pass 1: a date on a line explicitly labeled as THE date (and not
    // also an excluded one -- excluded always wins over preferred, so a
    // line can't qualify via both).
    findDateIn(
        lines.filter { line ->
            PREFERRED_DATE_LINE_KEYWORDS.any { kw -> line.contains(kw, ignoreCase = true) } &&
                EXCLUDED_DATE_LINE_KEYWORDS.none { kw -> line.contains(kw, ignoreCase = true) }
        }
    )?.let { return it }
    // Pass 2: nothing explicitly labeled -- fall back to the first
    // date-shaped candidate on any line that at least isn't already known
    // to mean something else (delivery window, coupon validity, etc.).
    return findDateIn(
        lines.filterNot { line ->
            EXCLUDED_DATE_LINE_KEYWORDS.any { kw -> line.contains(kw, ignoreCase = true) }
        }
    )
}

private fun findDateIn(candidateLines: List<String>): LocalDate? {
    candidateLines.forEach { line ->
        DATE_CANDIDATE_REGEX.findAll(line).forEach { match ->
            // Strip any stray internal space DATE_CANDIDATE_REGEX tolerated
            // (see its comment) before parsing -- DATE_FORMATS' patterns
            // have no whitespace in them, so "05/04/2 025" needs to become
            // "05/04/2025" right here to actually parse. Scoped to just
            // this one matched substring, never the whole line.
            val candidate = match.value.replace(Regex("""\s+"""), "")
            for (formatter in DATE_FORMATS) {
                try {
                    return LocalDate.parse(candidate, formatter)
                } catch (e: DateTimeParseException) {
                    // Not this format -- try the next one against the same
                    // candidate substring before moving on to the next match.
                }
            }
        }
    }
    return null
}

private fun findCost(lines: List<String>, rawText: String): Double? {
    // Prefer an amount that sits on a line naming itself as a total --
    // line-item prices earlier on an invoice are individually smaller and
    // less relevant than the total actually paid.
    lines.forEach { line ->
        if (TOTAL_LINE_KEYWORDS.any { keyword -> line.contains(keyword, ignoreCase = true) }) {
            // Currency-marked amount on this line, if there is one -- the
            // higher-confidence signal, tried first.
            AMOUNT_REGEX.find(line)?.let { match -> return parseAmount(match.groupValues[1]) }
            // No currency symbol on this line (common on machine-generated
            // Indian retail invoices, where the amount column only names
            // its currency once, in a header far above) -- fall back to
            // the LAST plain decimal number on this SAME keyword-matched
            // line. Restricted to this one line, not the whole document,
            // so this still only ever trusts a number that's explicitly
            // labeled a total, never a bare number found by scanning
            // everything. "Last" because a totals row is conventionally
            // laid out taxable-amount / tax-amount / final-total, left to
            // right -- e.g. "TOTAL:  94699.27  26312.04  121011.31", where
            // 121011.31 (rightmost) is the one actually charged.
            PLAIN_TOTAL_AMOUNT_REGEX.findAll(line).lastOrNull()
                ?.let { match -> return parseAmount(match.value) }
        }
    }
    // Fallback: no line was explicitly labeled a total, so the largest
    // currency-marked amount anywhere on the page is a reasonable proxy --
    // a grand total is very rarely smaller than every line-item price that
    // makes it up. Still requires a currency marker here, deliberately --
    // with no keyword line to anchor to at all, a bare number search
    // across the whole document would be exactly the low-precision guess
    // this file's design avoids everywhere else.
    AMOUNT_REGEX.findAll(rawText)
        .mapNotNull { match -> parseAmount(match.groupValues[1]) }
        .maxOrNull()
        ?.let { return it }
    // Last resort: the largest plain-decimal amount that appears 2+ times
    // anywhere in the document. Confirmed necessary from a real user's
    // on-device "Scanned text" export: ML Kit recognized this invoice's
    // label column ("BALANCE DUE", "TOTAL:", ...) and its value column
    // (the actual amounts) as two SEPARATE text blocks, so every
    // keyword line above matched with no number anywhere on it -- neither
    // pdftotext's embedded-text extraction nor a desktop Tesseract pass
    // reproduced this, both kept label and value on one line. A real
    // grand total on an Indian retail invoice is reliably printed more
    // than once (the bill body, then again in the GST summary table, and
    // sometimes a footer); incidental noise that happens to be a 2-decimal
    // number (a GST rate, a line-item price) is both far smaller and much
    // less likely to repeat exactly. Only reached when NOTHING above found
    // a number at all, so this never overrides a same-line or
    // currency-marked match -- it's specifically the split-block case.
    val counts = PLAIN_TOTAL_AMOUNT_REGEX.findAll(rawText)
        .map { match -> match.value }
        .groupingBy { it }
        .eachCount()
    return counts.filterValues { count -> count >= 2 }
        .keys
        .mapNotNull { raw -> parseAmount(raw) }
        .maxOrNull()
}

private fun parseAmount(raw: String): Double? = raw.replace(",", "").toDoubleOrNull()

// Sprint 6: serial/model number extraction — same precision-over-recall
// philosophy as the rest of this file. Only looks for values explicitly
// labeled on the document; a bare alphanumeric string sitting alone on a
// line is too ambiguous to be useful as a serial or model guess.
private val SERIAL_NUMBER_KEYWORDS = listOf(
    "serial no", "serial number", "sr. no", "sr no", "s/n", "s.n.",
    "serial #", "imei", "vin"
)
private val MODEL_NUMBER_KEYWORDS = listOf(
    "model no", "model number", "model name", "model #", "model:",
    "product code", "part no", "part number", "sku"
)

/**
 * Finds a value on a line that contains one of the given keywords,
 * extracting everything after the keyword+separator. Returns null if no
 * labeled value is found or if the extracted value is too short to be
 * meaningful (a single character or blank).
 *
 * Handles common label formats on Indian invoices and warranty cards:
 *   "Serial No: ABC123-XYZ"
 *   "Model Number - GL-T292RPZX"
 *   "S/N ABC123"
 *   "IMEI: 123456789012345"
 */
private fun findLabeledValue(lines: List<String>, keywords: List<String>): String? {
    for (line in lines) {
        for (keyword in keywords) {
            val idx = line.indexOf(keyword, ignoreCase = true)
            if (idx < 0) continue
            // Skip past the keyword, then past any separator characters
            val afterKeyword = line.substring(idx + keyword.length).trimStart()
            val value = afterKeyword
                .removePrefix(":")
                .removePrefix("-")
                .removePrefix("#")
                .removePrefix(".")
                .trim()
            // At least 2 chars to be a plausible serial/model — a single
            // character is more likely OCR noise than a real value.
            if (value.length >= 2) return value
        }
    }
    return null
}
