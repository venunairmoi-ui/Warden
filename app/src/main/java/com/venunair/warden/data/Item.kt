package com.venunair.warden.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class ItemCategory {
    WARRANTY, AMC, SUBSCRIPTION, DOCUMENT,
    VEHICLE, HOME, OFFICE, FINANCIAL;

    /** Human-readable label for UI display (replaces raw enum .name). */
    val displayName: String
        get() = when (this) {
            WARRANTY -> "Warranty"
            AMC -> "Service Contract"
            SUBSCRIPTION -> "Subscription"
            DOCUMENT -> "Document"
            VEHICLE -> "Vehicle"
            HOME -> "Home"
            OFFICE -> "Office"
            FINANCIAL -> "Financial"
        }
}

enum class ItemStatus { ACTIVE, EXPIRED, ARCHIVED }

/** What kind of thing the user is tracking — drives conditional form fields. */
enum class ItemType {
    PRODUCT, SUBSCRIPTION, SERVICE_CONTRACT;

    val displayName: String
        get() = when (this) {
            PRODUCT -> "Product"
            SUBSCRIPTION -> "Subscription"
            SERVICE_CONTRACT -> "Service Contract"
        }
}

/** Billing recurrence for subscriptions and recurring service contracts. */
enum class BillingCycle {
    MONTHLY, QUARTERLY, HALF_YEARLY, ANNUAL, ONE_TIME;

    val displayName: String
        get() = when (this) {
            MONTHLY -> "Monthly"
            QUARTERLY -> "Quarterly"
            HALF_YEARLY -> "Half-yearly"
            ANNUAL -> "Annual"
            ONE_TIME -> "One-time"
        }

    /** Normalise any billing amount to a monthly equivalent for dashboard summaries. */
    fun toMonthlyFactor(): Double = when (this) {
        MONTHLY -> 1.0
        QUARTERLY -> 1.0 / 3.0
        HALF_YEARLY -> 1.0 / 6.0
        ANNUAL -> 1.0 / 12.0
        ONE_TIME -> 0.0
    }
}

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val vendor: String? = null,
    val category: ItemCategory,
    val purchaseDate: LocalDate? = null,
    val expiryDate: LocalDate,
    val cost: Double? = null,
    // Policy/contract/service-request number — mainly relevant for AMC and
    // WARRANTY items, but kept general rather than AMC-only since vendors
    // aren't consistent about what they call it (warranty card number,
    // service agreement no., etc.).
    val amcNumber: String? = null,
    val notes: String? = null,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val createdAt: LocalDate = LocalDate.now(),

    // --- Sprint 6 additions (Room v4 → v5 migration) ---

    /** What kind of tracked item: Product, Subscription, or Service Contract. */
    val itemType: ItemType = ItemType.PRODUCT,

    /** Manufacturer or product serial number (from invoice/label). */
    val serialNumber: String? = null,

    /** Manufacturer model identifier. */
    val modelNumber: String? = null,

    /** Where the item was purchased (store name or online retailer). */
    val retailer: String? = null,

    /** Invoice or receipt reference number. */
    val invoiceNumber: String? = null,

    /** Physical location of the item (e.g. "Home", "Office", "Mom's house"). */
    val location: String? = null,

    /** How often the subscription or service contract is billed. */
    val billingCycle: BillingCycle? = null,

    /** Recurring billing amount per cycle. */
    val billingAmount: Double? = null,

    /** Whether this subscription auto-renews. */
    val autoRenew: Boolean = false,
)
