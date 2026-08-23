package com.venunair.warden.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venunair.warden.data.BillingCycle
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.ItemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Sprint 7: groups items by urgency, computes summary metrics, and
 * exposes filter state for category and location chips.
 */
class HomeViewModel(repository: ItemRepository) : ViewModel() {

    private val allItems: StateFlow<List<Item>> = repository.observeActiveItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Filter state ────────────────────────────────────────────────

    private val _selectedCategories = MutableStateFlow<Set<ItemCategory>>(emptySet())
    val selectedCategories: StateFlow<Set<ItemCategory>> = _selectedCategories

    private val _selectedLocation = MutableStateFlow<String?>(null)
    val selectedLocation: StateFlow<String?> = _selectedLocation

    fun toggleCategory(category: ItemCategory) {
        _selectedCategories.value = _selectedCategories.value.let { current ->
            if (category in current) current - category else current + category
        }
    }

    fun selectLocation(location: String?) {
        _selectedLocation.value = location
    }

    // ── Derived state ───────────────────────────────────────────────

    /** All distinct locations across active items, for the location filter. */
    val availableLocations: StateFlow<List<String>> = allItems
        .combine(_selectedCategories) { items, _ -> items }
        .combine(_selectedLocation) { items, _ ->
            items.mapNotNull { it.location }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Filtered + grouped items for the home list. */
    val groupedItems: StateFlow<GroupedItems> = combine(
        allItems, _selectedCategories, _selectedLocation
    ) { items, cats, loc ->
        val filtered = items
            .let { list -> if (cats.isEmpty()) list else list.filter { it.category in cats } }
            .let { list -> if (loc == null) list else list.filter { it.location == loc } }
        groupByUrgency(filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupedItems())

    /** Summary metrics computed on the UNFILTERED items (always the full picture). */
    val summary: StateFlow<HomeSummary> = allItems
        .combine(MutableStateFlow(Unit)) { items, _ -> computeSummary(items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeSummary())
}

// ── Data classes ────────────────────────────────────────────────────

data class GroupedItems(
    val expiringSoon: List<Item> = emptyList(),
    val renewalApproaching: List<Item> = emptyList(),
    val active: List<Item> = emptyList()
) {
    val isEmpty get() = expiringSoon.isEmpty() && renewalApproaching.isEmpty() && active.isEmpty()
    val totalCount get() = expiringSoon.size + renewalApproaching.size + active.size
}

data class HomeSummary(
    /** Sum of cost for items expiring within 30 days. */
    val moneyAtRisk: Double = 0.0,
    /** Sum of subscription billingAmount normalised to monthly. */
    val monthlySubscriptions: Double = 0.0,
    /** Number of items expiring within 30 days. */
    val expiringCount: Int = 0,
    /** Total active subscriptions. */
    val subscriptionCount: Int = 0
)

// ── Grouping logic ──────────────────────────────────────────────────

private fun groupByUrgency(items: List<Item>): GroupedItems {
    val today = LocalDate.now()
    val expiringSoon = mutableListOf<Item>()
    val renewalApproaching = mutableListOf<Item>()
    val active = mutableListOf<Item>()

    for (item in items) {
        val daysLeft = ChronoUnit.DAYS.between(today, item.expiryDate)
        when {
            // Subscriptions with auto-renew approaching get their own group
            item.itemType == ItemType.SUBSCRIPTION && item.autoRenew && daysLeft in 0..30 -> {
                renewalApproaching.add(item)
            }
            // Expired or expiring within 30 days
            daysLeft <= 30 -> {
                expiringSoon.add(item)
            }
            else -> {
                active.add(item)
            }
        }
    }

    return GroupedItems(
        expiringSoon = expiringSoon.sortedBy { it.expiryDate },
        renewalApproaching = renewalApproaching.sortedBy { it.expiryDate },
        active = active.sortedBy { it.expiryDate }
    )
}

private fun computeSummary(items: List<Item>): HomeSummary {
    val today = LocalDate.now()
    val expiringItems = items.filter {
        val d = ChronoUnit.DAYS.between(today, it.expiryDate)
        d in 0..30
    }
    val subscriptions = items.filter { it.itemType == ItemType.SUBSCRIPTION }

    return HomeSummary(
        moneyAtRisk = expiringItems.mapNotNull { it.cost }.sum(),
        monthlySubscriptions = subscriptions.sumOf { item ->
            val amount = item.billingAmount ?: 0.0
            val factor = item.billingCycle?.toMonthlyFactor() ?: 0.0
            amount * factor
        },
        expiringCount = expiringItems.size,
        subscriptionCount = subscriptions.size
    )
}
