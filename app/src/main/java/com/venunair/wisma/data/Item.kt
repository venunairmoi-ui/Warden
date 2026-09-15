package com.venunair.wisma.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

// Retaxonomy, 2026-08-25: was WARRANTY/AMC/SUBSCRIPTION/DOCUMENT/VEHICLE/
// HOME/OFFICE/FINANCIAL. Replaced with the 5 categories below plus OTHER,
// a catch-all migration bucket that only exists to receive whatever an
// existing item's old (now-deleted) category doesn't map onto one of the
// 5 -- see WardenDatabase's MIGRATION_5_6. OTHER stays a normal, pickable
// category rather than a hidden one specifically so those migrated items
// are easy to find (filter chip) and manually recategorize.
enum class ItemCategory {
    WARRANTY, INSURANCE, SUBSCRIPTION, AMC, MEMBERSHIP, OTHER;

    /** Human-readable label for UI display (replaces raw enum .name). */
    val displayName: String
        get() = when (this) {
            WARRANTY -> "Warranty"
            INSURANCE -> "Insurance"
            SUBSCRIPTION -> "Subscription"
            // Retaxonomy, 2026-08-25: was "Service Contract", which read as
            // identical text to ItemType.SERVICE_CONTRACT's own displayName
            // in a different dropdown on the same form -- two unrelated
            // fields showing the same words. Plain "AMC" here removes the
            // collision; ItemType is untouched.
            AMC -> "AMC"
            MEMBERSHIP -> "Membership"
            OTHER -> "Other"
        }
}

/**
 * Retaxonomy, 2026-08-25: fixed subcategory options per [ItemCategory],
 * shown as a second, category-dependent dropdown in the Add/Edit form
 * (see AddEditItemScreen's subcategory ExposedDropdownMenuBox) and stored
 * as plain text on [Item.subCategory] -- not its own enum, since a display
 * label is all a subcategory value is ever used for (no branching logic
 * reads it), and a plain string keeps adding/renaming a subcategory a
 * one-line change here with no Room migration, matching how [Item.location]
 * (also free text) already works in this codebase. OTHER has no
 * subcategories -- it's a temporary bucket for items whose pre-retaxonomy
 * category no longer exists, not a real category people file new items
 * under, so the Add/Edit form skips the subcategory field entirely for it.
 */
fun subcategoriesFor(category: ItemCategory): List<String> = when (category) {
    ItemCategory.WARRANTY -> listOf("Electronics", "Kitchen Appliances", "Home Improvement", "Others")
    ItemCategory.INSURANCE -> listOf("Vehicle", "Property", "Luxury Items", "Electronics", "Health", "Others")
    ItemCategory.SUBSCRIPTION -> listOf("Entertainment", "Software", "Smart Homes", "Others")
    ItemCategory.AMC -> listOf("RO/Purifier", "Electronics", "Kitchen Appliances", "Others")
    ItemCategory.MEMBERSHIP -> listOf("Fitness", "Sports", "Travel", "Hotel", "Shopping", "Others")
    ItemCategory.OTHER -> emptyList()
}

enum class ItemStatus { ACTIVE, EXPIRED, ARCHIVED }

/**
 * Whether there's a physical thing behind this record, or it's purely a
 * service/contract with nothing to serial-number. Independent of
 * [ItemCategory] deliberately -- unlike the old 3-value ItemType this
 * replaces (PRODUCT/SUBSCRIPTION/SERVICE_CONTRACT, removed 2026-08-25
 * for being a near-restatement of Category), Product-vs-Service is a
 * genuinely different axis that varies *within* a category: a fridge
 * warranty (Product) vs. a workmanship warranty on a renovation
 * (Service); an RO-purifier AMC (Product) vs. a housekeeping AMC (pure
 * Service, nothing to serial-number); a subscription box (Product) vs.
 * Netflix (Service). Drives which optional field-set shows on Add/Edit
 * (Product details: serial/model/retailer/invoice) -- see
 * AddEditItemScreen. Always a real, user-editable choice (defaulted from
 * category via [ItemCategory.defaultItemType] but never silently
 * re-derived after that), unlike the old field, so it can't go invisible
 * and can't drift the way the old one did.
 */
enum class ItemType {
    PRODUCT, SERVICE;

    val displayName: String
        get() = when (this) {
            PRODUCT -> "Product"
            SERVICE -> "Service"
        }
}

/**
 * Sensible starting point for [ItemType] when a category is picked --
 * used once to seed the Add/Edit dropdown's initial value, not to force
 * or silently re-derive it afterwards (see ItemType's doc comment).
 * Reflects the *typical* case per category; the user can always pick
 * the other value for the exceptions (a workmanship warranty, a
 * housekeeping AMC, a subscription box).
 */
