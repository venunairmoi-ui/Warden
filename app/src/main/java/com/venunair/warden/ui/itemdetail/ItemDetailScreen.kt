package com.venunair.warden.ui.itemdetail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.venunair.warden.capture.AttachmentStorage
import com.venunair.warden.data.Attachment
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.ServiceEvent
import com.venunair.warden.ui.attachment.AttachmentThumbnailRow
import com.venunair.warden.ui.attachment.AttachmentViewerDialog
import com.venunair.warden.ui.common.categoryIcon
import com.venunair.warden.ui.common.toIndianCurrencyString
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
    var showClaimInfo by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.name ?: "Item") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClaimInfo = true }, enabled = item != null) {
                        Icon(Icons.Default.Info, contentDescription = "Claim info")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, enabled = item != null) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        item?.let { current ->
            ItemDetailContent(
                item = current,
                repository = repository,
                modifier = Modifier.padding(padding)
            )
        } ?: ShimmerLoading(modifier = Modifier.padding(padding))
    }

    // Sprint 8: Claim info bottom sheet
    if (showClaimInfo) {
        item?.let { current ->
            ClaimInfoBottomSheet(
                item = current,
                snackbarHostState = snackbarHostState,
                onDismiss = { showClaimInfo = false }
            )
        }
    }

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
    val serviceEvents by repository.observeServiceHistory(item.id).collectAsState(initial = emptyList())
    var viewerAttachment by remember { mutableStateOf<Attachment?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        UrgencyBanner(item)

        // ── Detail card ──────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            val rows = buildList {
                add(DetailRowData(categoryIcon(item.category), "Category", item.category.displayName))
                add(DetailRowData(Icons.Filled.Storefront, "Vendor", item.vendor ?: "—"))
                add(DetailRowData(Icons.Filled.CalendarToday, "Purchased", item.purchaseDate.toIndianDateStringOrDash()))
                add(DetailRowData(Icons.Filled.CalendarToday, "Expires / next due", item.expiryDate.toIndianDateStringOrDash()))
                add(DetailRowData(Icons.Filled.Payments, "Cost", item.cost.toIndianCurrencyStringOrDash()))
                add(DetailRowData(Icons.Filled.Numbers, "AMC / policy number", item.amcNumber ?: "—"))

                item.location?.let { add(DetailRowData(Icons.Filled.LocationOn, "Location", it)) }
                item.serialNumber?.let { add(DetailRowData(Icons.Filled.QrCode, "Serial number", it)) }
                item.modelNumber?.let { add(DetailRowData(Icons.Filled.Numbers, "Model number", it)) }
                item.retailer?.let { add(DetailRowData(Icons.Filled.Store, "Retailer", it)) }
                item.invoiceNumber?.let { add(DetailRowData(Icons.Filled.Receipt, "Invoice number", it)) }
                item.billingCycle?.let { add(DetailRowData(Icons.Filled.Repeat, "Billing cycle", it.displayName)) }
                item.billingAmount?.let { add(DetailRowData(Icons.Filled.Payments, "Billing amount", it.toIndianCurrencyStringOrDash())) }
                if (item.autoRenew) add(DetailRowData(Icons.Filled.Repeat, "Auto-renews", "Yes"))

                add(DetailRowData(Icons.Filled.StickyNote2, "Notes", item.notes ?: "—"))
            }
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                rows.forEachIndexed { index, row ->
                    DetailRow(
                        icon = row.icon,
                        label = row.label,
                        value = row.value,
                        isLast = index == rows.lastIndex
                    )
                }
            }
        }

        // ── Total cost of ownership ──────────────────────────────
        TcoCostCard(item = item, serviceEvents = serviceEvents)

        // ── Service history timeline ─────────────────────────────
        if (serviceEvents.isNotEmpty()) {
            ServiceHistoryTimeline(events = serviceEvents)
        }

        // ── Attachments ──────────────────────────────────────────
        if (attachments.isNotEmpty()) {
            Text("Attachments", style = MaterialTheme.typography.labelLarge)
            AttachmentThumbnailRow(
                attachments = attachments,
                onOpen = { viewerAttachment = it },
                onDelete = null
            )
        }

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

// ── Total cost of ownership card ────────────────────────────────────

