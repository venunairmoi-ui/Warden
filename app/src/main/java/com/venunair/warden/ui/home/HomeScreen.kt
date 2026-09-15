package com.venunair.warden.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import coil.compose.AsyncImage
import com.venunair.warden.BuildConfig
import com.venunair.warden.R
import com.venunair.warden.autodetect.AutoDetectWorker
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.SearchResult
import com.venunair.warden.reminders.ReminderCheckWorker
import com.venunair.warden.ui.common.categoryIcon
import com.venunair.warden.ui.common.toCurrencyString
import com.venunair.warden.ui.theme.ItemUrgency
import com.venunair.warden.ui.theme.color
import com.venunair.warden.ui.theme.urgencyOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Overview screen pass, 2026-08-25: DEBUG_AUTO_DETECT_WORK_NAME and
// describeAutoDetectResult moved to OverviewScreen.kt along with the debug
// tools overflow menu that used them (top-level `private` declarations are
// file-scoped in Kotlin, so they moved rather than staying importable from
// here).

// Dashboard restyle pass (2026-08-25) — matches the mockup's card footer
// date format ("Mar 3, 2027" in wisma_dashboard/code.html).
private val CARD_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

private class HomeViewModelFactory(private val repository: ItemRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: ItemRepository,
    onAddItem: () -> Unit,
    onOpenItem: (Long) -> Unit,
    onEditItem: (Long) -> Unit,
    onBack: () -> Unit,
    // Overview screen pass, 2026-08-25: set when this screen was reached by
    // tapping a stat tile/card on OverviewScreen -- applied once below via
    // HomeViewModel.applyInitialFilter (distinct from toggleQuickFilter's
    // click-to-clear semantics: this always SETS, regardless of whatever
    // this screen's own fresh ViewModel instance already holds).
    initialFilter: HomeViewModel.QuickFilter? = null,
    // Set when reached via Overview's search icon, so the search bar opens
    // immediately instead of requiring a second tap once here.
    startInSearch: Boolean = false
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(repository))
    val grouped by viewModel.groupedItems.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val quickFilter by viewModel.quickFilter.collectAsState()
    val availableLocations by viewModel.availableLocations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // Track items just restored via Undo so their row skips the entry animation
    val restoredItemIds = remember { mutableStateListOf<Long>() }

    LaunchedEffect(Unit) {
        initialFilter?.let { viewModel.applyInitialFilter(it) }
        if (startInSearch) viewModel.setSearchActive(true)
    }

    // ── State-driven archive snackbar ──────────────────────────────
    // Keeps the snackbar lifecycle separate from the swipe callback,
    // preventing duplicate launches when confirmValueChange fires
    // more than once during the spring-back animation.
    var archivedItemId by remember { mutableStateOf(0L) }
    var archivedItemName by remember { mutableStateOf("") }
    var archiveSeq by remember { mutableIntStateOf(0) }
    // Resolved here (composable scope), then referenced inside the
    // LaunchedEffect coroutine below, since stringResource() can't be
    // called directly from non-composable code -- recomputed on every
    // recomposition, so the closure LaunchedEffect(archiveSeq) captures on
    // its next relaunch always sees the current archivedItemName.
    val archivedSnackbarMessage = stringResource(R.string.archived_snackbar, archivedItemName)
    val undoActionLabel = stringResource(R.string.undo)

    LaunchedEffect(archiveSeq) {
        if (archiveSeq == 0) return@LaunchedEffect
        val itemId = archivedItemId
        val result = snackbarHostState.showSnackbar(
            message = archivedSnackbarMessage,
            actionLabel = undoActionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            restoredItemIds += itemId
            viewModel.unarchiveItem(itemId)
        }
    }

    // Overview screen pass, 2026-08-25: this screen's title now names
    // whichever filter got the user here (or "My Products" when reached
    // unfiltered, e.g. via Overview's total-items text) -- replaces the
    // WISMA wordmark + the DashboardHeader/StatsRow/SummaryCards block that
    // used to live below it (moved to OverviewScreen.kt, the new landing
    // screen). The Close action clears the quick filter via the same
    // toggle function the removed SummaryCard used to call -- toggling the
    // filter that's already active clears it, same as re-tapping a
    // selected FilterChip.
    val filterTitle = when (quickFilter) {
        null -> stringResource(R.string.home_all_products_title)
        HomeViewModel.QuickFilter.ACTIVE -> stringResource(R.string.section_active)
        HomeViewModel.QuickFilter.DUE_SOON -> stringResource(R.string.filter_due_soon_title)
        HomeViewModel.QuickFilter.EXPIRED -> stringResource(R.string.expired_label)
        HomeViewModel.QuickFilter.AT_RISK -> stringResource(R.string.summary_at_risk)
        // Renamed 2026-09-01 alongside the Dashboard card (was "Monthly
        // subscriptions") -- the underlying QuickFilter still matches
        // Item.isRecurringPayment across every category (AMC, Insurance,
        // Membership, Subscription), not literal subscriptions only, so
        // this title needs to stay honest about that too.
        HomeViewModel.QuickFilter.SUBSCRIPTIONS -> stringResource(R.string.filter_recurring_costs_title)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(filterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.setSearchActive(true) }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.content_desc_search))
                    }
                    // quickFilter captured via a local val inside the let,
                    // rather than smart-cast across the IconButton's onClick
                    // lambda, to sidestep any ambiguity about whether a
                    // delegated `by collectAsState()` val smart-casts across
                    // a captured closure.
                    quickFilter?.let { activeFilter ->
                        IconButton(onClick = { viewModel.toggleQuickFilter(activeFilter) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_desc_clear_filter))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSearching) {
                ExtendedFloatingActionButton(
                    onClick = onAddItem,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_button)) }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ── Search bar ──────────────────────────────────────
            if (isSearching) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    onClose = { viewModel.setSearchActive(false) }
                )
            }

            if (isSearching && searchQuery.length >= 2) {
                // ── Search results ──────────────────────────────
                SearchResultsList(
                    results = searchResults,
                    query = searchQuery,
                    onOpenItem = onOpenItem
                )
            } else if (!isSearching && grouped.isEmpty && selectedCategories.isEmpty() && selectedLocation == null && quickFilter == null) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else if (!isSearching) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp) // clear FAB
                ) {
                    // ── Filter chips ─────────────────────────────
                    item(key = "filters") {
                        FilterBar(
                            selectedCategories = selectedCategories,
                            onToggleCategory = viewModel::toggleCategory,
                            selectedLocation = selectedLocation,
                            onSelectLocation = viewModel::selectLocation,
                            availableLocations = availableLocations
                        )
                    }

                    // ── Expiring soon ────────────────────────────
                    if (grouped.expiringSoon.isNotEmpty()) {
                        item(key = "header_expiring") {
                            SectionHeader(
                                icon = Icons.Filled.Warning,
                                title = stringResource(R.string.section_expiring_soon),
                                count = grouped.expiringSoon.size,
                                tintColor = MaterialTheme.colorScheme.error
                            )
                        }
                        itemsIndexed(
                            grouped.expiringSoon,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            SwipeableItemRow(
                                item = item,
                                index = index,
                                skipAnimation = item.id in restoredItemIds,
                                onClick = {
                                    restoredItemIds.remove(item.id)
                                    onOpenItem(item.id)
                                },
                                onArchive = {
                                    viewModel.archiveItem(item.id)
                                    archivedItemId = item.id
                                    archivedItemName = item.name
                                    archiveSeq++
                                },
                                onEdit = { onEditItem(item.id) }
                            )
                        }
                    }

                    // ── Renewal approaching ──────────────────────
                    if (grouped.renewalApproaching.isNotEmpty()) {
                        item(key = "header_renewal") {
                            SectionHeader(
                                icon = Icons.Filled.Autorenew,
                                title = stringResource(R.string.section_renewal_approaching),
                                count = grouped.renewalApproaching.size,
                                tintColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                        itemsIndexed(
                            grouped.renewalApproaching,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            SwipeableItemRow(
                                item = item,
                                index = index,
                                skipAnimation = item.id in restoredItemIds,
                                onClick = {
                                    restoredItemIds.remove(item.id)
                                    onOpenItem(item.id)
                                },
                                onArchive = {
                                    viewModel.archiveItem(item.id)
                                    archivedItemId = item.id
                                    archivedItemName = item.name
                                    archiveSeq++
                                },
                                onEdit = { onEditItem(item.id) }
                            )
                        }
                    }

                    // ── Active ───────────────────────────────────
                    if (grouped.active.isNotEmpty()) {
                        item(key = "header_active") {
                            SectionHeader(
                                icon = Icons.Filled.Shield,
                                title = stringResource(R.string.section_active),
                                count = grouped.active.size,
                                tintColor = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        itemsIndexed(
                            grouped.active,
                            key = { _, item -> item.id }
                        ) { index, item ->
                            SwipeableItemRow(
                                item = item,
                                index = index,
                                skipAnimation = item.id in restoredItemIds,
                                onClick = {
                                    restoredItemIds.remove(item.id)
                                    onOpenItem(item.id)
                                },
                                onArchive = {
                                    viewModel.archiveItem(item.id)
                                    archivedItemId = item.id
                                    archivedItemName = item.name
                                    archiveSeq++
                                },
                                onEdit = { onEditItem(item.id) }
                            )
                        }
                    }

                    // ── Empty filter result ──────────────────────
                    if (grouped.isEmpty && (selectedCategories.isNotEmpty() || selectedLocation != null || quickFilter != null)) {
                        item(key = "no_results") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.no_filter_results),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else if (isSearching) {
                // Searching but query < 2 chars
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.search_min_chars_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Search bar ─────────────────────────────────────────────────────

/**
 * Pill-shaped, bordered — no dedicated mockup shows an expanded search
 * bar (wisma_dashboard/code.html only has a bare search icon in the
 * filter row), so this borrows the same pill + outlineVariant-border
 * language as the rest of this redesign pass (filter chips, CTAs)
 * rather than Material3's default rounded-rect OutlinedTextField.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(stringResource(R.string.home_search_placeholder), style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = {
                if (query.isNotEmpty()) onQueryChange("") else onClose()
            }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.content_desc_clear_search))
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

// ── Search results list ────────────────────────────────────────────

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    query: String,
    onOpenItem: (Long) -> Unit
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.search_no_results_for, query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            itemsIndexed(results, key = { _, r -> r.item.id }) { _, result ->
                SearchResultRow(
                    result = result,
                    onClick = { onOpenItem(result.item.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val item = result.item
    ProductCard(
        item = item,
        onClick = onClick,
        modifier = modifier,
        leading = {
            // Search results show the matched attachment thumbnail (OCR
            // hits) in place of the usual category icon, when there is one.
            if (result.attachmentThumbnail != null) {
                AsyncImage(
                    model = result.attachmentThumbnail,
                    contentDescription = stringResource(R.string.content_desc_attachment),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                CategoryIconBadge(item = item)
            }
        },
        belowVendorContent = {
            result.ocrSnippet?.let { snippet ->
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    )
}

// Overview screen pass, 2026-08-25: DashboardHeader, the Active/Due soon/
// Expired bento row (DashboardStatsRow/StatTile/ActiveCategoryBreakdown),
// and the At risk/Monthly subscriptions split card (SummaryCards/
// SummaryCard) all moved to OverviewScreen.kt, which is now the app's
// landing screen -- this screen (My Products) is reached by tapping one
// of those, already filtered accordingly (see HomeViewModel.QuickFilter),
// and no longer needs its own copy of the same stats. Its top bar now
// carries the current filter's name instead (see the TopAppBar above).

// ── Filter bar ──────────────────────────────────────────────────────

@Composable
private fun FilterBar(
    selectedCategories: Set<ItemCategory>,
    onToggleCategory: (ItemCategory) -> Unit,
    selectedLocation: String?,
    onSelectLocation: (String?) -> Unit,
    availableLocations: List<String>
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Retaxonomy, 2026-08-25: the old HOME/OFFICE-exclusion here
            // (they duplicated the free-text Item.location field) no
            // longer applies -- those two categories don't exist anymore.
            // Every current category, including OTHER, gets a chip: OTHER
            // specifically needs to stay filterable so items migrated into
            // it (their pre-retaxonomy category was removed) are easy to
            // find and manually recategorize.
            ItemCategory.entries.forEach { category ->
                FilterChip(
                    selected = category in selectedCategories,
                    onClick = { onToggleCategory(category) },
                    // UI redesign pass, 2026-08-25: labelSmall (DESIGN.md's
                    // DIRECT label-sm token: 12sp Geist Mono, +0.05em
                    // tracking) + uppercase, matching the mockup's own
                    // filter chips ("ALL"/"ACTIVE"/"EXPIRED" in
                    // wisma_dashboard/code.html are literally uppercase
                    // font-label-sm). Was labelMedium (13sp, extrapolated,
                    // mixed-case) -- one step down the type scale per
                    // feedback, and uppercase turns the mono treatment into
                    // a deliberate "tag" look instead of an accidental font
                    // clash with the mixed-case body text around it.
                    label = {
                        Text(
                            category.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = if (category in selectedCategories) {
                        {
                            Icon(
                                categoryIcon(category),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    // True pill (mockup: rounded-full), not M3's default
                    // small-corner chip shape.
                    shape = CircleShape,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = category in selectedCategories,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
        // Location filter — only when 2+ distinct locations exist
        if (availableLocations.size >= 2) {
            Spacer(Modifier.height(4.dp))
            LocationFilter(
                selectedLocation = selectedLocation,
                locations = availableLocations,
                onSelect = onSelectLocation
            )
        }
    }
}

@Composable
private fun LocationFilter(
    selectedLocation: String?,
    locations: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedLocation != null,
            onClick = { expanded = true },
            label = {
                Text(
                    (selectedLocation ?: stringResource(R.string.filter_all_locations)).uppercase(),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            },
            shape = CircleShape,
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selectedLocation != null,
                borderColor = MaterialTheme.colorScheme.outlineVariant,
                selectedBorderColor = MaterialTheme.colorScheme.primary
            ),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                selectedLabelColor = MaterialTheme.colorScheme.primary
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_all_locations)) },
                onClick = { onSelect(null); expanded = false }
            )
            locations.forEach { loc ->
                DropdownMenuItem(
                    text = { Text(loc) },
                    onClick = { onSelect(loc); expanded = false }
                )
            }
        }
    }
}

// ── Section header ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    tintColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = tintColor.copy(alpha = 0.12f),
            contentColor = tintColor,
            shape = RoundedCornerShape(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(4.dp).size(16.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tintColor
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = tintColor.copy(alpha = 0.7f)
        )
    }
}

// ── Swipeable animated item row ─────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableItemRow(
    item: Item,
    index: Int,
    skipAnimation: Boolean = false,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(skipAnimation) }
    LaunchedEffect(item.id) {
        if (!visible) {
            delay(index.coerceAtMost(10) * 50L)
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(300)
        )
    ) {
        // Guard: prevent duplicate onArchive calls if the spring-back
        // animation oscillates across the swipe threshold.
        var swiped by remember { mutableStateOf(false) }
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { dismissValue ->
                if (dismissValue == SwipeToDismissBoxValue.StartToEnd && !swiped) {
                    swiped = true
                    onArchive()
                }
                // Always return false so the state resets to Settled.
                // The item disappears from the list instantly (Flow filters
                // out ARCHIVED), and when Undo restores it, the fresh
                // composable starts at Settled instead of inheriting the
                // saved StartToEnd value from rememberSaveable.
                false
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            // Matches ProductCard's own corner radius so the
                            // swipe-reveal background and the card sliding
                            // over it share the same silhouette mid-swipe.
                            MaterialTheme.shapes.extraLarge
                        )
                        .padding(start = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Archive,
                            contentDescription = stringResource(R.string.action_archive),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.action_archive),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            },
            enableDismissFromEndToStart = false
        ) {
            ItemRow(
                item = item,
                onClick = onClick,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onEdit = onEdit,
                onArchive = {
                    if (!swiped) {
                        swiped = true
                        onArchive()
                    }
                }
            )
        }
    }
}

// ── Item row ────────────────────────────────────────────────────────

@Composable
private fun ItemRow(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null
) {
    ProductCard(item = item, onClick = onClick, modifier = modifier, onEdit = onEdit, onArchive = onArchive)
}

/** The circular category-icon badge every product card leads with, unless a search result swaps it for an attachment thumbnail. */
@Composable
private fun CategoryIconBadge(item: Item) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            categoryIcon(item.category),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Shared product-card anatomy, restyled from wisma_dashboard/code.html's
 * product cards (2026-08-25): a urgency-colored left accent bar, an
 * uppercase category tag, the item name and vendor, a status pill, and a
 * footer row (days remaining + expiry date) below a hairline divider.
 * Used by both [ItemRow] (the normal grouped list) and [SearchResultRow]
 * (which swaps in an attachment thumbnail and an OCR snippet) so the two
 * don't visually drift apart over time.
 *
 * The mockup's cards also carry a status icon on the right
 * (verified_user/gpp_bad) -- skipped here since this card already has a
 * category icon on the left AND a status pill on the right; a third icon
 * would be one signal too many for what needs to stay scannable across a
 * list of dozens of real items, not the mockup's 3-item demo.
 */
@Composable
private fun ProductCard(
    item: Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = { CategoryIconBadge(item) },
    belowVendorContent: (@Composable () -> Unit)? = null,
    // Overflow menu, so Archive/Edit don't require the swipe gesture --
    // null on call sites (e.g. search results) that don't manage a list.
    onEdit: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null
) {
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), item.expiryDate)
    // Rolls over to whole months past 60 days out so a year-long warranty
    // reads as "11 MONTHS REMAINING" rather than "334 DAYS REMAINING" --
    // the exact date stays visible in the row below regardless.
    val monthsLeft = ChronoUnit.MONTHS.between(LocalDate.now(), item.expiryDate)
    val urgency = urgencyOf(item.expiryDate)
    val urgencyTint = urgency.color()

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth()
    ) {
        // IntrinsicSize.Min, not a Box+fillMaxHeight overlay: this Row sits
        // inside a LazyColumn item, which measures its content with an
        // effectively unbounded max height -- fillMaxHeight() would have
        // nothing finite to fill against there and either collapse to zero
        // or crash. Sizing the Row to its own minimum intrinsic height
        // first (a real, bounded measurement pass) is what makes
        // fillMaxHeight() on the accent-bar child below resolve correctly
        // against a real number. Same technique Compose's own vertical
        // divider samples use for exactly this "bar spans a content-sized
        // sibling" shape.
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(urgencyTint)
            )
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    leading()
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        Text(
                            // Retaxonomy, 2026-08-25: append the subcategory
                            // when there is one ("WARRANTY · ELECTRONICS")
                            // -- category alone got a lot less specific now
                            // that Warranty/AMC/Insurance each cover several
                            // kinds of item.
                            listOfNotNull(item.category.displayName, item.subCategory)
                                .joinToString(" · ")
                                .uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        item.vendor?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        belowVendorContent?.invoke()
                    }
                    // Feedback, 2026-08-25: the status pill that used to sit
                    // here duplicated the footer text below it -- literally,
                    // for an overdue item ("EXPIRED" appeared both here and
                    // in the footer). Removed; the accent bar (color) +
                    // footer text (color + label) already carry the status,
                    // once each.
                    if (onEdit != null || onArchive != null) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            // Accessibility fix, 2026-09-15 (Phase 2): this
                            // used to override IconButton's default size
                            // down to 32dp to look tidier in the compact
                            // card row -- but that shrinks the actual
                            // touchable area below Android's 48dp minimum
                            // recommended target, not just the visuals.
                            // IconButton already reserves 48dp of touch
                            // target by default; shrink only the icon
                            // glyph inside it instead.
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = stringResource(R.string.content_desc_more_actions, item.name),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                onEdit?.let { edit ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_edit)) },
                                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            edit()
                                        }
                                    )
                                }
                                onArchive?.let { archive ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_archive)) },
                                        leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            archive()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            daysLeft < 0 -> stringResource(R.string.expired_label).uppercase()
                            daysLeft == 0L -> stringResource(R.string.due_today_label).uppercase()
                            daysLeft < 60 -> stringResource(R.string.days_remaining_label, daysLeft)
                            else -> stringResource(R.string.months_remaining_label, monthsLeft)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = urgencyTint
                    )
                    Text(
                        item.expiryDate.format(CARD_DATE_FORMAT),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.empty_state_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.empty_state_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
