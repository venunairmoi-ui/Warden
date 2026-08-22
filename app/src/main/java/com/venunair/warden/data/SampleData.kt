package com.venunair.warden.data

import java.time.LocalDate

/**
 * Dev/testing aid, wired to a button on HomeScreen's TopAppBar -- same
 * "remove or gate behind a debug build check before Play Store" caveat as
 * that screen's existing "check reminders now" action; neither has a place
 * in a shipped UI.
 *
 * Inserts a handful of realistic warranty/AMC/subscription/document
 * records spanning every ItemCategory and every urgency band (expired, due
 * tomorrow, due this week, due this month, comfortably in the future) so
 * there's always real data on the device to click through -- open an item,
 * edit it, attach a photo/PDF, mark it serviced, delete it -- and actually
 * see the urgency-color and reminder-threshold logic exercised, instead of
 * testing every flow against an empty list.
 *
 * Goes through the exact same ItemRepository.saveItem() every real Add
 * does -- these are ordinary items with real seeded ReminderRule rows, not
 * a special-cased shortcut -- so they're indistinguishable from, and behave
 * identically to, anything typed in by hand.
 */
suspend fun ItemRepository.seedSampleData() {
    val today = LocalDate.now()
    val samples = listOf(
        Item(
            id = 0,
            name = "LG Double-Door Refrigerator",
            vendor = "LG Electronics",
            category = ItemCategory.WARRANTY,
            purchaseDate = today.minusYears(2),
            expiryDate = today.plusDays(40),
            cost = 42000.0,
            notes = "Extended warranty card is in the box with the manual."
        ),
        Item(
            id = 0,
            name = "Whirlpool Washing Machine AMC",
            vendor = "Whirlpool Service",
            category = ItemCategory.AMC,
            purchaseDate = today.minusYears(1),
            expiryDate = today.plusDays(5),
            cost = 3500.0,
            amcNumber = "WHR-AMC-88213",
            notes = "Annual comprehensive AMC, includes 2 free service visits."
        ),
        Item(
            id = 0,
            name = "Netflix Premium",
            vendor = "Netflix",
            category = ItemCategory.SUBSCRIPTION,
            expiryDate = today.plusDays(1),
            cost = 649.0,
            notes = "Auto-renews unless cancelled."
        ),
        Item(
            id = 0,
            name = "Home Insurance Policy",
            vendor = "HDFC ERGO",
            category = ItemCategory.DOCUMENT,
            purchaseDate = today.minusYears(1),
            expiryDate = today.minusDays(3),
            cost = 8200.0,
            amcNumber = "POL-2024-556621",
            notes = "Renew before the grace period ends."
        ),
        Item(
            id = 0,
            name = "OnePlus 12 Warranty",
            vendor = "OnePlus India",
            category = ItemCategory.WARRANTY,
            purchaseDate = today.minusDays(65),
            expiryDate = today.plusDays(300),
            cost = 64999.0,
            notes = "1-year manufacturer warranty plus a 6-month extension."
        ),
        Item(
            id = 0,
            name = "Society Lift AMC",
            vendor = "Otis Elevators",
            category = ItemCategory.AMC,
            purchaseDate = today.minusMonths(11),
            expiryDate = today.plusDays(25),
            cost = 12000.0,
            amcNumber = "OTIS-LFT-4471"
        )
    )
    samples.forEach { saveItem(it) }
}
