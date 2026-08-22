package com.venunair.warden.ui.itemdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.venunair.warden.capture.AttachmentStorage
import com.venunair.warden.data.Attachment
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.ui.attachment.AttachmentThumbnailRow
import com.venunair.warden.ui.attachment.AttachmentViewerDialog
import com.venunair.warden.ui.common.categoryIcon
import com.venunair.warden.ui.common.toIndianCurrencyStringOrDash
import com.venunair.warden.ui.common.toIndianDateString
import com.venunair.warden.ui.common.toIndianDateStringOrDash
import com.venunair.warden.ui.theme.color
import com.venunair.warden.ui.theme.urgencyOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    repository: ItemRepository,
    itemId: Long,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val item by repository.observeItem(itemId).collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, enabled = item != null) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        item?.let { current ->
            ItemDetailContent(
                item = current,
                repository = repository,
                modifier = Modifier.padding(padding)
            )
        } ?: Box(modifier = Modifier.padding(padding)) { Text("Loading…") }
    }

    // Lives at this level (not inside ItemDetailContent) since the trigger
    // -- the TopAppBar's Delete icon -- lives here too, and this composable
    // already has `item` in scope from the same observeItem() collection
    // ItemDetailContent would otherwise need duplicated.
    if (showDeleteConfirm) {
        val toDelete = item
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this item?") },
            text = {
                Text(
                    (toDelete?.let { "\"${it.name}\"" } ?: "This item") +
                        " and any attached photos or documents will be permanently removed. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        toDelete?.let { target ->
                            scope.launch {
                                // Fetch attachments BEFORE deleting the item --
                                // Room's ON DELETE CASCADE removes their DB
                                // rows the instant deleteItem() runs, and
                                // there'd be nothing left to look up
                                // afterward to know which backing files to
                                // clean up. onDeleted() fires only once this
                                // whole sequence finishes, not right after
                                // launch{} starts, so navigating away can't
                                // cancel it partway through (rememberCoroutineScope's
                                // scope dies with this composable).
                                val itemAttachments = repository.getAttachments(target.id)
                                repository.deleteItem(target)
                                itemAttachments.forEach { attachment ->
                                    AttachmentStorage.deleteBackingFile(context, attachment.localFileUri)
                                    attachment.thumbnailUri?.let {
                                        AttachmentStorage.deleteBackingFile(context, it)
                                    }
                                }
                                onDeleted()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ItemDetailContent(item: Item, repository: ItemRepository, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var showMarkServicedConfirm by remember { mutableStateOf(false) }
    val attachments by repository.observeAttachments(item.id).collectAsState(initial = emptyList())
    var viewerAttachment by remember { mutableStateOf<Attachment?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // Same bug class as the earlier "Submit button missing" fix in
            // AddEditItemScreen: this screen's Scaffold applies edge-to-edge
            // inset padding (MainActivity calls enableEdgeToEdge()), but
            // that only positions content away from the system bars -- it
            // doesn't make an over-tall Column scrollable. Banner + 7-row
            // card + button is taller than the viewport on most phones, so
            // the button (last in the Column) was getting pushed off-screen
            // behind the gesture nav bar with no way to reach it. Reported
            // as "the Mark serviced button text is cut off at the bottom".
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            // Extra breathing room below the button so it clears the
            // gesture nav bar even once scrolled all the way down, rather
            // than sitting flush against it.
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UrgencyBanner(item)

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                DetailRow(icon = categoryIcon(item.category), label = "Category", value = item.category.name)
                DetailRow(icon = Icons.Filled.Storefront, label = "Vendor", value = item.vendor ?: "—")
                DetailRow(
                    icon = Icons.Filled.CalendarToday,
                    label = "Purchased",
                    value = item.purchaseDate.toIndianDateStringOrDash()
                )
                DetailRow(
                    icon = Icons.Filled.CalendarToday,
                    label = "Expires / next due",
                    value = item.expiryDate.toIndianDateStringOrDash()
                )
                DetailRow(icon = Icons.Filled.Payments, label = "Cost", value = item.cost.toIndianCurrencyStringOrDash())
                DetailRow(icon = Icons.Filled.Numbers, label = "AMC / policy number", value = item.amcNumber ?: "—")
                DetailRow(
                    icon = Icons.Filled.StickyNote2,
                    label = "Notes",
                    value = item.notes ?: "—",
                    isLast = true
                )
            }
        }

        if (attachments.isNotEmpty()) {
            Text("Attachments", style = MaterialTheme.typography.labelLarge)
            AttachmentThumbnailRow(
                attachments = attachments,
                onOpen = { viewerAttachment = it },
                // No delete affordance here, deliberately -- this screen is
                // view-only for attachments. Deleting one is an Edit-screen
                // action (AttachmentThumbnailRow there passes onDelete),
                // consistent with every other field on this screen only
                // being changeable via Edit, not from Detail directly.
                onDelete = null
            )
        }

        // Only "Mark serviced" here, not "Snooze" — snoozing only makes
        // sense in reference to one specific already-fired reminder (which
        // rule, due when), and that context only exists when the action
        // comes from the notification itself. Opening this screen doesn't
        // carry that context, so offering a Snooze button here would raise
        // "snooze WHICH reminder?" with no good answer.
        OutlinedButton(
            onClick = { showMarkServicedConfirm = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Mark serviced / renewed", modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (showMarkServicedConfirm) {
        val newExpiry = item.expiryDate.plusYears(1)
        AlertDialog(
            onDismissRequest = { showMarkServicedConfirm = false },
            title = { Text("Mark as serviced?") },
            text = {
                Text(
                    "This sets the next due date to ${newExpiry.toIndianDateString()} — one year " +
                        "from the current expiry. If that's not right for this item (e.g. a one-off " +
                        "repair rather than an annual renewal), open Edit afterward and correct it."
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.markServiced(item.id, newExpiry)
                    }
                    showMarkServicedConfirm = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showMarkServicedConfirm = false }) { Text("Cancel") }
            }
        )
    }

    viewerAttachment?.let { attachment ->
        AttachmentViewerDialog(attachment = attachment, onDismiss = { viewerAttachment = null })
    }
}

@Composable
private fun UrgencyBanner(item: Item) {
    val urgency = urgencyOf(item.expiryDate)
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), item.expiryDate)
    val message = when {
        daysLeft < 0 -> "Expired ${-daysLeft} day${if (-daysLeft == 1L) "" else "s"} ago"
        daysLeft == 0L -> "Due today"
        else -> "$daysLeft day${if (daysLeft == 1L) "" else "s"} left"
    }
    val tint = urgency.color()
    Surface(
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = message,
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (!isLast) {
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