fun ItemCategory.defaultItemType(): ItemType = when (this) {
    ItemCategory.WARRANTY -> ItemType.PRODUCT
    ItemCategory.AMC -> ItemType.SERVICE
    ItemCategory.SUBSCRIPTION -> ItemType.SERVICE
    ItemCategory.INSURANCE -> ItemType.SERVICE
    ItemCategory.MEMBERSHIP -> ItemType.SERVICE
    ItemCategory.OTHER -> ItemType.PRODUCT
}

/**
 * Category-aware label for [Item.amcNumber] -- same underlying field
 * reused across categories rather than adding a new column per category
 * (Insurance's "policy number" and AMC's "contract number" are the same
 * kind of fact -- a reference number for a document/contract -- just
 * called different things), matching how [costLabel] below handles the
 * cost field. Used both as the Add/Edit form field label and, trimmed
 * of its "(optional)" suffix by the caller, as the read-only detail-row
 * label.
 */
fun ItemCategory.referenceNumberLabel(): String = when (this) {
    ItemCategory.AMC -> "Contract number"
    ItemCategory.INSURANCE -> "Policy number"
    // Category-specific fields pass, 2026-08-30: these three used to share
    // one generic "AMC / warranty / policy number" label with each other
    // (and with AMC/Insurance, before those two got their own line above) --
    // the exact "same field, generic label" complaint this pass is fixing
    // elsewhere. Each now reads as what it actually is.
    ItemCategory.WARRANTY -> "Warranty number"
    ItemCategory.SUBSCRIPTION -> "Subscription / account ID"
    ItemCategory.MEMBERSHIP -> "Membership number"
    ItemCategory.OTHER -> "Reference number"
}

/**
 * Category-aware label for [Item.cost]. Insurance's "cost" is actually
 * the sum insured / coverage amount, not a purchase price -- a real
 * label mismatch, not just cosmetic, since someone reading their own
 * data back later could otherwise misread a coverage amount as what
 * they paid. Reuses the existing generic field rather than adding an
 * Insurance-only column, same reasoning as [referenceNumberLabel].
 *
 * Feedback, 2026-09-01: "When I add an item under AMC, I see a cost and
 * a billing amount per cycle... What is the cost? If that is the
 * purchase price, label it as such." Every category except Insurance
 * used to share one generic "Cost" label here, with no hint of what it
 * meant -- confusing specifically wherever a category ALSO shows the
 * Billing section (AMC, Subscription, Membership, Other all do; see
 * AddEditItemScreen's showBilling), since two unlabelled money fields
 * sit right next to each other. This field genuinely IS a purchase
 * price everywhere it isn't Insurance or Membership -- confirmed by
 * ItemDetailScreen's own TCO section, which already calls this exact
 * field "Purchase price" (purchaseCost = item.cost) regardless of
 * category; this label was simply out of sync with that. AMC's case
 * specifically: it's the purchase price of the item UNDER the AMC
 * contract (e.g. the AC or RO purifier), not the AMC contract's own
 * cost -- that's Billing amount per cycle, a separate field entirely.
 * Membership gets its own wording ("Enrollment / joining fee") rather
 * than "Purchase price" since nobody "purchases" a club membership --
 * same underlying column, clearer name for what it represents there.
 */
