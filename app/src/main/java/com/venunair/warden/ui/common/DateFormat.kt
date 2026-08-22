package com.venunair.warden.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * dd-MM-yyyy — the format Indian users expect. Purely a presentation-layer
 * concern: Room/Item still store LocalDate as ISO under the hood (see
 * data/Converters.kt), and Sprint 4's OCR date parsing works off the raw
 * text it finds on receipts, not this formatter. This is only ever used to
 * render a LocalDate for display, and (via the DatePicker introduced here)
 * to render the picker's selection — there is no free-text date entry left
 * to parse, which removes the "typo'd the date format" bug class entirely.
 */
private val INDIAN_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

fun LocalDate.toIndianDateString(): String = format(INDIAN_DATE_FORMATTER)

fun LocalDate?.toIndianDateStringOrDash(): String = this?.toIndianDateString() ?: "—"

/**
 * DatePickerState works in UTC epoch millis at midnight, not the device's
 * local zone — converting through the system default zone instead would
 * occasionally shift the selected date by a day depending on the user's
 * timezone offset at that moment. Always go through UTC, both directions.
 */
fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun Long.toLocalDateFromUtcMillis(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
