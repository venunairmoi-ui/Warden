package com.venunair.wisma.data

import java.time.LocalDate

/**
 * Dev/testing aid. Was wired to a "Load sample data" debug menu item,
 * removed 2026-08-25 (that day's feedback), kept as a callable function in
 * case it was reconnected later -- it was: re-added 2026-09-01 as "Add test
 * records" in OverviewScreen's debug menu ("The check reminders and Add
 * test records are both missing. It needs to be built back"), and that
 * whole menu is now shown unconditionally (not just in debug builds) so it
 * reaches the same signed release APK testers install. Updated here purely
 * to stay compilable against the retaxonomy (was WARRANTY/AMC/SUBSCRIPTION/
 * DOCUMENT/VEHICLE/HOME/OFFICE/FINANCIAL; now WARRANTY/INSURANCE/
 * SUBSCRIPTION/AMC/MEMBERSHIP/OTHER, each with its own subCategory).
 *
 * Inserts realistic records spanning every ItemCategory (including OTHER,
 * standing in for a pre-retaxonomy migrated item) and every urgency band
 * (expired, due tomorrow, due this week, due this month, comfortably in
 * the future) so there's always real data on the device to exercise
 * urgency-color, reminder, and billing logic.
 *
 * itemType isn't set by hand per entry -- defaulted below from each
 * item's own category via ItemCategory.defaultItemType(), same starting
 * point AddEditItemScreen's dropdown uses. (Setting it by hand per entry
 * here is exactly the kind of independently-maintained duplicate that
 * caused the Insurance/Membership entries in an earlier version of this
 * file to have billing data attached but itemType left at its PRODUCT
 * default -- see ItemType's doc comment.)
 *
 * Goes through the exact same ItemRepository.saveItem() every real Add
 * does -- these are ordinary items with real seeded ReminderRule rows, not
 * a special-cased shortcut -- so they behave identically to anything
 * typed in by hand.
 */
suspend fun ItemRepository.seedSampleData() {
    val today = LocalDate.now()
    val samples = listOf(
        // ── Products (WARRANTY category) ─────────────────────────────
        Item(
            name = "LG Double-Door Refrigerator",
            vendor = "LG Electronics",
            category = ItemCategory.WARRANTY,
            subCategory = "Kitchen Appliances",
            purchaseDate = today.minusYears(2),
            expiryDate = today.plusDays(40),
            cost = 42000.0,
            serialNumber = "LG-REF-202408-7741",
            modelNumber = "GL-T292RPZX",
            retailer = "Croma, Indiranagar",
            invoiceNumber = "CRM-BLR-88432",
            warrantyType = WarrantyType.EXTENDED,
            location = "Home",
            notes = "Extended warranty card is in the box with the manual."
        ),
        Item(
            name = "OnePlus 12 Warranty",
            vendor = "OnePlus India",
            category = ItemCategory.WARRANTY,
            subCategory = "Electronics",
            purchaseDate = today.minusDays(65),
            expiryDate = today.plusDays(300),
            cost = 64999.0,
            serialNumber = "OP12-IND-93827461",
            modelNumber = "CPH2583",
            retailer = "Amazon.in",
            invoiceNumber = "AMZ-408-2847193",
            warrantyType = WarrantyType.MANUFACTURER,
            location = "Home",
            notes = "1-year manufacturer warranty plus a 6-month extension."
        ),

        // ── Service contracts (AMC category) ─────────────────────────
        Item(
            name = "Whirlpool Washing Machine AMC",
            vendor = "Whirlpool Service",
            category = ItemCategory.AMC,
            subCategory = "Kitchen Appliances",
            purchaseDate = today.minusYears(1),
            expiryDate = today.plusDays(5),
            cost = 3500.0,
            amcNumber = "WHR-AMC-88213",
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 3500.0,
            visitsIncluded = 2,
            serviceProviderContact = "Rajesh (technician) 98450-XXXXX",
            location = "Home",
            notes = "Annual comprehensive AMC, includes 2 free service visits."
        ),
        Item(
            name = "RO Water Purifier AMC",
            vendor = "Kent Service",
            category = ItemCategory.AMC,
            subCategory = "RO/Purifier",
            purchaseDate = today.minusMonths(6),
            expiryDate = today.plusMonths(6),
            cost = 2400.0,
            amcNumber = "KENT-SVC-91234",
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 2400.0,
            visitsIncluded = 4,
            location = "Kitchen"
        ),

        // ── Subscriptions (SUBSCRIPTION category) ────────────────────
        Item(
            name = "Netflix Premium",
            vendor = "Netflix",
            category = ItemCategory.SUBSCRIPTION,
            subCategory = "Entertainment",
            expiryDate = today.plusDays(1),
            cost = 649.0,
            billingCycle = BillingCycle.MONTHLY,
            billingAmount = 649.0,
            autoRenew = true,
            notes = "Auto-renews unless cancelled."
        ),
        Item(
            name = "Microsoft 365",
            vendor = "Microsoft",
            category = ItemCategory.SUBSCRIPTION,
            subCategory = "Software",
            expiryDate = today.plusDays(18),
            cost = 4899.0,
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 4899.0,
            autoRenew = true,
            planTier = "Family (6 users)"
        ),

        // ── Insurance (new category) ─────────────────────────────────
        Item(
            name = "Honda City Motor Insurance",
            vendor = "HDFC ERGO",
            category = ItemCategory.INSURANCE,
            subCategory = "Vehicle",
            purchaseDate = today.minusYears(1),
            expiryDate = today.minusDays(3),
            cost = 18500.0,
            amcNumber = "POL-2024-556621",
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 18500.0,
            notes = "Renew before the grace period ends."
        ),
        Item(
            name = "Term Life Insurance",
            vendor = "ICICI Prudential",
            category = ItemCategory.INSURANCE,
            subCategory = "Health",
            purchaseDate = today.minusYears(2),
            expiryDate = today.plusYears(18),
            cost = 10000000.0,
            amcNumber = "ICIPRU-TERM-2024-88312",
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 14200.0,
            autoRenew = true,
            nomineeName = "Spouse",
            notes = "Sum assured ₹1 Cr. Premium due every July."
        ),

        // ── Membership (new category) ────────────────────────────────
        Item(
            name = "Cult.fit Gym Membership",
            vendor = "Cult.fit",
            category = ItemCategory.MEMBERSHIP,
            subCategory = "Fitness",
            expiryDate = today.plusDays(25),
            cost = 12000.0,
            billingCycle = BillingCycle.ANNUAL,
            billingAmount = 12000.0,
            autoRenew = true,
            planTier = "Gold",
            membersCovered = 2,
            location = "Home"
        ),

        // ── Other (retaxonomy migration bucket) ──────────────────────
        Item(
            name = "HP LaserJet Warranty",
            vendor = "HP India",
            // Stand-in for a pre-retaxonomy item whose old category
            // (Office) no longer exists -- see WardenDatabase's
            // MIGRATION_5_6. Left uncategorized-by-subcategory
            // deliberately: OTHER has no subcategory list.
            category = ItemCategory.OTHER,
            purchaseDate = today.minusMonths(8),
            expiryDate = today.plusMonths(4),
            cost = 22990.0,
            serialNumber = "CND4HP2K91",
            modelNumber = "LaserJet Pro M404dn",
            retailer = "Amazon.in",
            invoiceNumber = "AMZ-408-7723901",
            location = "Office"
        ),
    )
    samples
        .map { it.copy(itemType = it.category.defaultItemType()) }
        .forEach { saveItem(it) }
}