fun ItemCategory.costLabel(): String = when (this) {
    ItemCategory.INSURANCE -> "Sum insured / coverage amount"
    ItemCategory.MEMBERSHIP -> "Enrollment / joining fee"
    ItemCategory.WARRANTY, ItemCategory.SUBSCRIPTION, ItemCategory.AMC, ItemCategory.OTHER -> "Purchase price"
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

    /** Whether there's a physical thing here, or it's a pure service/contract. */
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

    // --- Retaxonomy addition (Room v5 → v6 migration) ---

    /**
     * Category-dependent subcategory label (see [subcategoriesFor]), e.g.
     * "Electronics" under Warranty. Free text rather than its own enum --
     * see [subcategoriesFor]'s doc comment. Nullable/optional: existing
     * items upgrade with no value rather than being forced into "Others",
     * and the Add/Edit form doesn't require picking one to save.
     */
    val subCategory: String? = null,

    // --- Product/Service reintroduction addition (Room v6 → v7 migration) ---

    /**
     * AMC-only: the number of service visits the contract entitles you
     * to (e.g. "2 free visits/year") -- a fact about the contract itself,
     * not a derived stat. Deliberately NOT paired with a "visits used"
     * counter: every "Mark serviced / renewed" call both logs a
     * ServiceEvent AND pushes expiryDate forward (see
     * ItemRepository.markServiced), so there's no data boundary marking
     * where one contract period's usage ends and the next begins --
     * counting ServiceEvent rows would either total every visit ever (
     * wrong the moment the contract renews) or require inventing a period
     * boundary the app doesn't actually track. Shown only for AMC items;
     * null elsewhere.
     */
    val visitsIncluded: Int? = null,

    // --- Archive recovery addition (Room v7 → v8 migration) ---

    /**
     * When this item was archived (see ItemRepository.archiveItem) --
     * null for anything not currently archived, cleared back to null on
     * unarchive. Added, 2026-08-26, alongside the Archived items recovery
     * screen (Settings > Archived items). Before that screen existed,
     * archiving was effectively one-way once the 5-second "Undo" snackbar
     * (HomeScreen's SwipeableItemRow) was missed -- observeActiveItems and
     * searchByNameOrVendor both filter on status != ARCHIVED, so an
     * archived item vanished from every list, the Overview, and search,
     * with no other screen showing it and no way back short of finding
     * the row directly in the database. This field exists purely so that
     * recovery screen can sort by "what did I just archive" (recency)
     * rather than an arbitrary order -- not read by anything else.
     */
    val archivedAt: LocalDate? = null,

    // --- AMC service-visit tracking addition (Room v8 → v9 migration) ---

    /**
     * AMC-only: when the current contract period began -- the anchor
     * [ServiceEvent] rows are counted from/against to derive "services
     * used this period" (see computeAmcServiceStatus in
     * AmcServiceTracking.kt). Set once at creation (from purchaseDate, or
     * today if that's unknown) and reset to today by
     * ItemRepository.applyAmcPeriodTracking whenever an AMC item's
     * expiryDate is edited to a LATER date -- that's how a renewal is
     * detected (feedback, 2026-08-26: "reset automatically" was the
     * requested behaviour over a confirm-first prompt). This field is
     * what makes it safe to split "log a visit" from "renew" -- the old
     * single markServiced() action conflated the two with no period
     * boundary to count against; see [ItemRepository.logAmcService]'s
     * doc comment.
     */
    val currentPeriodStart: LocalDate? = null,

    /**
     * AMC-only: expected number of months between services, used to
     * compute "next service expected around" (period ÷ visitsIncluded,
     * evenly spread -- feedback, 2026-08-26: "divide the period by the
     * number of services... and spread them evenly", e.g. a 12-month/
     * 2-visit contract expects services ~6 months apart, a 24-month/
     * 4-visit contract also ~6 months apart). Auto-filled once from
     * visitsIncluded and the contract length whenever this is blank on
     * save (see ItemRepository.applyAmcPeriodTracking), but always
     * user-editable afterward and never silently recomputed over a value
     * already set -- the user explicitly asked for this to stay
     * configurable rather than fixed.
     */
    val serviceIntervalMonths: Int? = null,

    /**
     * AMC-only, not user-facing: the "next expected service" date the app
     * already sent a due-service nudge for, so ReminderCheckWorker
     * doesn't repeat the same nudge every day it stays true. Self-
     * resetting by construction -- logging a new service or renewing the
     * contract both change what "next expected" computes to, so the
     * stored date here stops matching and a fresh nudge becomes eligible
     * again with no explicit clear() step needed.
     */
    val serviceDueNotifiedForDate: LocalDate? = null,

    // --- Category-specific fields addition (Room v10 → v11 migration) ---
    // Feedback, 2026-08-30: "we use the same fields for all the items... but
    // each has different features and their respective fields need to be
    // shown" -- researched what Insurance/AMC/Warranty/Subscription/
    // Membership trackers typically record and added the handful of fields
    // that genuinely didn't exist yet (see each field's own doc comment for
    // why it's scoped to one category). Deliberately followed the SAME
    // reuse-with-a-category-aware-label pattern as [costLabel] and
    // [referenceNumberLabel] above wherever the concept already existed --
    // e.g. Insurance's "Premium paid" and "Premium frequency" are the
    // existing billingAmount/billingCycle fields with new labels
    // (billingAmountLabel/billingCycleLabel below), not new columns. Only
    // truly new facts got a truly new column, to keep this table from
    // growing one column per category per request.

    /**
     * Insurance-only: who the policy payout goes to. Never asked for
     * elsewhere in the app (no category has an equivalent concept), so
     * unlike cost/amcNumber this is a genuinely new column rather than a
     * relabeled shared one.
     */
    val nomineeName: String? = null,

    /**
     * AMC-only: phone number or contact name for the vendor/technician who
     * actually shows up for a service visit -- distinct from [vendor]
     * (the contracting company), since in practice the person to call for
     * "where's my technician" is often a direct line, not the company's
     * general number. Free text rather than split phone/name fields --
     * matches how [location] already handles a similarly free-form fact.
     */
    val serviceProviderContact: String? = null,

    /**
     * Warranty-only: who's actually on the hook for a claim -- the
     * manufacturer, an extended-warranty provider (often a different
     * company from who made the product), or the retailer/store. Real
     * warranty-tracking tools treat this as a core field because it
     * determines who you call, not just metadata.
     */
    val warrantyType: WarrantyType? = null,

    /**
     * Subscription: the plan name (e.g. "Family", "Premium", "Pro").
     * Membership: the tier name (e.g. "Gold", "Individual"). Reused across
     * both rather than two near-identical columns -- both are "which
     * variant of this recurring thing did I sign up for", same as
     * [costLabel] reuses one column across categories with different
     * meanings. See [planTierLabel] for the category-aware label.
     */
    val planTier: String? = null,

    /**
     * Membership-only: how many people this membership covers (e.g. a
     * family gym membership covering 4). Null/absent reads as "just me" --
     * deliberately not defaulted to 1, so an unset value is visibly unset
     * rather than indistinguishable from a real answer.
     */
    val membersCovered: Int? = null,
)

