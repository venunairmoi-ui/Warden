package com.venunair.warden.ui.home

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.venunair.warden.BuildConfig
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.ItemRepository
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
    onOpenItem: (Long) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(repository))
    val grouped by viewModel.groupedItems.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val selectedCategories by viewModel.selectedCategories.collectAsState()
    val selectedLocation by viewModel.selectedLocation.collectAsState()
    val availableLocations by viewModel.availableLocations.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
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
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = {
                            scope.launch {
                                repository.seedSampleData()
                                Toast.makeText(context, "Added sample items", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Biotech, contentDescription = "Load sample data")
                        }
                        IconButton(onClick = {
                            WorkManager.getInstance(context)
                                .enqueue(OneTimeWorkRequestBuilder<ReminderCheckWorker>().build())
                            Toast.makeText(context, "Checking reminders…", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = "Check reminders now")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddItem,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") }
            )
        }
    ) { padding ->
        if (grouped.isEmpty && selectedCategories.isEmpty() && selectedLocation == null) {
            EmptyState(modifier = Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // clear FAB
            ) {
                // ── Summary cards ────────────────────────────────
                item(key = "summary") {
                    SummaryCards(summary = summary)
                }

                // ── Filter chips ─────────────────────────────────
                item(key = "filters") {
                    FilterBar(
                        selectedCategories = selectedCategories,
                        onToggleCategory = viewModel::toggleCategory,
                        selectedLocation = selectedLocation,
                        onSelectLocation = viewModel::selectLocation,
                        availableLocations = availableLocations
                    )
                }

                // ── Expiring soon ────────────────────────────────
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
                        AnimatedItemRow(item = item, index = index, onClick = { onOpenItem(item.id) })
                    }
                }

                // ── Renewal approaching ──────────────────────────
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
                        AnimatedItemRow(item = item, index = index, onClick = { onOpenItem(item.id) })
                    }
                }

                // ── Active ───────────────────────────────────────
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
                        AnimatedItemRow(item = item, index = index, onClick = { onOpenItem(item.id) })
                    }
                }

                // ── Empty filter result ──────────────────────────
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

// ── Animated item row ───────────────────────────────────────────────

@Composable
private fun AnimatedItemRow(item: Item, index: Int, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        delay(index.coerceAtMost(10) * 50L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(300)
        )
    ) {
        ItemRow(
            item = item,
            onClick = onClick,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
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
