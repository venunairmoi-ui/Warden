package com.venunair.warden.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.seedSampleData
import com.venunair.warden.reminders.ReminderCheckWorker
import com.venunair.warden.ui.common.categoryIcon
import com.venunair.warden.ui.theme.color
import com.venunair.warden.ui.theme.urgencyOf
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
    val items by viewModel.items.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Warden") },
                actions = {
                    // Dev/testing aid: no real warranties on hand yet to
                    // exercise the app with. Inserts a handful of realistic
                    // items spanning every category and urgency band (see
                    // data/SampleData.kt) through the exact same saveItem()
                    // path a real Add uses — remove or gate behind a debug
                    // build check before Play Store (Sprint 7 polish),
                    // alongside the "check reminders now" action beside it.
                    IconButton(onClick = {
                        scope.launch {
                            repository.seedSampleData()
                            Toast.makeText(context, "Added sample items", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Biotech, contentDescription = "Load sample data")
                    }
                    // Dev/testing aid: the real check runs once a day via
                    // ReminderScheduler, which is too slow to iterate
                    // against while testing. Enqueuing a one-time job
                    // against the SAME ReminderCheckWorker class runs the
                    // identical doWork() logic immediately — remove or gate
                    // behind a debug build check before Play Store (Sprint
                    // 7 polish), it has no place in a shipped UI.
                    IconButton(onClick = {
                        WorkManager.getInstance(context)
                            .enqueue(OneTimeWorkRequestBuilder<ReminderCheckWorker>().build())
                        Toast.makeText(context, "Checking reminders…", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.NotificationsActive, contentDescription = "Check reminders now")
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
        if (items.isEmpty()) {
            EmptyState(modifier = Modifier.padding(padding).fillMaxSize())
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    ItemRow(item = item, onClick = { onOpenItem(item.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No items yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap + to add your first warranty, AMC, or document.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ItemRow(item: Item, onClick: () -> Unit) {
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), item.expiryDate)
    val urgency = urgencyOf(item.expiryDate)
    val urgencyTint = urgency.color()

    // The Card(onClick = ...) overload already makes the whole card
    // clickable with correct ripple/semantics — an extra .clickable()
    // modifier here would just stack a second, redundant click handler.
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
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
                    item.vendor ?: item.category.name,
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
