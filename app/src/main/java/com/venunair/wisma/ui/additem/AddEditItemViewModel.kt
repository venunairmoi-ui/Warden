package com.venunair.warden.ui.additem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venunair.warden.data.BillingCycle
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.ItemStatus
import com.venunair.warden.data.ItemType
import com.venunair.warden.data.WarrantyType
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Save id is passed in per-call (NOT captured once from a constructor
 * param) deliberately: on Edit, it's the existing item's id every time; on
 * Add, it's 0 (Room's "insert a new row" convention) every time, since
 * nothing about a new item is saved before this actual Save press -- see
 * AddEditItemScreen's pendingAttachments for why that's true even when
 * photos/PDFs were already attached in the UI before Save was pressed.
 *
 * onSaved receives the real, now-persisted item id (0 in, real autoGenerate
 * id out) rather than being a bare no-arg callback, specifically so the
 * caller can insert any attachments that were sitting in local pending
 * state -- Attachment.itemId is a NOT NULL foreign key, so those inserts
 * can only happen once this id exists for real.
 */
class AddEditItemViewModel(
    private val repository: ItemRepository
) : ViewModel() {

    fun save(
        id: Long,
        name: String,
        vendor: String?,
        category: ItemCategory,
        subCategory: String?,
        itemType: ItemType,
        purchaseDate: LocalDate?,
        expiryDate: LocalDate,
        cost: Double?,
        amcNumber: String?,
        notes: String?,
        // Sprint 6 additions
        serialNumber: String?,
        modelNumber: String?,
        retailer: String?,
        invoiceNumber: String?,
        location: String?,
        billingCycle: BillingCycle?,
        billingAmount: Double?,
        autoRenew: Boolean,
        // Product/Service reintroduction addition: AMC-only, null elsewhere
        visitsIncluded: Int?,
        // AMC service-visit tracking addition, 2026-08-26: AMC-only, null
        // elsewhere. Threaded straight through from the form's own text
        // field (blank -> null), same convention every other optional
        // field here follows -- ItemRepository.applyAmcPeriodTracking
        // auto-fills a default when this comes through null on a new item
        // or a renewal, but never overrides a value the user actually typed.
        serviceIntervalMonths: Int?,
        // Category-specific fields addition, 2026-08-30: each null except
        // for the one category it's actually shown for -- see each field's
        // doc comment on Item.
        nomineeName: String?,
        serviceProviderContact: String?,
        warrantyType: WarrantyType?,
        planTier: String?,
        membersCovered: Int?,
        // Caller passes the loaded item's current status on an edit (it has
        // it, from the same Flow the rest of the form is pre-filled from).
        // Without this, every edit silently reset status back to the Item
        // constructor's ACTIVE default -- e.g. un-archiving an archived item
        // just by editing and saving it. Found alongside the reminder-rules
        // cascade-delete bug while auditing this same save path.
        status: ItemStatus = ItemStatus.ACTIVE,
        // Sprint 8: user-selected reminder intervals
        reminderOffsets: List<Int> = emptyList(),
        onSaved: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val item = Item(
                id = id,
                name = name,
                vendor = vendor,
                category = category,
                subCategory = subCategory,
                // Product/Service reintroduction, 2026-08-25: back to being
                // a real user choice again (see ItemType's doc comment for
                // why this one doesn't repeat the old field's redundancy
                // problem) -- caller passes whatever the Add/Edit dropdown
                // holds, defaulted from category only once when that
                // dropdown first appears.
                itemType = itemType,
                purchaseDate = purchaseDate,
                expiryDate = expiryDate,
                cost = cost,
                amcNumber = amcNumber,
                notes = notes,
                status = status,
                serialNumber = serialNumber,
                modelNumber = modelNumber,
                retailer = retailer,
                invoiceNumber = invoiceNumber,
                location = location,
                billingCycle = billingCycle,
                billingAmount = billingAmount,
                autoRenew = autoRenew,
                visitsIncluded = visitsIncluded,
                serviceIntervalMonths = serviceIntervalMonths,
                nomineeName = nomineeName,
                serviceProviderContact = serviceProviderContact,
                warrantyType = warrantyType,
                planTier = planTier,
                membersCovered = membersCovered
                // NOTE: createdAt resets to today on every edit since Item's
                // default isn't preserved here. Harmless for now — nothing
                // reads createdAt yet — but revisit if you add "sort by date
                // added" later; thread the original value through instead.
            )
            val savedId = repository.saveItem(item, reminderOffsets)
            // Sprint 8: if editing, also replace existing rules with the
            // user-selected offsets (saveItem only seeds defaults on insert
            // or resets fired state on update — it doesn't replace the set).
            if (id != 0L && reminderOffsets.isNotEmpty()) {
                repository.replaceReminderRules(savedId, reminderOffsets)
            }
            onSaved(savedId)
        }
    }
}
