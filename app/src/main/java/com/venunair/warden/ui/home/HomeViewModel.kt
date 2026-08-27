package com.venunair.warden.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.venunair.warden.data.BillingCycle
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.SearchResult
import com.venunair.warden.data.isRecurringPayment
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Sprint 7: groups items by urgency, computes summary metrics, and
 * exposes filter state for category and location chips.
 * Sprint 8: adds search state and archive/unarchive.
 */
class HomeViewModel(private val repository: ItemRepository) : ViewModel() {

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

    // Feedback, 2026-08-25: the "At risk this month" and "Monthly
    // subscriptions" summary cards on the Dashboard are now tappable quick
    // filters, alongside the category/location chips above. A separate
    // enum (not reusing ItemCategory) because neither tier maps to a single
    // category value -- AT_RISK is a date-window cut across every category,
    // and SUBSCRIPTIONS matches Item.isRecurringPayment (any item with a
    // billing amount attached), a different, ItemCategory-independent
    // condition -- see groupedItems below. Toggle, not radio: tapping the
    // active filter again clears it, matching FilterChip's own
    // selected/click-to-deselect behaviour.
    //
    // Overview screen pass, 2026-08-25: ACTIVE/DUE_SOON/EXPIRED added --
    // this screen is now also reached by tapping one of OverviewScreen's
    // stat tiles (its own separate HomeViewModel instance; see that
    // screen's doc comment), which needs to land here pre-filtered to
    // exactly the population its tile counted.
    //
    // Bug fix, 2026-08-26: these three DO now run their own day-math below
    // (see groupedItems), matching OverviewScreen's tile counts exactly --
    // they originally post-filtered groupByUrgency's own bucket output
    // instead (on the theory that reusing it couldn't disagree with
    // OverviewScreen), but groupByUrgency's Expiring soon/Renewal
    // approaching/Active split is a different partition than this
    // Active/Due soon/Expired trio, and borrowing it let a billed,
    // auto-renewing item due today get folded into Active and vanish from
    // Due soon. Both sides now share the same plain day-math instead.
    enum class QuickFilter { ACTIVE, DUE_SOON, EXPIRED, AT_RISK, SUBSCRIPTIONS }

    private val _quickFilter = MutableStateFlow<QuickFilter?>(null)
    val quickFilter: StateFlow<QuickFilter?> = _quickFilter

    fun toggleQuickFilter(filter: QuickFilter) {
        _quickFilter.value = if (_quickFilter.value == filter) null else filter
    }

    // Overview screen pass, 2026-08-25: distinct from toggleQuickFilter --
    // always SETS the filter (never clears it), and is meant to be called
    // exactly once, from a LaunchedEffect(Unit) when this screen is reached
    // by tapping an OverviewScreen tile rather than opened directly. The
    // user can still clear it afterwards via the on-screen "Clear filter"
    // affordance, which calls toggleQuickFilter/selectLocation etc. as usual.
    fun applyInitialFilter(filter: QuickFilter) {
        _quickFilter.value = filter
    }

    // ── Sprint 8: Search state ──────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearchActive(active: Boolean) {
        _isSearching.value = active
        if (!active) {
            _searchQuery.value = ""
            _searchResults.value = emptyList()
        }
    }

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    _searchResults.value = if (query.length >= 2) {
                        repository.searchItems(query)
                    } else {
                        emptyList()
                    }
                }
        }
    }

    // ── Sprint 8: Archive/Unarchive ─────────────────────────────────

    fun archiveItem(itemId: Long) {
        viewModelScope.launch { repository.archiveItem(itemId) }
    }

    fun unarchiveItem(itemId: Long) {
        viewModelScope.launch { repository.unarchiveItem(itemId) }
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
        allItems, _selectedCategories, _selectedLocation, _quickFilter
    ) { items, cats, loc, quick ->
        val today = LocalDate.now()
        val categoryLocationFiltered = items
            .let { list -> if (cats.isEmpty()) list else list.filter { it.category in cats } }
            .let { list -> if (loc == null) list else list.filter { it.location == loc } }
        when (quick) {
            null -> groupByUrgency(categoryLocationFiltered)
            // Same "within 30 days" window HomeSummary.moneyAtRisk /
            // expiringCount use (computeSummary below) -- keeps the
            // filtered list matching exactly what the tapped card's own
            // numbers described.
            QuickFilter.AT_RISK -> groupByUrgency(
                categoryLocationFiltered.filter { ChronoUnit.DAYS.between(today, it.expiryDate) in 0..30 }
            )
            QuickFilter.SUBSCRIPTIONS -> groupByUrgency(
                categoryLocationFiltered.filter { it.isRecurringPayment }
            )
            // Bug fix, 2026-08-26: these three used to post-filter
            // groupByUrgency's own bucket membership (as the removed comment
            // here explained) -- but groupByUrgency's split (Expiring soon /
            // Renewal approaching / Active, for My Products' own list
            // sections) is a DIFFERENT partition than this Active/Due soon/
            // Expired trio. A billed, auto-renewing item due today or
            // tomorrow landed in renewalApproaching, which ACTIVE's old
            // `.copy(expiringSoon = emptyList())` kept -- so it counted as
            // Active and was invisible under Due soon entirely (reported
            // 2026-08-26). These three now classify directly off the plain
            // day-math every other "is this expired/due soon" consumer
            // already uses (OverviewScreen's tile counts, computeSummary's
            // at-risk window) -- independent of billing/auto-renew status,
            // so an item's bucket here can't depend on whether it renews.
            //
            // Bug fix, 2026-08-26 (same day, follow-up): boundary moved from
            // "due today counts as Expired" to "due today counts as Due
            // soon" -- matches Urgency.kt's urgencyOf (and this same fix
            // there), so an item due today gets the same classification and
            // color everywhere: this quick filter, the Overview tile it's
            // reached from, and the accent bar/status pill on the item
            // itself.
            QuickFilter.ACTIVE -> GroupedItems(
                active = categoryLocationFiltered
                    .filter { ChronoUnit.DAYS.between(today, it.expiryDate) > 30 }
                    .sortedBy { it.expiryDate }
            )
            QuickFilter.DUE_SOON -> GroupedItems(
                expiringSoon = categoryLocationFiltered
                    .filter { ChronoUnit.DAYS.between(today, it.expiryDate) in 0..30 }
                    .sortedBy { it.expiryDate }
            )
            QuickFilter.EXPIRED -> GroupedItems(
                expiringSoon = categoryLocationFiltered
                    .filter { it.expiryDate.isBefore(today) }
                    .sortedBy { it.expiryDate }
            )
        }
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
            // Retaxonomy follow-up, 2026-08-25: was itemType == SUBSCRIPTION
            // -- now any recurring-billed item (any category) with
            // auto-renew on gets its own group, not just ones explicitly
            // typed Subscription.
            item.isRecurringPayment && item.autoRenew && daysLeft in 0..30 -> {
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
    val subscriptions = items.filter { it.isRecurringPayment }

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
