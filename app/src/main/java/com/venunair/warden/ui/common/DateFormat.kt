package com.venunair.warden.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.venunair.warden.data.Region
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * dd-MM-yyyy — the format Indian users expect. Purely a presentation-layer
 * concern: Room/Item still store LocalDate as ISO under the hood (see
 * data/Converters.kt), and OCR date parsing works off the raw text it
 * finds on receipts, not this formatter. This is only ever used to render
 * a LocalDate for display, and (via the DatePicker) to render the
 * picker's selection — there is no free-text date entry left to parse,
 * which removes the "typo'd the date format" bug class entirely.
 *
 * This is the exact same pattern that was hardcoded here before Region
 * existed -- the INDIA branch below, not a re-derived approximation.
 *
 * The other four patterns are a deliberate, disclosed simplification, not
 * an authoritative locale study: UK and Australia keep the day-first
 * convention India already uses; US and Canada get month-first, since
 * that's the everyday convention in both (Canadian government style
 * guides differ, but this app has no official/legal-document use case).
 * Easy to adjust per-country later if a tester flags it as wrong for them.
 */
private val INDIAN_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private val US_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
private val UK_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
private val CANADA_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy")
private val AUSTRALIA_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

private fun dateFormatterFor(region: Region): DateTimeFormatter = when (region) {
    Region.INDIA -> INDIAN_DATE_FORMATTER
    Region.UNITED_STATES -> US_DATE_FORMATTER
    Region.UNITED_KINGDOM -> UK_DATE_FORMATTER
    Region.CANADA -> CANADA_DATE_FORMATTER
    Region.AUSTRALIA -> AUSTRALIA_DATE_FORMATTER
}

/** Plain, region-parameterized formatter -- usable anywhere, including
 *  non-Composable contexts (DigestNotificationWorker, buildClaimInfoText)
 *  where there is no composition to read a CompositionLocal from. */
fun LocalDate.toDateString(region: Region): String = format(dateFormatterFor(region))

fun LocalDate?.toDateStringOrDash(region: Region): String = this?.toDateString(region) ?: "—"

/** Composable convenience overload: reads the ambient Region (see
 *  LocalRegion / WardenNavHost). */
@Composable
@ReadOnlyComposable
fun LocalDate.toDateString(): String = toDateString(LocalRegion.current)

@Composable
@ReadOnlyComposable
fun LocalDate?.toDateStringOrDash(): String = toDateStringOrDash(LocalRegion.current)

/**
 * DatePickerState works in UTC epoch millis at midnight, not the device's
 * local zone — converting through the system default zone instead would
 * occasionally shift the selected date by a day depending on the user's
 * timezone offset at that moment. Always go through UTC, both directions.
 * Unrelated to display formatting above -- not region-dependent.
 */
fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.toLocalDateFromUtcMillis(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Sentence-style relative phrasing ("Expires in 11 months", "Due today",
 * "Expired 3 days ago") for pairing next to the exact date, not replacing
 * it -- callers still show the formatted date alongside this. Rolls over
 * to months past 60 days out (or 60 days past) so a year-long warranty
 * doesn't read as "365 days" the way [ChronoUnit.DAYS] alone would.
 */
fun LocalDate.toRelativeDueString(today: LocalDate = LocalDate.now()): String {
    val days = ChronoUnit.DAYS.between(today, this)
    return when {
        days == 0L -> "Due today"
        days > 0 -> if (days < 60) {
            "Due in $days day${if (days == 1L) "" else "s"}"
        } else {
            val months = ChronoUnit.MONTHS.between(today, this)
            "Due in $months month${if (months == 1L) "" else "s"}"
        }
        else -> {
            val agoDays = -days
            if (agoDays < 60) {
                "Expired $agoDays day${if (agoDays == 1L) "" else "s"} ago"
            } else {
                val agoMonths = ChronoUnit.MONTHS.between(this, today)
                "Expired $agoMonths month${if (agoMonths == 1L) "" else "s"} ago"
            }
        }
    }
}
