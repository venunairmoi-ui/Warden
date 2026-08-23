package com.venunair.warden.data

import java.time.LocalDate

/**
 * Dev/testing aid, wired to a button on HomeScreen's TopAppBar -- same
 * "remove or gate behind a debug build check before Play Store" caveat as
 * that screen's existing "check reminders now" action; neither has a place
 * in a shipped UI.
 *
 * Inserts realistic records spanning every ItemCategory, every ItemType,
 * and every urgency band (expired, due tomorrow, due this week, due this
 * month, comfortably in the future) so there's always real data on the
 * device to exercise urgency-color, reminder, and billing logic.
 *
 * Goes through the exact same ItemRepository.saveItem() every real Add
 * does -- these are ordinary items with real seeded ReminderRule rows, not
 * a special-cased shortcut -- so they behave identically to anything
 * typed in by hand.
 */
suspend fun ItemRepository.seedSampleData() {
    val today = LocalDate.now()
    val samples = listOf(
        // ── Products (WARRANTY category, PRODUCT type) ──────────────
        Item(
            name = "LG Double-Door Refrigerator",
            vendor = "LG Electronics",
            category = ItemCategory.WARRANTY,
            itemType = ItemType.PRODUCT,
            purchaseDate = today.minusYears(2),
            expiryDate = today.plusDays(40),
            cost = 42000.0,
            serialNumber = "LG-REF-202408-7741",
            modelNumber = "GL-T292RPZX",
            retailer = "Croma, Indiranagar",
            invoiceNumber = "CRM-BLR-88432",
            location = "Home",
            notes = "Extended warranty card is in the box with the manual."
        ),
        Item(
            name = "OnePlus 12 Warranty",
            vendor = "OnePlus India",
            category = ItemCategory.WARRANTY,
            itemType = ItemType.PRODUCT,
            purchaseDate = today.minusDays(65),
            expiryDate = today.plusDays(300),
            cost = 64999.0,
            serialNumber = "OP12-IND-93827461",
            modelNumber = "CPH2583",
            retailer = "Amazon.in",
            invoiceNumber = "AMZ-408-2847193",
            location = "Home",
            notes = "1-year manufacturer warranty plus a 6-month extension."
        ),

        // ── Service contracts (AMC category, SERVICE_CONTRACT type) ─
        Item(
            name = "Whirlpool Washing Machine AMC",
            vendor = "Whirlpool Service",
            category = ItemCategory.AMC,
            itemType = ItemType.SERVICE_CONTRACT,
            purchaseDate = today.minusYears(1),
            expiryDate = today.plusDays(5),
            cost = 3500.0,
            amcNumber = "WHR-AMC-88213",
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 3500.0,
            location = "Home",
            notes = "Annual comprehensive AMC, includes 2 free service visits."
        ),
        Item(
            name = "Society Lift AMC",
            vendor = "Otis Elevators",
            category = ItemCategory.AMC,
            itemType = ItemType.SERVICE_CONTRACT,
            purchaseDate = today.minusMonths(11),
            expiryDate = today.plusDays(25),
            cost = 12000.0,
            amcNumber = "OTIS-LFT-4471",
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 12000.0,
            location = "Home"
        ),

        // ── Subscriptions (SUBSCRIPTION category, SUBSCRIPTION type) ─
        Item(
            name = "Netflix Premium",
            vendor = "Netflix",
            category = ItemCategory.SUBSCRIPTION,
            itemType = ItemType.SUBSCRIPTION,
            expiryDate = today.plusDays(1),
            cost = 649.0,
            billingCycle = BillingCycle.MONTHLY,
            billingAmount = 649.0,
            autoRenew = true,
            notes = "Auto-renews unless cancelled."
        ),
        Item(
            name = "YouTube Premium Family",
            vendor = "Google",
            category = ItemCategory.SUBSCRIPTION,
            itemType = ItemType.SUBSCRIPTION,
            expiryDate = today.plusDays(18),
            cost = 189.0,
            billingCycle = BillingCycle.MONTHLY,
            billingAmount = 189.0,
            autoRenew = true
        ),

        // ── Documents (DOCUMENT category) ───────────────────────────
        Item(
            name = "Home Insurance Policy",
            vendor = "HDFC ERGO",
            category = ItemCategory.DOCUMENT,
            purchaseDate = today.minusYears(1),
            expiryDate = today.minusDays(3),
            cost = 8200.0,
            amcNumber = "POL-2024-556621",
            notes = "Renew before the grace period ends."
        ),

        // ── Vehicle (new Sprint 6 category) ─────────────────────────
        Item(
            name = "Honda City Extended Warranty",
            vendor = "Honda Cars India",
            category = ItemCategory.VEHICLE,
            itemType = ItemType.PRODUCT,
            purchaseDate = today.minusYears(3),
            expiryDate = today.plusDays(90),
            cost = 18500.0,
            serialNumber = "MA3GK2E58P0123456",
            modelNumber = "City ZX CVT 2023",
            retailer = "Sundaram Honda, Whitefield",
            invoiceNumber = "SH-BLR-2023-4412",
            location = "Home",
            notes = "VIN is the serial number. Dashboard booklet has service coupons."
        ),

        // ── Home (new Sprint 6 category) ────────────────────────────
        Item(
            name = "RO Water Purifier AMC",
            vendor = "Kent Service",
            category = ItemCategory.HOME,
            itemType = ItemType.SERVICE_CONTRACT,
            purchaseDate = today.minusMonths(6),
            expiryDate = today.plusMonths(6),
            cost = 2400.0,
            amcNumber = "KENT-SVC-91234",
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 2400.0,
            location = "Kitchen"
        ),

        // ── Office (new Sprint 6 category) ──────────────────────────
        Item(
            name = "HP LaserJet Warranty",
            vendor = "HP India",
            category = ItemCategory.OFFICE,
            itemType = ItemType.PRODUCT,
            purchaseDate = today.minusMonths(8),
            expiryDate = today.plusMonths(4),
            cost = 22990.0,
            serialNumber = "CND4HP2K91",
            modelNumber = "LaserJet Pro M404dn",
            retailer = "Amazon.in",
            invoiceNumber = "AMZ-408-7723901",
            location = "Office"
        ),

        // ── Financial (new Sprint 6 category) ───────────────────────
        Item(
            name = "Term Life Insurance",
            vendor = "ICICI Prudential",
            category = ItemCategory.FINANCIAL,
            purchaseDate = today.minusYears(2),
            expiryDate = today.plusYears(18),
            cost = 14200.0,
            amcNumber = "ICIPRU-TERM-2024-88312",
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 14200.0,
            autoRenew = true,
            notes = "Sum assured ₹1 Cr. Premium due every July."
        ),
    )
    samples.forEach { saveItem(it) }
}