/**
 * Warranty-only: who is actually responsible for honouring a claim. Kept
 * as its own small enum (unlike planTier's free text) because there ARE
 * only ever these three real answers in practice, and having them as fixed
 * choices makes "who do I call" scannable at a glance rather than a pile of
 * slightly-different free-text spellings ("Manufacturer" vs "Mfg" vs "OEM").
 */
enum class WarrantyType {
    MANUFACTURER, EXTENDED, STORE;

    val displayName: String
        get() = when (this) {
            MANUFACTURER -> "Manufacturer"
            EXTENDED -> "Extended warranty"
            STORE -> "Store / retailer"
        }
}

/**
 * Category-aware label for the shared "Billing" section's cycle/amount/
 * section title -- Insurance's premium and Membership's fee are the exact
 * same underlying fields ([Item.billingCycle]/[Item.billingAmount]) as a
 * Subscription's billing, just called something else in real life. See
 * [costLabel]'s doc comment for the same reasoning applied earlier.
 */
fun ItemCategory.billingSectionLabel(): String = when (this) {
    ItemCategory.INSURANCE -> "Premium"
    ItemCategory.MEMBERSHIP -> "Membership fee"
    ItemCategory.WARRANTY, ItemCategory.SUBSCRIPTION, ItemCategory.AMC, ItemCategory.OTHER -> "Billing"
}

fun ItemCategory.billingCycleLabel(): String = when (this) {
    ItemCategory.INSURANCE -> "Premium frequency"
    ItemCategory.WARRANTY, ItemCategory.SUBSCRIPTION, ItemCategory.AMC, ItemCategory.MEMBERSHIP, ItemCategory.OTHER -> "Billing cycle"
}

fun ItemCategory.billingAmountLabel(): String = when (this) {
    ItemCategory.INSURANCE -> "Premium paid"
    ItemCategory.MEMBERSHIP -> "Membership fee amount"
    ItemCategory.WARRANTY, ItemCategory.SUBSCRIPTION, ItemCategory.AMC, ItemCategory.OTHER -> "Billing amount per cycle"
}

/**
 * Category-aware label for [Item.planTier]. Subscription and Membership
 * are the only categories that show this field at all (see
 * AddEditItemScreen's AnimatedVisibility gate) -- the other branches exist
 * only so the `when` stays exhaustive if a caller ever asks for one anyway.
 */
fun ItemCategory.planTierLabel(): String = when (this) {
    ItemCategory.SUBSCRIPTION -> "Plan / tier"
    ItemCategory.MEMBERSHIP -> "Membership tier"
    ItemCategory.WARRANTY, ItemCategory.AMC, ItemCategory.INSURANCE, ItemCategory.OTHER -> "Plan / tier"
}

/**
 * Retaxonomy follow-up, 2026-08-25: single definition of "this item has a
 * recurring payment attached", used everywhere money/subscription tracking
 * needs it -- HomeViewModel's Overview money card and QuickFilter.
 * SUBSCRIPTIONS, its "renewal approaching" grouping bucket, and the daily
 * digest notification. Replaces the old itemType == SUBSCRIPTION check
 * those 3 places each ran independently: that only ever caught items
 * explicitly typed SUBSCRIPTION, so a billed AMC contract or an Insurance
 * premium was invisible to "Monthly subscriptions" even with real billing
 * data attached. Keyed on the billing fields themselves rather than
 * category or the now-derived ItemType, so it can never again silently
 * drift out of sync with what a user actually entered -- exactly the
 * class of bug ItemType being independently settable caused once already.
 */
val Item.isRecurringPayment: Boolean
    get() = billingCycle != null && billingAmount != null
