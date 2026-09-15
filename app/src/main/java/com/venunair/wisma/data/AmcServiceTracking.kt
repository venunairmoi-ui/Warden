package com.venunair.wisma.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * AMC-only, feedback 2026-08-26: how many of the contract's included
 * services have been used so far this period, and when the next one is
 * expected. Deliberately computed on the fly from [Item] + its
 * [ServiceEvent] history rather than stored as its own counter --
 * "used" is just a count of events on/after [Item.currentPeriodStart],
 * so it can never drift from the event log the way a separately-
 * maintained counter could (the same anti-drift reasoning behind
 * [Item.isRecurringPayment] being derived rather than a stored flag).
 *
 * This is exactly the tracking that couldn't be built safely when
 * [Item.visitsIncluded] was first added, because the old markServiced()
 * conflated "log a visit" with "renew the contract" and left no period
 * boundary to count against. [Item.currentPeriodStart] is that boundary.
 */
data class AmcServiceStatus(
    val used: Int,
    val total: Int,
    val remaining: Int,
    val nextExpectedDate: LocalDate?
)

/**
 * Returns null when the item isn't a trackable AMC yet -- no
 * [Item.visitsIncluded] set, or no [Item.currentPeriodStart] (an AMC
 * item saved before this feature existed and not yet re-saved to pick
 * up the migration/auto-fill).
 */
fun computeAmcServiceStatus(item: Item, events: List<ServiceEvent>): AmcServiceStatus? {
    val total = item.visitsIncluded?.takeIf { it > 0 } ?: return null
    val periodStart = item.currentPeriodStart ?: return null
    val eventsThisPeriod = events.filter { !it.date.isBefore(periodStart) }
    val used = eventsThisPeriod.size
    val remaining = (total - used).coerceAtLeast(0)
    val intervalMonths = item.serviceIntervalMonths
    val nextExpectedDate = if (remaining <= 0 || intervalMonths == null) {
        null
    } else {
        val anchor = eventsThisPeriod.maxOfOrNull { it.date } ?: periodStart
        anchor.plusMonths(intervalMonths.toLong())
    }
    return AmcServiceStatus(used, total, remaining, nextExpectedDate)
}

/**
 * Sensible starting point for [Item.serviceIntervalMonths] -- period
 * length (in whole months) divided evenly by the number of included
 * visits, floored, minimum 1 (feedback, 2026-08-26: "divide the period
 * by the number of services... and spread them evenly" -- a 12-month/
 * 2-visit contract expects services ~6 months apart, a 24-month/4-visit
 * contract also ~6 months apart). Used once to seed the value when it's
 * blank on save -- never re-run over a value the user already set, since
 * they explicitly asked for this to stay configurable.
 */
fun defaultServiceIntervalMonths(periodStart: LocalDate, expiryDate: LocalDate, visitsIncluded: Int): Int? {
    if (visitsIncluded <= 0) return null
    val periodMonths = ChronoUnit.MONTHS.between(periodStart, expiryDate)
    if (periodMonths <= 0) return null
    return (periodMonths / visitsIncluded).toInt().coerceAtLeast(1)
}