@Composable
private fun TcoCostCard(item: Item, serviceEvents: List<ServiceEvent>) {
    val purchaseCost = item.cost ?: 0.0
    val serviceCosts = serviceEvents.mapNotNull { it.cost }.sum()
    val totalCost = purchaseCost + serviceCosts

    // Don't show the card if there's no cost data at all
    if (totalCost <= 0.0) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Total cost of ownership",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(12.dp))

            if (purchaseCost > 0.0) {
                TcoRow("Purchase price", purchaseCost.toIndianCurrencyString())
            }
            if (serviceCosts > 0.0) {
                TcoRow(
                    "Service costs (${serviceEvents.count { it.cost != null }} visit${if (serviceEvents.count { it.cost != null } != 1) "s" else ""})",
                    serviceCosts.toIndianCurrencyString()
                )
            }
            if (purchaseCost > 0.0 && serviceCosts > 0.0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                )
                TcoRow("Total", totalCost.toIndianCurrencyString(), bold = true)
            }

            // Per-year average if we know the purchase date
            item.purchaseDate?.let { purchaseDate ->
                val months = ChronoUnit.MONTHS.between(purchaseDate, LocalDate.now()).coerceAtLeast(1)
                val perYear = totalCost / months * 12
                Spacer(Modifier.height(4.dp))
                Text(
                    "≈ ${perYear.toIndianCurrencyString()} / year",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun TcoRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

// ── Service history timeline ────────────────────────────────────────

@Composable
private fun ServiceHistoryTimeline(events: List<ServiceEvent>) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Filled.Timeline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Service history",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        events.forEachIndexed { index, event ->
            TimelineEntry(
                event = event,
                isLast = index == events.lastIndex
            )
        }
    }
}

@Composable
private fun TimelineEntry(event: ServiceEvent, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline dot + connector line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        // Event content
        Column(modifier = Modifier.padding(start = 8.dp, bottom = if (isLast) 0.dp else 8.dp)) {
            Text(
                event.date.toIndianDateString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            event.cost?.let {
                Text(
                    it.toIndianCurrencyString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ── Urgency banner ──────────────────────────────────────────────────

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

// ── Shimmer loading placeholder ─────────────────────────────────────

@Composable
private fun ShimmerLoading(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(shimmerBrush, RoundedCornerShape(12.dp))
        )
        // Card placeholder — several rows
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                repeat(6) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(shimmerBrush, CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(12.dp)
                                    .background(shimmerBrush, RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(14.dp)
                                    .background(shimmerBrush, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }
        // Button placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(shimmerBrush, RoundedCornerShape(20.dp))
        )
    }
}

// ── Sprint 8: Claim info bottom sheet ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClaimInfoBottomSheet(
    item: Item,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()

    val claimText = buildClaimInfoText(item)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Claim information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))

            // Summary card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = claimText,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Claim Info", claimText))
                        scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy")
                }
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "${item.name} — Claim Info")
                            putExtra(Intent.EXTRA_TEXT, claimText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share claim info"))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share")
                }
            }
        }
    }
}

private fun buildClaimInfoText(item: Item): String = buildString {
    appendLine("Product: ${item.name}")
    item.vendor?.let { appendLine("Brand / Vendor: $it") }
    item.category.let { appendLine("Type: ${it.displayName}") }
    appendLine("Expiry / Due: ${item.expiryDate.toIndianDateString()}")
    item.purchaseDate?.let { appendLine("Purchased: ${it.toIndianDateString()}") }
    item.amcNumber?.let { appendLine("AMC / Policy No: $it") }
    item.serialNumber?.let { appendLine("Serial No: $it") }
    item.modelNumber?.let { appendLine("Model No: $it") }
    item.retailer?.let { appendLine("Retailer: $it") }
    item.invoiceNumber?.let { appendLine("Invoice No: $it") }
    item.cost?.let { appendLine("Cost: ${it.toIndianCurrencyString()}") }
    item.location?.let { appendLine("Location: $it") }
    item.notes?.let { appendLine("Notes: $it") }
}

// ── Shared helpers ──────────────────────────────────────────────────

private data class DetailRowData(
    val icon: ImageVector,
    val label: String,
    val value: String
)

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
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
