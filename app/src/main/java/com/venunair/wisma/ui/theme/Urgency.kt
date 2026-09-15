package com.venunair.wisma.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Three-tier "how urgent is this" signal used consistently on Home and
 * Detail. Deliberately NOT reusing colorScheme.error for a genuinely
 * overdue item either (see [color] below) — error is a Material3 semantic
 * role meant for actual failure/validation states (AddEditItemScreen's
 * field validation), and its dark-theme tone is deliberately desaturated
 * for that role, which read as washed-out on this app's own Expired
 * accent bar/tile. OVERDUE gets its own dedicated colors instead, same as
 * SOON and COMFORTABLE already do.
 */
enum class ItemUrgency { OVERDUE, SOON, COMFORTABLE }

/**
 * Bug fix, 2026-08-26: was `daysLeft <= 0 -> OVERDUE`, which classified an
 * item due exactly today as overdue/expired -- red accent bar, "EXPIRED"
 * status pill -- even though the footer/Hero-card text right next to it
 * already special-cased daysLeft == 0 as "Due today" / "DUE TODAY", a
 * separate, correct notion of "not yet expired" that the color and label
 * simply disagreed with. Due today now falls in the SOON tier (0..30),
 * matching HomeViewModel's QuickFilter.DUE_SOON and OverviewScreen's Due
 * soon tile, which use this same boundary.
 */
fun urgencyOf(expiryDate: LocalDate, today: LocalDate = LocalDate.now()): ItemUrgency {
    val daysLeft = ChronoUnit.DAYS.between(today, expiryDate)
    return when {
        daysLeft < 0 -> ItemUrgency.OVERDUE
        daysLeft <= 30 -> ItemUrgency.SOON
        else -> ItemUrgency.COMFORTABLE
    }
}

@Composable
fun ItemUrgency.color(): Color = when (this) {
    ItemUrgency.OVERDUE -> if (isSystemInDarkTheme()) WardenOverdueDark else WardenOverdue
    ItemUrgency.SOON -> if (isSystemInDarkTheme()) WardenWarningDark else WardenWarning
    // Feedback, 2026-08-25: was colorScheme.tertiary (blue-lavender) --
    // green reads unambiguously as "safe/active" the way red/amber already
    // do for the other two tiers, and this is a single shared extension
    // used everywhere urgency is tinted (Dashboard accent bars, footer
    // text, stat tile, Detail's Hero card), so the change is consistent
    // app-wide rather than a one-screen override.
    ItemUrgency.COMFORTABLE -> if (isSystemInDarkTheme()) WardenSuccessDark else WardenSuccess
}
