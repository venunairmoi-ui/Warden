package com.venunair.wisma.ui.itemdetail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.venunair.wisma.capture.AttachmentStorage
import com.venunair.wisma.data.Attachment
import com.venunair.wisma.data.AttachmentMimeType
import com.venunair.wisma.data.AttachmentSource
import com.venunair.wisma.data.Item
import com.venunair.wisma.data.ItemCategory
import com.venunair.wisma.data.ItemRepository
import com.venunair.wisma.data.Region
import com.venunair.wisma.data.computeAmcServiceStatus
import com.venunair.wisma.data.ServiceEvent
import com.venunair.wisma.data.costLabel
import com.venunair.wisma.data.billingAmountLabel
import com.venunair.wisma.data.billingCycleLabel
import com.venunair.wisma.data.planTierLabel
import com.venunair.wisma.data.referenceNumberLabel
import com.venunair.wisma.R
import com.venunair.wisma.WardenApplication
import com.venunair.wisma.license.currentLicenseState
import com.venunair.wisma.ocr.recognizeText
import com.venunair.wisma.pdf.PdfPageRenderer
import com.venunair.wisma.ui.attachment.AttachmentThumbnailRow
import com.venunair.wisma.ui.attachment.AttachmentViewerDialog
import com.venunair.wisma.ui.common.LocalRegion
import com.venunair.wisma.ui.common.categoryIcon
import com.venunair.wisma.ui.common.decodeBitmapForOcr
import com.venunair.wisma.ui.common.toCurrencyString
import com.venunair.wisma.ui.common.toCurrencyStringOrDash
import com.venunair.wisma.ui.common.toDateString
import com.venunair.wisma.ui.common.toDateStringOrDash
import com.venunair.wisma.ui.common.toLocalDateFromUtcMillis
import com.venunair.wisma.ui.common.toUtcMillis
import com.venunair.wisma.ui.theme.ItemUrgency
import com.venunair.wisma.ui.theme.color
import com.venunair.wisma.ui.theme.urgencyOf
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    repository: ItemRepository,
    itemId: Long,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    // Kept distinct from onDeleted (same pop-back effect today) so this
    // screen's API stays self-documenting about which action triggered it --
    // matches onDeleted's own doc comment at the call site in WardenNavHost.
    onArchived: () -> Unit
) {
    val item by repository.observeItem(itemId).collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClaimInfo by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            // UI redesign pass, 2026-08-25: neutral chrome (background, not
            // primary-colored), matching product_details/code.html — same
            // reasoning as AddEditItemScreen's header. No title text either
            // (the mockup's top nav is icons-only): the item name now leads
            // the Hero card below instead of being said twice.
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
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
                    IconButton(
                        onClick = {
                            item?.let { current ->
                                scope.launch {
                                    repository.archiveItem(current.id)
                                    onArchived()
                                }
                            }
                        },
                        enabled = item != null
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = "Archive")
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
                snackbarHostState = snackbarHostState,
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
private fun ItemDetailContent(
    item: Item,
    repository: ItemRepository,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // Resolved here (composable scope) rather than inside handleReceiptCapture
    // below -- that's a suspend function, not a @Composable one, so
    // stringResource() can't be called from it directly. Same pattern as
    // AddEditItemScreen's ocrLockedMessage.
    val ocrLockedMessage = stringResource(R.string.ocr_locked_toast)
    var showMarkServicedConfirm by remember { mutableStateOf(false) }
    // Feedback, 2026-08-25: the confirm dialog used to hardcode BOTH dates
    // (service date = today, next due = +1yr from current expiry) with no
    // way to override either -- these three fields let the user enter the
    // actual service date and adjust the renewal date before confirming.
    // Keyed on showMarkServicedConfirm so every fresh open of the dialog
    // starts from clean defaults rather than carrying over a previous,
    // cancelled edit.
    var serviceDate by remember(showMarkServicedConfirm) { mutableStateOf(LocalDate.now()) }
    var serviceNewExpiry by remember(showMarkServicedConfirm) { mutableStateOf(item.expiryDate.plusYears(1)) }
    var serviceDatePickerTarget by remember(showMarkServicedConfirm) { mutableStateOf<ServiceDateTarget?>(null) }
    // AMC service-visit tracking, 2026-08-26: a separate dialog from Mark
    // serviced above -- logging a visit must never touch expiryDate (see
    // ItemRepository.logAmcService's doc comment).
    var showLogServiceDialog by remember { mutableStateOf(false) }
    var logServiceDate by remember(showLogServiceDialog) { mutableStateOf(LocalDate.now()) }
    var logServiceDatePickerOpen by remember(showLogServiceDialog) { mutableStateOf(false) }
    // AMC service-visit tracking follow-up, 2026-08-26: "allow attaching
    // the vendor-provided receipt" when logging a service -- one optional
    // receipt captured before Confirm, inserted with the new ServiceEvent's
    // id once Confirm actually creates that row (see PendingServiceReceipt's
    // doc comment). Keyed on showLogServiceDialog so a captured-but-
    // cancelled receipt never lingers into the next time the dialog opens.
    val context = LocalContext.current
    var pendingReceipt by remember(showLogServiceDialog) { mutableStateOf<PendingServiceReceipt?>(null) }
    var isProcessingReceipt by remember(showLogServiceDialog) { mutableStateOf(false) }

    suspend fun handleReceiptCapture(sourceUri: Uri, mimeType: AttachmentMimeType, source: AttachmentSource) {
        isProcessingReceipt = true
        try {
            val localUri = AttachmentStorage.copyToAppStorage(context, sourceUri, mimeType)
            val ocrBitmap: Bitmap? = when (mimeType) {
                AttachmentMimeType.PDF -> runCatching { PdfPageRenderer.renderPage(context, localUri) }.getOrNull()
                AttachmentMimeType.IMAGE -> decodeBitmapForOcr(context, localUri.toString())
            }
            val thumbnailUri = if (mimeType == AttachmentMimeType.PDF && ocrBitmap != null) {
                val thumbFile = File(AttachmentStorage.attachmentsDir(context), "${UUID.randomUUID()}_thumb.jpg")
                if (PdfPageRenderer.saveAsJpeg(ocrBitmap, thumbFile)) {
                    AttachmentStorage.uriForFile(context, thumbFile).toString()
                } else null
            } else null
            // Freemium gating (see LicenseState): once the 30-day OCR trial
            // lapses and Premium isn't unlocked, the receipt still attaches
            // -- it just stops auto-filling fields, same "best-effort"
            // degradation as OCR finding no usable text.
            val ocrUnlocked = (context.applicationContext as WardenApplication)
                .settingsRepository.currentLicenseState().isOcrUnlocked
            if (!ocrUnlocked && ocrBitmap != null) {
                scope.launch { snackbarHostState.showSnackbar(ocrLockedMessage) }
            }
            val rawOcrText = if (ocrUnlocked) {
                ocrBitmap?.let { bitmap ->
                    runCatching { recognizeText(bitmap) }.getOrNull()?.text?.takeIf { it.isNotBlank() }
                }
            } else {
                null
            }
            ocrBitmap?.recycle()
            // One receipt slot, not a list -- replacing a previous capture
            // (rather than appending) also means its now-unused backing
            // file needs cleaning up here, same as an explicit Remove tap.
            pendingReceipt?.let { previous ->
                AttachmentStorage.deleteBackingFile(context, previous.localUri)
                previous.thumbnailUri?.let { AttachmentStorage.deleteBackingFile(context, it) }
            }
            pendingReceipt = PendingServiceReceipt(localUri.toString(), mimeType, thumbnailUri, rawOcrText, source)
        } finally {
            isProcessingReceipt = false
        }
    }

    fun clearPendingReceipt() {
        pendingReceipt?.let {
            AttachmentStorage.deleteBackingFile(context, it.localUri)
            it.thumbnailUri?.let { t -> AttachmentStorage.deleteBackingFile(context, t) }
        }
        pendingReceipt = null
    }

    val receiptGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { picked -> scope.launch { handleReceiptCapture(picked, AttachmentMimeType.IMAGE, AttachmentSource.GALLERY_PICKER) } }
    }
    val receiptPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { picked -> scope.launch { handleReceiptCapture(picked, AttachmentMimeType.PDF, AttachmentSource.PDF_DOCUMENT_PICKER) } }
    }

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
        // ── Hero card ─────────────────────────────────────────────
        // Restyled from product_details/code.html's hero section (UI
        // redesign pass, 2026-08-25) — replaces the old separate
        // UrgencyBanner + Category/Vendor detail rows: category, name,
        // vendor, status pill and days-left all lead together now,
        // matching the mockup's centered hero treatment.
        HeroCard(item)

        // ── Warranty timeline ─────────────────────────────────────
        DetailSectionCard(title = "Warranty timeline") {
            TimelineRow(
                icon = Icons.Filled.CalendarToday,
                label = "Purchased on",
                value = item.purchaseDate.toDateStringOrDash(),
                isLast = false
            )
            TimelineRow(
                icon = Icons.Filled.Timeline,
                label = "Expires on",
                value = item.expiryDate.toDateStringOrDash(),
                isLast = true
            )
        }

        // ── Remaining fields ──────────────────────────────────────
        // Bug fix, 2026-09-01: "when I select Insurance, product details
        // still appear (serial number, model number, retailer, invoice
        // number)". Product-only fields never apply to Insurance or
        // Membership (see AddEditItemScreen's matching fix) -- gated here
        // too, defensively, so an item already saved with stray Product
        // data from before that fix (or the "Product or service" row
        // itself, always "Service" for these two categories anyway once
        // the form-side fix takes effect) stops showing it on re-open
        // without requiring the user to re-edit and re-save every such item.
        val showProductFields = item.category != ItemCategory.INSURANCE && item.category != ItemCategory.MEMBERSHIP
        val rows = buildList {
            if (showProductFields) {
                add(DetailRowData(Icons.Filled.Category, "Product or service", item.itemType.displayName))
            }
            add(DetailRowData(Icons.Filled.Payments, item.category.costLabel(), item.cost.toCurrencyStringOrDash()))
            add(DetailRowData(Icons.Filled.Numbers, item.category.referenceNumberLabel(), item.amcNumber ?: "—"))
            item.visitsIncluded?.let { add(DetailRowData(Icons.Filled.CheckCircle, "Visits included per year", it.toString())) }

            item.location?.let { add(DetailRowData(Icons.Filled.LocationOn, "Location", it)) }
            if (showProductFields) {
                item.serialNumber?.let { add(DetailRowData(Icons.Filled.QrCode, "Serial number", it)) }
                item.modelNumber?.let { add(DetailRowData(Icons.Filled.Numbers, "Model number", it)) }
                item.retailer?.let { add(DetailRowData(Icons.Filled.Store, "Retailer", it)) }
                item.invoiceNumber?.let { add(DetailRowData(Icons.Filled.Receipt, "Invoice number", it)) }
            }
            item.billingCycle?.let { add(DetailRowData(Icons.Filled.Repeat, item.category.billingCycleLabel(), it.displayName)) }
            item.billingAmount?.let { add(DetailRowData(Icons.Filled.Payments, item.category.billingAmountLabel(), it.toCurrencyStringOrDash())) }
            if (item.autoRenew) add(DetailRowData(Icons.Filled.Repeat, "Auto-renews", "Yes"))

            // Category-specific fields pass, 2026-08-30
            item.warrantyType?.let { add(DetailRowData(Icons.Filled.VerifiedUser, "Warranty type", it.displayName)) }
            item.nomineeName?.let { add(DetailRowData(Icons.Filled.Person, "Nominee", it)) }
            item.serviceProviderContact?.let { add(DetailRowData(Icons.Filled.SupportAgent, "Service provider contact", it)) }
            item.planTier?.let { add(DetailRowData(Icons.Filled.Star, item.category.planTierLabel(), it)) }
            item.membersCovered?.let { add(DetailRowData(Icons.Filled.Group, "Members covered", it.toString())) }

            add(DetailRowData(Icons.Filled.StickyNote2, "Notes", item.notes ?: "—"))
        }
        DetailSectionCard(title = "Additional details", contentPadding = 0.dp) {
            rows.forEachIndexed { index, row ->
                DetailRow(
                    icon = row.icon,
                    label = row.label,
                    value = row.value,
                    isLast = index == rows.lastIndex
                )
            }
        }

        // ── Total cost of ownership ──────────────────────────────
        TcoCostCard(item = item, serviceEvents = serviceEvents)

        // ── Service history timeline ─────────────────────────────
        if (serviceEvents.isNotEmpty()) {
            DetailSectionCard(title = "Service history") {
                serviceEvents.forEachIndexed { index, event ->
                    TimelineEntry(
                        event = event,
                        isLast = index == serviceEvents.lastIndex,
                        repository = repository,
                        onOpenReceipt = { viewerAttachment = it }
                    )
                }
            }
        }

        // ── Attachments ──────────────────────────────────────────
        if (attachments.isNotEmpty()) {
            DetailSectionCard(title = "Invoice available") {
                AttachmentThumbnailRow(
                    attachments = attachments,
                    onOpen = { viewerAttachment = it },
                    onDelete = null
                )
            }
        }

        // AMC service-visit tracking, 2026-08-26: split out of the single
        // "Mark serviced / renewed" action below -- for a multi-visit AMC,
        // logging each visit that way silently pushed expiryDate forward
        // once per visit (e.g. 2 visits/year would add 2 years of expiry
        // in one contract year). Renewal now happens only by editing
        // expiryDate itself (Edit screen); see Item.currentPeriodStart's
        // doc comment.
        if (item.category == ItemCategory.AMC) {
            val amcStatus = remember(item, serviceEvents) { computeAmcServiceStatus(item, serviceEvents) }
            if (amcStatus != null) {
                DetailSectionCard(title = "Service tracking") {
                    Text(
                        "${amcStatus.used} of ${amcStatus.total} services used this period",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        when {
                            amcStatus.remaining <= 0 -> "All included services used for this period"
                            amcStatus.nextExpectedDate != null ->
                                "Next service expected around ${amcStatus.nextExpectedDate.toDateString()}"
                            else ->
                                "${amcStatus.remaining} service${if (amcStatus.remaining != 1) "s" else ""} remaining this period"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(
                onClick = { showLogServiceDialog = true },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Log a service", modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            // Feedback, 2026-08-25: "allow tracking of service days or the
            // number of services added" -- both derived here, in-memory, from
            // the serviceEvents list ItemDetailContent already observes (Room-
            // persisted; see ItemRepository.markServiced), not a new stat
            // stored anywhere of its own. Hidden with zero service history
            // (there's nothing to report yet -- the button's label below
            // already covers that case).
            if (serviceEvents.isNotEmpty()) {
                val lastServiceDate = serviceEvents.maxOf { it.date }
                val daysSinceLastService = ChronoUnit.DAYS.between(lastServiceDate, LocalDate.now())
                Text(
                    "Serviced ${serviceEvents.size} time${if (serviceEvents.size != 1) "s" else ""} — " +
                        "last on ${lastServiceDate.toDateString()} " +
                        "($daysSinceLastService day${if (daysSinceLastService != 1L) "s" else ""} ago)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
            OutlinedButton(
                onClick = { showMarkServicedConfirm = true },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Mark serviced / renewed", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (showMarkServicedConfirm) {
        AlertDialog(
            onDismissRequest = { showMarkServicedConfirm = false },
            title = { Text("Mark as serviced?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Enter when it was serviced and the next due date. Defaults to today and " +
                            "one year from the current expiry — adjust either if this was a one-off " +
                            "repair rather than an annual renewal."
                    )
                    ServiceDateField(
                        label = "Service date",
                        date = serviceDate,
                        onClick = { serviceDatePickerTarget = ServiceDateTarget.SERVICE }
                    )
                    ServiceDateField(
                        label = "Next due date",
                        date = serviceNewExpiry,
                        onClick = { serviceDatePickerTarget = ServiceDateTarget.EXPIRY }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        repository.markServiced(item.id, serviceNewExpiry, serviceDate = serviceDate)
                    }
                    showMarkServicedConfirm = false
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showMarkServicedConfirm = false }) { Text("Cancel") }
            }
        )
    }

    serviceDatePickerTarget?.let { target ->
        val initial = when (target) {
            ServiceDateTarget.SERVICE -> serviceDate
            ServiceDateTarget.EXPIRY -> serviceNewExpiry
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { serviceDatePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = millis.toLocalDateFromUtcMillis()
                        when (target) {
                            ServiceDateTarget.SERVICE -> serviceDate = picked
                            ServiceDateTarget.EXPIRY -> serviceNewExpiry = picked
                        }
                    }
                    serviceDatePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { serviceDatePickerTarget = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // AMC service-visit tracking, 2026-08-26: "Log a service" dialog --
    // date only, deliberately no expiry field (see ItemRepository.logAmcService).
    if (showLogServiceDialog) {
        AlertDialog(
            onDismissRequest = { clearPendingReceipt(); showLogServiceDialog = false },
            title = { Text("Log a service?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Records this visit against your AMC's included services. This " +
                            "doesn't change the expiry date -- renew separately via Edit " +
                            "when the contract period ends."
                    )
                    ServiceDateField(
                        label = "Service date",
                        date = logServiceDate,
                        onClick = { logServiceDatePickerOpen = true }
                    )
                    // AMC service-visit tracking follow-up, 2026-08-26:
                    // "allow attaching the vendor-provided receipt".
                    // Gallery/PDF only for now -- live camera capture uses
                    // a dedicated full-screen route elsewhere in this app
                    // (see WardenNavHost's CameraCapture destination) that
                    // doesn't fit inside a modal dialog without a larger
                    // navigation change; a photo already in the gallery or
                    // a PDF receipt covers most vendor receipts in the
                    // meantime.
                    if (pendingReceipt == null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    receiptGalleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isProcessingReceipt
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Photo", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(
                                onClick = { receiptPdfLauncher.launch(arrayOf("application/pdf")) },
                                enabled = !isProcessingReceipt
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("PDF", modifier = Modifier.padding(start = 6.dp))
                            }
                            if (isProcessingReceipt) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (pendingReceipt?.mimeType == AttachmentMimeType.PDF) Icons.Filled.PictureAsPdf else Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Receipt attached",
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(onClick = { clearPendingReceipt() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove receipt")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isProcessingReceipt,
                    onClick = {
                        val receiptToAttach = pendingReceipt
                        scope.launch {
                            val eventId = repository.logAmcService(item.id, logServiceDate)
                            receiptToAttach?.let { receipt ->
                                repository.addAttachment(
                                    Attachment(
                                        itemId = item.id,
                                        serviceEventId = eventId,
                                        localFileUri = receipt.localUri,
                                        mimeType = receipt.mimeType,
                                        thumbnailUri = receipt.thumbnailUri,
                                        rawOcrText = receipt.rawOcrText,
                                        source = receipt.source
                                    )
                                )
                            }
                        }
                        showLogServiceDialog = false
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { clearPendingReceipt(); showLogServiceDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (logServiceDatePickerOpen) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = logServiceDate.toUtcMillis())
        DatePickerDialog(
            onDismissRequest = { logServiceDatePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { logServiceDate = it.toLocalDateFromUtcMillis() }
                    logServiceDatePickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { logServiceDatePickerOpen = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    viewerAttachment?.let { attachment ->
        AttachmentViewerDialog(attachment = attachment, onDismiss = { viewerAttachment = null })
    }
}

/**
 * AMC service-visit tracking follow-up, 2026-08-26: a single optional
 * receipt captured in the "Log a service" dialog before Confirm is
 * pressed. Mirrors AddEditItemScreen's own PendingAttachment (defer the
 * real Attachment insert until the row it points at actually exists --
 * there, the Item; here, the ServiceEvent), simplified to one slot
 * rather than a list since a single service visit has one receipt, not
 * a gallery of attachments.
 */
private data class PendingServiceReceipt(
    val localUri: String,
    val mimeType: AttachmentMimeType,
    val thumbnailUri: String?,
    val rawOcrText: String?,
    val source: AttachmentSource
)

// Mirrors AddEditItemScreen's own private DateFieldTarget/DateField pair --
// same bordered read-only-field-that-opens-a-DatePickerDialog pattern,
// duplicated locally rather than shared across files (see this file's
// other duplicated fieldset-card patterns; not worth extracting for a
// second use).
private enum class ServiceDateTarget { SERVICE, EXPIRY }

@Composable
private fun ServiceDateField(
    label: String,
    date: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date.toDateString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = "Pick date") },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
        )
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
                TcoRow("Purchase price", purchaseCost.toCurrencyString())
            }
            if (serviceCosts > 0.0) {
                TcoRow(
                    "Service costs (${serviceEvents.count { it.cost != null }} visit${if (serviceEvents.count { it.cost != null } != 1) "s" else ""})",
                    serviceCosts.toCurrencyString()
                )
            }
            if (purchaseCost > 0.0 && serviceCosts > 0.0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                )
                TcoRow("Total", totalCost.toCurrencyString(), bold = true)
            }

            // Per-year average if we know the purchase date
            item.purchaseDate?.let { purchaseDate ->
                val months = ChronoUnit.MONTHS.between(purchaseDate, LocalDate.now()).coerceAtLeast(1)
                val perYear = totalCost / months * 12
                Spacer(Modifier.height(4.dp))
                Text(
                    "≈ ${perYear.toCurrencyString()} / year",
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

/**
 * AMC service-visit tracking follow-up, 2026-08-26: [onOpenReceipt] reuses
 * the same [viewerAttachment] state and [AttachmentViewerDialog] the
 * item's own "Invoice available" section already uses -- one viewer, two
 * entry points, rather than a second dialog implementation.
 */
@Composable
private fun TimelineEntry(
    event: ServiceEvent,
    isLast: Boolean,
    repository: ItemRepository,
    onOpenReceipt: (Attachment) -> Unit
) {
    val receiptAttachments by repository.observeAttachmentsForServiceEvent(event.id).collectAsState(initial = emptyList())
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
                event.date.toDateString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            event.note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            event.cost?.let {
                Text(
                    it.toCurrencyString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            receiptAttachments.firstOrNull()?.let { receipt ->
                // Accessibility fix, 2026-09-15 (Phase 2): this explicit
                // 28dp override shrank the tap target well below even
                // Material3 TextButton's own ~40dp default (every other
                // TextButton in the app is left at that default) -- removed
                // so it's at least back in line with the rest of the app's
                // buttons; contentPadding alone still keeps it visually
                // compact in this dense service-history list.
                TextButton(
                    onClick = { onOpenReceipt(receipt) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("Receipt", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

// ── Hero card ────────────────────────────────────────────────────────

/**
 * Restyled from product_details/code.html's hero section (2026-08-25):
 * a bordered card with a urgency-tinted left accent bar (same
 * `Row(Modifier.height(IntrinsicSize.Min))` pattern HomeScreen's
 * ProductCard uses for the same reason — a bounded intrinsic-height
 * pass before a sibling `fillMaxHeight()` bar resolves correctly; this
 * Column sits in a plain scrollable Column rather than a LazyColumn
 * item, so it's not strictly required here, but keeping one accent-bar
 * technique across the app beats having two), and centered content:
 * category pill, name, vendor, a status pill (same OVERDUE/SOON/
 * COMFORTABLE → EXPIRED/DUE SOON/ACTIVE convention as HomeScreen's
 * `StatusPill`, redefined locally below since Kotlin `private` doesn't
 * cross files) and the days-left message — logic carried over verbatim
 * from the old `UrgencyBanner` this card replaces.
 */
@Composable
private fun HeroCard(item: Item) {
    val urgency = urgencyOf(item.expiryDate)
    val urgencyTint = urgency.color()
    val daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), item.expiryDate)
    val message = when {
        daysLeft < 0 -> "Expired ${-daysLeft} day${if (-daysLeft == 1L) "" else "s"} ago"
        daysLeft == 0L -> "Due today"
        else -> "$daysLeft day${if (daysLeft == 1L) "" else "s"} left"
    }

    Card(
        // Feedback, 2026-08-25: deliberately darker than the
        // DetailSectionCards below (surfaceContainerLowest vs. their
        // surfaceContainerLow) -- the Hero card leads the screen, so it
        // reads as a distinct, recessed "stage" the rest of the page's
        // cards float above, rather than blending in as just another
        // same-toned section.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(urgencyTint)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        // Retaxonomy, 2026-08-25: same "Category · Subcategory"
                        // combination ProductCard's tag now shows, so the two
                        // stay consistent.
                        listOfNotNull(item.category.displayName, item.subCategory)
                            .joinToString(" · ")
                            .uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    item.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    // Bug fix, 2026-09-16 (static audit finding): same
                    // unbounded-name issue as HomeScreen's product card --
                    // no cap here let a very long pasted name blow out this
                    // hero card's height.
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                item.vendor?.let { vendor ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        vendor,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(12.dp))
                HeroStatusPill(urgency = urgency, tint = urgencyTint)
                Spacer(Modifier.height(8.dp))
                // Feedback, 2026-08-25: was a flat onSurfaceVariant gray
                // regardless of urgency -- now tinted the same color as the
                // status pill above it, so an Active item's "N days left"
                // reads in green (via ItemUrgency.COMFORTABLE's new color),
                // matching an overdue item already reading in red the same
                // way via colorScheme.error.
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = urgencyTint
                )
            }
        }
    }
}

/**
 * Local copy of HomeScreen's `StatusPill` — same dot + uppercase-word
 * anatomy, same urgency→label convention. Duplicated rather than shared
 * because Kotlin `private` composables can't cross files; kept in sync
 * by convention (both map OVERDUE/SOON/COMFORTABLE the same way).
 */
@Composable
private fun HeroStatusPill(urgency: ItemUrgency, tint: Color) {
    val label = when (urgency) {
        ItemUrgency.OVERDUE -> "EXPIRED"
        ItemUrgency.SOON -> "DUE SOON"
        ItemUrgency.COMFORTABLE -> "ACTIVE"
    }
    Row(
        modifier = Modifier
            .background(tint.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(tint, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
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

    val claimText = buildClaimInfoText(item, LocalRegion.current)

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

// Plain (non-@Composable) function -- called from ClaimInfoBottomSheet,
// which is @Composable, but this itself isn't, so it can't read LocalRegion
// directly. Takes region explicitly instead; see the region-parameterized
// toCurrencyString(region)/toDateString(region) overloads in ui/common.
private fun buildClaimInfoText(item: Item, region: Region): String = buildString {
    appendLine("Product: ${item.name}")
    item.vendor?.let { appendLine("Brand / Vendor: $it") }
    item.category.let { appendLine("Type: ${it.displayName}") }
    item.subCategory?.let { appendLine("Subcategory: $it") }
    appendLine("Product or service: ${item.itemType.displayName}")
    appendLine("Expiry / Due: ${item.expiryDate.toDateString(region)}")
    item.purchaseDate?.let { appendLine("Purchased: ${it.toDateString(region)}") }
    item.amcNumber?.let { appendLine("${item.category.referenceNumberLabel()}: $it") }
    item.serialNumber?.let { appendLine("Serial No: $it") }
    item.modelNumber?.let { appendLine("Model No: $it") }
    item.retailer?.let { appendLine("Retailer: $it") }
    item.invoiceNumber?.let { appendLine("Invoice No: $it") }
    item.cost?.let { appendLine("${item.category.costLabel()}: ${it.toCurrencyString(region)}") }
    item.visitsIncluded?.let { appendLine("Visits included per year: $it") }
    item.billingCycle?.let { appendLine("${item.category.billingCycleLabel()}: ${it.displayName}") }
    item.billingAmount?.let { appendLine("${item.category.billingAmountLabel()}: ${it.toCurrencyString(region)}") }
    item.warrantyType?.let { appendLine("Warranty type: ${it.displayName}") }
    item.nomineeName?.let { appendLine("Nominee: $it") }
    item.serviceProviderContact?.let { appendLine("Service provider contact: $it") }
    item.planTier?.let { appendLine("${item.category.planTierLabel()}: $it") }
    item.membersCovered?.let { appendLine("Members covered: $it") }
    item.location?.let { appendLine("Location: $it") }
    item.notes?.let { appendLine("Notes: $it") }
}

// ── Shared helpers ──────────────────────────────────────────────────

/**
 * Grouped bordered "fieldset" card — mirrors AddEditItemScreen's
 * `FormSectionCard` exactly (bordered `surfaceContainerLow`,
 * `outlineVariant` border, `shapes.large`, uppercase label-small title
 * + divider), with one addition: a configurable [contentPadding] so
 * the "Additional details" card — whose `DetailRow` children already
 * carry their own internal padding and dividers — can zero it out and
 * avoid doubling up, while every other call site keeps the 16dp
 * default.
 */
@Composable
private fun DetailSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(contentPadding),
                verticalArrangement = if (contentPadding == 0.dp) Arrangement.Top else Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Warranty-timeline row — 48dp bordered icon-circle badge
 * (`surfaceContainerLow` fill, `primary`-tinted icon) with a connector
 * line down to the next row (omitted on the last one), plus a
 * label/value pair. Matches product_details/code.html's Warranty
 * Timeline card structure.
 */
@Composable
private fun TimelineRow(icon: ImageVector, label: String, value: String, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = if (isLast) 0.dp else 16.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

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
