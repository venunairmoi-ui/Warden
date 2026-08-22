package com.venunair.warden.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Three-tier "how urgent is this" signal used consistently on Home and
 * Detail. Deliberately NOT reusing colorScheme.error for every close-to-
 * expiry item — error is a Material3 semantic role meant for actual failure/
 * validation states (see AddEditItemScreen's field validation), and
 * overloading it for "12 days left" would blur that meaning. It IS correct
 * for a genuinely overdue item, though — that's a failure state.
 */
enum class ItemUrgency { OVERDUE, SOON, COMFORTABLE }

fun urgencyOf(expiryDate: LocalDate, today: LocalDate = LocalDate.now()): ItemUrgency {
    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)
    return when {
        daysLeft <= 0 -> ItemUrgency.OVERDUE
        daysLeft <= 30 -> ItemUrgency.SOON
        else -> ItemUrgency.COMFORTABLE
    }
}

@Composable
fun ItemUrgency.color(): Color = when (this) {
    ItemUrgency.OVERDUE -> MaterialTheme.colorScheme.error
    ItemUrgency.SOON -> if (isSystemInDarkTheme()) WardenWarningDark else WardenWarning
    ItemUrgency.COMFORTABLE -> MaterialTheme.colorScheme.tertiary
}
