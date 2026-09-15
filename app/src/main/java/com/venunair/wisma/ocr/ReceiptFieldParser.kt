package com.venunair.wisma.ocr

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
 *
 * Feedback, 2026-08-26: real-device testing (this file's own stated bar
 * for extending KNOWN_VENDORS) found vendor going unrecognized on ordinary
 * invoices that print it plainly -- because [findVendor] only ever
 * consulted a closed brand whitelist, never the document's own "Sold by:"/
 * "Seller:" line. Added a labeled-line pass ([findVendorFromLabel]) ahead
 * of the whitelist, same technique serial/model already used successfully,
 * plus three more Product-details fields the whitelist-only approach never
 * touched at all: retailer, invoiceNumber, and referenceNumber (feeds
 * Item.amcNumber -- see [com.venunair.wisma.data.referenceNumberLabel]
 * for its category-aware on-screen label). Deliberately still label-
 * required for all three, same precision-over-recall bar as serial/model.
 */
data class ParsedReceiptFields(
    val vendor: String? = null,
    val purchaseDate: LocalDate? = null,
    val cost: Double? = null,
    /** Sprint 6: serial number extracted from labeled text on the document. */
    val serialNumber: String? = null,
    /** Sprint 6: model number extracted from labeled text on the document. */
    val modelNumber: String? = null,
    /** Feedback, 2026-08-26: store/dealer the item was bought from -- distinct from [vendor] (the brand/manufacturer printed on the same document). */
    val retailer: String? = null,
    /** Feedback, 2026-08-26: invoice/bill/receipt/order number. */
    val invoiceNumber: String? = null,
    /** Feedback, 2026-08-26: policy/contract/AMC/warranty-card number. */
    val referenceNumber: String? = null
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

// Feedback, 2026-08-26: a real receipt/invoice/warranty card almost always
// names its own seller explicitly -- "Sold by:" (every e-commerce
// invoice), "Seller:", "Billed by:", "Authorised Dealer:" -- which is a
// far stronger, document-specific signal than a closed brand whitelist,
// and it's the whole reason vendor detection kept missing real invoices:
// KNOWN_VENDORS above can only ever recognize names someone thought to add
// to it in advance. Checked BEFORE the whitelist in findVendor -- it's
// more precise (it's what THIS document says, not a guess from a fixed
// list) and, on marketplace invoices, it's often a different, more
// correct answer than the whitelist would give anyway (Amazon.in's own
// "Sold by:" line usually names the actual third-party seller, not
// "Amazon" itself).
private val VENDOR_LABEL_KEYWORDS = listOf(
    "sold by", "seller name", "seller", "billed by", "bill from",
    "dealer name", "authorized dealer", "authorised dealer",
    "issued by", "service provider"
)

// A seller line often carries registration details after the actual name
// ("Sold by: Appario Retail Private Ltd, GSTIN: 29AAxxx...") -- cut the
// extracted value at the first such marker rather than keeping the whole
// tail. Deliberately narrow (a handful of markers) rather than trying to detect
// every possible trailing clause -- an over-long but otherwise-correct
// vendor name is a five-second manual trim; this file's precision-over-
// recall bar is about not inventing a WRONG name, not about perfect
// tidiness.
private val VENDOR_TRAILING_MARKERS = listOf("gstin", "gst no", "pan no", "pan:", "cin no", "cin:")

// Feedback, 2026-08-26: same reasoning as VENDOR_LABEL_KEYWORDS above,
// scoped to retailer (where it was BOUGHT) rather than vendor (who MAKES
// it) -- Item keeps these as two separate fields (see Item.retailer's own
// doc comment), and OCR previously never populated retailer at all. A
// short list of retail chains/marketplaces likely to show up on a real
// Indian household's electronics purchase, same "extend first if real-
// device testing turns up a common one it keeps missing" policy
// KNOWN_VENDORS above already documents -- checked only when no line is
// explicitly labeled (most warranty cards/AMC contracts have no retailer
// label at all, since the retailer isn't party to those documents).
private val RETAILER_KEYWORDS = listOf(
    "retailer", "store name", "purchased from", "bought from",
    "shop name", "outlet name"
)
private val KNOWN_RETAILERS = listOf(
    "Croma", "Reliance Digital", "Amazon", "Flipkart", "Vijay Sales",
    "Tata Cliq", "Snapdeal", "Sathya", "Poorvika", "Bajaj Electronics"
)

// Feedback, 2026-08-26: Sprint 6 added Item.invoiceNumber, but OCR was
// never taught to look for one -- same gap as retailer above. "Order
// id/no/number" covers e-commerce packing-slip-style receipts that never
// say "invoice" at all.
private val INVOICE_NUMBER_KEYWORDS = listOf(
    "invoice no", "invoice number", "invoice #", "bill no", "bill number",
    "receipt no", "receipt number", "order id", "order no", "order number"
)

// Feedback, 2026-08-26: feeds Item.amcNumber -- the same reference-number
// field AMC/Insurance/Warranty categories all reuse under a category-aware
// label (see ItemCategory.referenceNumberLabel) -- which is exactly the
// kind of thing a warranty card or AMC contract prints explicitly, and
// which OCR never looked for at all before this pass.
private val REFERENCE_NUMBER_KEYWORDS = listOf(
    "policy no", "policy number", "contract no", "contract number",
    "agreement no", "agreement number", "amc no", "amc number",
    "warranty card no", "warranty no", "warranty number"
)

fun parseReceiptFields(rawText: String): ParsedReceiptFields {
    val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return ParsedReceiptFields(
        vendor = findVendor(lines, rawText),
        purchaseDate = findDate(lines),
        cost = findCost(lines, rawText),
        serialNumber = findLabeledValue(lines, SERIAL_NUMBER_KEYWORDS),
        modelNumber = findLabeledValue(lines, MODEL_NUMBER_KEYWORDS),
        retailer = findRetailer(lines, rawText),
        invoiceNumber = findLabeledValue(lines, INVOICE_NUMBER_KEYWORDS),
        referenceNumber = findLabeledValue(lines, REFERENCE_NUMBER_KEYWORDS)
    )
}

private fun findVendor(lines: List<String>, rawText: String): String? =
    findVendorFromLabel(lines)
        ?: KNOWN_VENDORS.firstOrNull { known -> rawText.contains(known, ignoreCase = true) }

private fun findVendorFromLabel(lines: List<String>): String? {
    val raw = findLabeledValue(lines, VENDOR_LABEL_KEYWORDS) ?: return null
    val cutAt = VENDOR_TRAILING_MARKERS
        .mapNotNull { marker -> raw.indexOf(marker, ignoreCase = true).takeIf { it >= 0 } }
        .minOrNull()
    val cleaned = if (cutAt != null) raw.substring(0, cutAt) else raw
    return cleaned.trim(',', '|', ' ', '-').takeIf { it.length >= 2 }
}

private fun findRetailer(lines: List<String>, rawText: String): String? =
    findLabeledValue(lines, RETAILER_KEYWORDS)
        ?: KNOWN_RETAILERS.firstOrNull { known -> rawText.contains(known, ignoreCase = true) }

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
