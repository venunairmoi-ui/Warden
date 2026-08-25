package com.venunair.warden.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.venunair.warden.BuildConfig
import com.venunair.warden.autodetect.AutoDetectWorker
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.SearchResult
import com.venunair.warden.data.seedSampleData
import com.venunair.warden.reminders.ReminderCheckWorker
import com.venunair.warden.ui.common.categoryIcon
import com.venunair.warden.ui.common.toIndianCurrencyString
import com.venunair.warden.ui.theme.color
import com.venunair.warden.ui.theme.urgencyOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    onOpenSettings: () -> Unit = {}
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(repository))
    val grouped by viewModel.groupedItems.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val availableLocations by viewModel.availableLocations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Track items just restored via Undo so their row skips the entry animation
    val restoredItemIds = remember { mutableStateListOf<Long>() }

    // ── State-driven archive snackbar ──────────────────────────────
    // Keeps the snackbar lifecycle separate from the swipe callback,
    // preventing duplicate launches when confirmValueChange fires
    // more than once during the spring-back animation.
    var archivedItemId by remember { mutableStateOf(0L) }
    var archivedItemName by remember { mutableStateOf("") }
    var archiveSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(archiveSeq) {
        if (archiveSeq == 0) return@LaunchedEffect
        val itemId = archivedItemId
        val result = snackbarHostState.showSnackbar(
            message = "$archivedItemName archived",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            restoredItemIds += itemId
            viewModel.unarchiveItem(itemId)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Warden",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { viewModel.setSearchActive(true) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = {
                            scope.launch {
                                repository.seedSampleData()
                                snackbarHostState.showSnackbar("Added sample items")
                            }
                        }) {
                            Icon(Icons.Filled.Biotech, contentDescription = "Load sample data")
                        }
                        IconButton(onClick = {
                            WorkManager.getInstance(context)
                                .enqueue(OneTimeWorkRequestBuilder<ReminderCheckWorker>().build())
                            scope.launch { snackbarHostState.showSnackbar("Checking reminders…") }
                        }) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = "Check reminders now")
                        }
                        // Sprint 9: auto-detect's real schedule is every 6
                        // hours (WardenApplication.AUTO_DETECT_INTERVAL_HOURS)
                        // -- this lets a scan be tested on demand instead of
                        // waiting. Enqueuing here doesn't check whether the
                        // toggle/permission are actually on; AutoDetectWorker
                        // itself no-ops safely if either is missing, so this
                        // button is safe to tap regardless of Settings state.
                        IconButton(onClick = {
                            WorkManager.getInstance(context)
                                .enqueue(OneTimeWorkRequestBuilder<AutoDetectWorker>().build())
                            scope.launch { snackbarHostState.showSnackbar("Running auto-detect scan…") }
                        }) {
                            Icon(Icons.Filled.ImageSearch, contentDescription = "Run auto-detect scan now")
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
                    text = { Text("Add") }
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
            } else if (!isSearching && grouped.isEmpty && selectedCategories.isEmpty() && selectedLocation == null) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else if (!isSearching) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp) // clear FAB
                ) {
                    // ── Summary cards ────────────────────────────
                    item(key = "summary") {
                        SummaryCards(summary = summary)
                    }

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
                                title = "Expiring soon",
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
                                }
                            )
                        }
                    }

                    // ── Renewal approaching ──────────────────────
                    if (grouped.renewalApproaching.isNotEmpty()) {
                        item(key = "header_renewal") {
                            SectionHeader(
                                icon = Icons.Filled.Autorenew,
                                title = "Renewal approaching",
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
                                }
                            )
                        }
                    }

                    // ── Active ───────────────────────────────────
                    if (grouped.active.isNotEmpty()) {
                        item(key = "header_active") {
                            SectionHeader(
                                icon = Icons.Filled.Shield,
                                title = "Active",
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
                                }
                            )
                        }
                    }

                    // ── Empty filter result ──────────────────────
                    if (grouped.isEmpty && (selectedCategories.isNotEmpty() || selectedLocation != null)) {
                        item(key = "no_results") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No items match the selected filters.",
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
                        "Type at least 2 characters to search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Search bar ─────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search items, vendors, receipts…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = {
                if (query.isNotEmpty()) onQueryChange("") else onClose()
            }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search")
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface
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
                "No results for \"$query\"",
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
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), item.expiryDate)
    val urgency = urgencyOf(item.expiryDate)
    val urgencyTint = urgency.color()

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Show attachment thumbnail for OCR matches, category icon otherwise
            if (result.attachmentThumbnail != null) {
                AsyncImage(
                    model = result.attachmentThumbnail,
                    contentDescription = "Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
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

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.vendor ?: item.category.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Show OCR snippet for OCR matches
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

            Text(
                text = when {
                    daysLeft < 0 -> "Expired"
                    daysLeft == 0L -> "Due today"
                    else -> "$daysLeft d left"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = urgencyTint
            )
        }
    }
}

// ── Summary cards ───────────────────────────────────────────────────

@Composable
private fun SummaryCards(summary: HomeSummary) {
    // Only show when there's something worth reporting
    if (summary.moneyAtRisk <= 0.0 && summary.monthlySubscriptions <= 0.0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (summary.moneyAtRisk > 0.0) {
            SummaryCard(
                icon = Icons.Filled.TrendingDown,
                label = "At risk this month",
                value = summary.moneyAtRisk.toIndianCurrencyString(),
                subtitle = "${summary.expiringCount} item${if (summary.expiringCount != 1) "s" else ""} expiring",
                modifier = Modifier.weight(1f)
            )
        }
        if (summary.monthlySubscriptions > 0.0) {
            SummaryCard(
                icon = Icons.Filled.Autorenew,
                label = "Monthly subscriptions",
                value = summary.monthlySubscriptions.toIndianCurrencyString(),
                subtitle = "${summary.subscriptionCount} active",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

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
            ItemCategory.entries.forEach { category ->
                FilterChip(
                    selected = category in selectedCategories,
                    onClick = { onToggleCategory(category) },
                    label = { Text(category.displayName, style = MaterialTheme.typography.labelMedium) },
                    leadingIcon = if (category in selectedCategories) {
                        {
                            Icon(
                                categoryIcon(category),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null
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
                    selectedLocation ?: "All locations",
                    style = MaterialTheme.typography.labelMedium
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All locations") },
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
    onArchive: () -> Unit
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
                            RoundedCornerShape(12.dp)
                        )
                        .padding(start = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Archive,
                            contentDescription = "Archive",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Archive",
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}

// ── Item row ────────────────────────────────────────────────────────

@Composable
private fun ItemRow(item: Item, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), item.expiryDate)
    val urgency = urgencyOf(item.expiryDate)
    val urgencyTint = urgency.color()

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.vendor ?: item.category.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = when {
                    daysLeft < 0 -> "Expired"
                    daysLeft == 0L -> "Due today"
                    else -> "$daysLeft d left"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = urgencyTint
            )
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
                "Nothing tracked yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Add your first warranty, subscription,\nor service contract to keep it safe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
