package com.venunair.warden.ui.additem

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venunair.warden.capture.AttachmentStorage
import com.venunair.warden.data.Attachment
import com.venunair.warden.data.AttachmentMimeType
import com.venunair.warden.data.AttachmentSource
import com.venunair.warden.data.BillingCycle
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.AVAILABLE_REMINDER_OFFSETS
import com.venunair.warden.data.DEFAULT_REMINDER_OFFSETS
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.ItemStatus
import com.venunair.warden.data.ItemType
import com.venunair.warden.data.costLabel
import com.venunair.warden.data.defaultItemType
import com.venunair.warden.data.referenceNumberLabel
import com.venunair.warden.data.subcategoriesFor
import com.venunair.warden.ocr.parseReceiptFields
import com.venunair.warden.ocr.recognizeText
import com.venunair.warden.pdf.PdfPageRenderer
import com.venunair.warden.ui.attachment.AttachmentThumbnailRow
import com.venunair.warden.ui.attachment.AttachmentViewerDialog
import com.venunair.warden.ui.common.decodeBitmapForOcr
import com.venunair.warden.ui.common.toIndianDateString
import com.venunair.warden.ui.common.toLocalDateFromUtcMillis
import com.venunair.warden.ui.common.toUtcMillis
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.UUID

private class AddEditViewModelFactory(
    private val repository: ItemRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AddEditItemViewModel(repository) as T
}

private const val MAX_OCR_PAGES = 3

private data class PendingAttachment(
    val id: Long,
    val localUri: String,
    val mimeType: AttachmentMimeType,
    val thumbnailUri: String?,
    val source: AttachmentSource,
    val rawOcrText: String?
)

private fun PendingAttachment.toDisplayAttachment() = Attachment(
    id = id,
    itemId = 0L,
    localFileUri = localUri,
    mimeType = mimeType,
    thumbnailUri = thumbnailUri,
    rawOcrText = rawOcrText,
    source = source
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    repository: ItemRepository,
    itemId: Long?,
    onDone: () -> Unit,
    onLaunchCamera: () -> Unit = {},
    capturedUri: String? = null,
    onCapturedUriConsumed: () -> Unit = {},
    pendingShareUri: String? = null,
    pendingShareMimeType: String? = null,
    pendingShareDisplayName: String? = null,
    // Sprint 9: AttachmentSource.name() as a raw string — "SHARE" (default,
    // matches every pre-Sprint-9 caller) or "AUTO_DETECT". See
    // WardenNavHost's PendingShare.source doc comment for why this is a
    // raw string rather than the enum itself.
    pendingShareSource: String? = null,
    onPendingShareConsumed: () -> Unit = {},
    // Sprint 9: Settings' configurable reminder defaults, resolved by the
    // caller (WardenNavHost, which owns SettingsRepository) rather than
    // this screen reading DataStore directly — keeps this composable's
    // dependency surface to ItemRepository alone, same as before.
    defaultReminderOffsets: List<Int> = DEFAULT_REMINDER_OFFSETS
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val existingItem: Item? by if (itemId != null) {
        repository.observeItem(itemId).collectAsState(initial = null)
    } else {
        remember { mutableStateOf(null) }
    }

    val viewModel: AddEditItemViewModel = viewModel(
        factory = AddEditViewModelFactory(repository)
    )

    // ── Core fields ─────────────────────────────────────────────────
    var name by remember(existingItem) { mutableStateOf(existingItem?.name ?: "") }
    var vendor by remember(existingItem) { mutableStateOf(existingItem?.vendor ?: "") }
    var category by remember(existingItem) { mutableStateOf(existingItem?.category ?: ItemCategory.WARRANTY) }
    var subCategory by remember(existingItem) { mutableStateOf(existingItem?.subCategory ?: "") }
    // Product/Service reintroduction, 2026-08-25: defaulted from category
    // ONCE here (existingItem's own stored value on Edit; category's
    // typical case on Add) and never silently re-derived after that --
    // see ItemType's doc comment for why this field stays independent
    // rather than repeating the old redundancy bug. The user can always
    // change it via the dropdown below, including after switching category.
    var itemType by remember(existingItem) { mutableStateOf(existingItem?.itemType ?: category.defaultItemType()) }
    var purchaseDate by remember(existingItem) { mutableStateOf(existingItem?.purchaseDate) }
    var expiryDate by remember(existingItem) { mutableStateOf(existingItem?.expiryDate) }
    var costText by remember(existingItem) { mutableStateOf(existingItem?.cost?.toString() ?: "") }
    var amcNumber by remember(existingItem) { mutableStateOf(existingItem?.amcNumber ?: "") }
    var visitsIncludedText by remember(existingItem) { mutableStateOf(existingItem?.visitsIncluded?.toString() ?: "") }
    // AMC service-visit tracking, 2026-08-26: pre-filled from the stored
    // value (including a migration/repository-computed default) same as
    // every other optional field here -- always further user-editable,
    // never re-derived by this screen once shown.
    var serviceIntervalText by remember(existingItem) { mutableStateOf(existingItem?.serviceIntervalMonths?.toString() ?: "") }
    var notes by remember(existingItem) { mutableStateOf(existingItem?.notes ?: "") }
    var location by remember(existingItem) { mutableStateOf(existingItem?.location ?: "") }

    // ── Product-specific fields ─────────────────────────────────────
    var serialNumber by remember(existingItem) { mutableStateOf(existingItem?.serialNumber ?: "") }
    var modelNumber by remember(existingItem) { mutableStateOf(existingItem?.modelNumber ?: "") }
    var retailer by remember(existingItem) { mutableStateOf(existingItem?.retailer ?: "") }
    var invoiceNumber by remember(existingItem) { mutableStateOf(existingItem?.invoiceNumber ?: "") }

    // ── Billing fields (subscription / service contract) ────────────
    var billingCycle by remember(existingItem) { mutableStateOf(existingItem?.billingCycle) }
    var billingAmountText by remember(existingItem) { mutableStateOf(existingItem?.billingAmount?.toString() ?: "") }
    var autoRenew by remember(existingItem) { mutableStateOf(existingItem?.autoRenew ?: false) }

    // ── Sprint 8: Configurable reminder intervals ─────────────────
    val reminderChecked = remember { mutableStateMapOf<Int, Boolean>() }
    var reminderOffsetsLoaded by remember { mutableStateOf(false) }

    // Load existing reminder offsets for edit mode, or use defaults for add
    LaunchedEffect(existingItem) {
        if (!reminderOffsetsLoaded) {
            val offsets = if (itemId != null && itemId != 0L) {
                repository.getReminderOffsetsForItem(itemId)
            } else {
                // Sprint 9: Settings' configurable reminder defaults —
                // falls back to DEFAULT_REMINDER_OFFSETS only when the
                // caller didn't supply one (keeps every non-NavHost
                // preview/test call site working unchanged).
                defaultReminderOffsets
            }
            AVAILABLE_REMINDER_OFFSETS.forEach { offset ->
                reminderChecked[offset] = offset in offsets
            }
            reminderOffsetsLoaded = true
        }
    }

    // ── Sprint 8: Unsaved changes tracking ──────────────────────
    // Snapshot the initial values once existingItem arrives so we can
    // detect whether the user changed anything.
    val initialName = remember(existingItem) { existingItem?.name ?: "" }
    val initialVendor = remember(existingItem) { existingItem?.vendor ?: "" }
    val initialExpiryDate = remember(existingItem) { existingItem?.expiryDate }
    val initialCostText = remember(existingItem) { existingItem?.cost?.toString() ?: "" }
    val initialNotes = remember(existingItem) { existingItem?.notes ?: "" }

    var showDiscardDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // ── UI state ────────────────────────────────────────────────────
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var subCategoryMenuExpanded by remember { mutableStateOf(false) }
    var itemTypeMenuExpanded by remember { mutableStateOf(false) }
    var billingCycleMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var datePickerTarget by remember { mutableStateOf<DateFieldTarget?>(null) }

    // Retaxonomy, 2026-08-25: subcategory options depend on the chosen
    // category (subcategoriesFor). If the user switches category and the
    // current subCategory text isn't one of the new list (e.g. it was
    // "RO/Purifier" under AMC and they just switched to Warranty), clear
    // it rather than silently keep saving an option that's no longer
    // offered anywhere in the UI. A value that happens to still be valid
    // under the new category (e.g. "Electronics" is offered under both
    // Warranty and AMC) is left alone.
    LaunchedEffect(category) {
        if (subCategory.isNotEmpty() && subCategory !in subcategoriesFor(category)) {
            subCategory = ""
        }
    }

    var savedItemId by remember(itemId) { mutableStateOf(itemId) }
    val attachmentsFlow = remember(savedItemId) {
        savedItemId?.let { repository.observeAttachments(it) } ?: flowOf(emptyList())
    }
    val attachments by attachmentsFlow.collectAsState(initial = emptyList())
    val pendingAttachments = remember { mutableStateListOf<PendingAttachment>() }

    // isDirty must follow pendingAttachments declaration
    val isDirty = name != initialName || vendor != initialVendor ||
            expiryDate != initialExpiryDate || costText != initialCostText ||
            notes != initialNotes || pendingAttachments.isNotEmpty()
    var pendingIdCounter by remember { mutableStateOf(-1L) }
    var viewerAttachment by remember { mutableStateOf<Attachment?>(null) }
    var isProcessingAttachment by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            pendingAttachments.forEach { pending ->
                AttachmentStorage.deleteBackingFile(context, pending.localUri)
                pending.thumbnailUri?.let { AttachmentStorage.deleteBackingFile(context, it) }
            }
        }
    }

    // ── OCR + attachment handling (unchanged from Sprint 4) ─────────
    suspend fun handleNewAttachment(sourceUri: Uri, mimeType: AttachmentMimeType, source: AttachmentSource) {
        isProcessingAttachment = true
        try {
            val localUri = AttachmentStorage.copyToAppStorage(context, sourceUri, mimeType)

            val ocrBitmap: Bitmap? = when (mimeType) {
                AttachmentMimeType.PDF -> runCatching { PdfPageRenderer.renderPage(context, localUri) }.getOrNull()
                AttachmentMimeType.IMAGE -> decodeBitmapForOcr(context, localUri.toString())
            }

            var thumbnailUri = if (mimeType == AttachmentMimeType.PDF && ocrBitmap != null) {
                val thumbFile = File(AttachmentStorage.attachmentsDir(context), "${UUID.randomUUID()}_thumb.jpg")
                if (PdfPageRenderer.saveAsJpeg(ocrBitmap, thumbFile)) {
                    AttachmentStorage.uriForFile(context, thumbFile).toString()
                } else null
            } else null

            val ocrTextParts = mutableListOf<String>()
            ocrBitmap?.let { bitmap ->
                runCatching { recognizeText(bitmap) }.getOrNull()?.text
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ocrTextParts.add(it) }
            }
            ocrBitmap?.recycle()

            if (mimeType == AttachmentMimeType.PDF) {
                val pageCount = runCatching { PdfPageRenderer.pageCount(context, localUri) }.getOrDefault(0)
                for (pageIndex in 1 until minOf(pageCount, MAX_OCR_PAGES)) {
                    val pageBitmap = runCatching { PdfPageRenderer.renderPage(context, localUri, pageIndex) }
                        .getOrNull() ?: continue
                    if (thumbnailUri == null) {
                        val thumbFile = File(AttachmentStorage.attachmentsDir(context), "${UUID.randomUUID()}_thumb.jpg")
                        if (PdfPageRenderer.saveAsJpeg(pageBitmap, thumbFile)) {
                            thumbnailUri = AttachmentStorage.uriForFile(context, thumbFile).toString()
                        }
                    }
                    runCatching { recognizeText(pageBitmap) }.getOrNull()?.text
                        ?.takeIf { it.isNotBlank() }
                        ?.let { ocrTextParts.add(it) }
                    pageBitmap.recycle()
                }
            }

            val rawOcrText = ocrTextParts.joinToString("\n").takeIf { it.isNotBlank() }

            rawOcrText?.let { text ->
                val parsed = parseReceiptFields(text)
                var filledAnything = false
                if (vendor.isBlank() && parsed.vendor != null) {
                    vendor = parsed.vendor; filledAnything = true
                }
                if (purchaseDate == null && parsed.purchaseDate != null) {
                    purchaseDate = parsed.purchaseDate; filledAnything = true
                }
                if (costText.isBlank() && parsed.cost != null) {
                    costText = parsed.cost.toString(); filledAnything = true
                }
                if (serialNumber.isBlank() && parsed.serialNumber != null) {
                    serialNumber = parsed.serialNumber; filledAnything = true
                }
                if (modelNumber.isBlank() && parsed.modelNumber != null) {
                    modelNumber = parsed.modelNumber; filledAnything = true
                }
                // Feedback, 2026-08-26: retailer/invoiceNumber were added to
                // Item back in Sprint 6 but never wired into this auto-fill --
                // OCR now looks for them too (see ReceiptFieldParser), same
                // blank-fields-only rule as every field above.
                if (retailer.isBlank() && parsed.retailer != null) {
                    retailer = parsed.retailer; filledAnything = true
                }
                if (invoiceNumber.isBlank() && parsed.invoiceNumber != null) {
                    invoiceNumber = parsed.invoiceNumber; filledAnything = true
                }
                if (amcNumber.isBlank() && parsed.referenceNumber != null) {
                    amcNumber = parsed.referenceNumber; filledAnything = true
                }
                if (filledAnything) {
                    scope.launch { snackbarHostState.showSnackbar("Some fields were filled in from the scan — please check them") }
                }
            }

            val existingId = savedItemId
            if (existingId != null) {
                repository.addAttachment(
                    Attachment(
                        itemId = existingId,
                        localFileUri = localUri.toString(),
                        mimeType = mimeType,
                        thumbnailUri = thumbnailUri,
                        rawOcrText = rawOcrText,
                        source = source
                    )
                )
            } else {
                pendingAttachments.add(
                    PendingAttachment(
                        id = pendingIdCounter--,
                        localUri = localUri.toString(),
                        mimeType = mimeType,
                        thumbnailUri = thumbnailUri,
                        rawOcrText = rawOcrText,
                        source = source
                    )
                )
            }
        } finally {
            isProcessingAttachment = false
        }
    }

    LaunchedEffect(capturedUri) {
        capturedUri?.let { uriString ->
            handleNewAttachment(uriString.toUri(), AttachmentMimeType.IMAGE, AttachmentSource.CAMERA)
            onCapturedUriConsumed()
        }
    }

    LaunchedEffect(pendingShareUri) {
        pendingShareUri?.let { uriString ->
            if (name.isBlank()) {
                name = pendingShareDisplayName?.let(::stripFileExtension) ?: "Shared item"
            }
            if (expiryDate == null) {
                expiryDate = LocalDate.now().plusYears(1)
            }
            val mt = pendingShareMimeType
                ?.let { runCatching { AttachmentMimeType.valueOf(it) }.getOrNull() }
                ?: AttachmentMimeType.IMAGE
            val source = pendingShareSource
                ?.let { runCatching { AttachmentSource.valueOf(it) }.getOrNull() }
                ?: AttachmentSource.SHARE
            handleNewAttachment(uriString.toUri(), mt, source)
            onPendingShareConsumed()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { picked -> scope.launch { handleNewAttachment(picked, AttachmentMimeType.IMAGE, AttachmentSource.GALLERY_PICKER) } }
    }
    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { picked -> scope.launch { handleNewAttachment(picked, AttachmentMimeType.PDF, AttachmentSource.PDF_DOCUMENT_PICKER) } }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onLaunchCamera() else error = "Camera permission is needed to take a photo."
    }

    // ── Sprint 8: Unsaved changes back guard ──────────────────────
    BackHandler(enabled = isDirty) {
        showDiscardDialog = true
    }

    // ── UI ──────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            // UI redesign pass, 2026-08-25: neutral chrome (background, not
            // primary-colored), matching both add_new_product/code.html and
            // product_details/code.html — the mockups reserve the brand-blue
            // TopAppBar for the Dashboard tab and use a plain two-line
            // header (title + subtitle) for transactional/detail screens.
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (itemId == null) "Add item" else "Edit item",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            if (itemId == null) "Track a new item" else "Update the details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Core fields ─────────────────────────────────────────
            FormSectionCard(title = "Item information") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. LG Refrigerator) *") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Vendor / brand") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = category.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        ItemCategory.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    category = option
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Retaxonomy, 2026-08-25: subcategory dropdown, options
                // dependent on the category picked above (subcategoriesFor).
                // OTHER has no subcategory list -- it's a temporary
                // migration bucket, not a real category new items get
                // filed under -- so the field is skipped entirely rather
                // than shown empty/disabled.
                val subCategoryOptions = subcategoriesFor(category)
                if (subCategoryOptions.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = subCategoryMenuExpanded,
                        onExpandedChange = { subCategoryMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = subCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Subcategory") },
                            placeholder = { Text("Optional") },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = subCategoryMenuExpanded,
                            onDismissRequest = { subCategoryMenuExpanded = false }
                        ) {
                            subCategoryOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        subCategory = option
                                        subCategoryMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Product/Service reintroduction, 2026-08-25: independent
                // of Category (see ItemType's doc comment) -- decides
                // whether the Product details section below applies.
                ExposedDropdownMenuBox(
                    expanded = itemTypeMenuExpanded,
                    onExpandedChange = { itemTypeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = itemType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Product or service?") },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = itemTypeMenuExpanded,
                        onDismissRequest = { itemTypeMenuExpanded = false }
                    ) {
                        ItemType.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.displayName) },
                                onClick = {
                                    itemType = option
                                    itemTypeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                DateField(
                    label = "Purchase date (optional)",
                    date = purchaseDate,
                    onClick = { datePickerTarget = DateFieldTarget.PURCHASE },
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(
                    label = "Expiry / next due date *",
                    date = expiryDate,
                    onClick = { datePickerTarget = DateFieldTarget.EXPIRY },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("${category.costLabel()} (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amcNumber,
                    onValueChange = { amcNumber = it },
                    label = { Text("${category.referenceNumberLabel()} (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                // Product/Service reintroduction, 2026-08-25: AMC-only --
                // the contractual entitlement (e.g. "2 free visits/year"),
                // not a usage count. See Item.visitsIncluded's doc comment
                // for why "visits used" isn't tracked here too.
                AnimatedVisibility(visible = category == ItemCategory.AMC) {
                    OutlinedTextField(
                        value = visitsIncludedText,
                        onValueChange = { visitsIncludedText = it },
                        label = { Text("Visits included per year (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // AMC service-visit tracking, 2026-08-26: how many months
                // between services -- left blank here, the app fills a
                // default on save (period ÷ visits, evenly spread; see
                // ItemRepository.applyAmcPeriodTracking), but it's always
                // user-editable since this was explicitly asked to stay
                // configurable rather than fixed.
                AnimatedVisibility(visible = category == ItemCategory.AMC) {
                    OutlinedTextField(
                        value = serviceIntervalText,
                        onValueChange = { serviceIntervalText = it },
                        label = { Text("Service interval, months (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (e.g. Home, Office)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── Product details section ─────────────────────────────
            // Product/Service reintroduction, 2026-08-25: back to being
            // driven by ItemType, not Category -- Category answers "what
            // domain" (Warranty/AMC/...), ItemType answers "is there a
            // physical thing here", and those two questions don't always
            // have the same answer within a category (see ItemType's doc
            // comment). Billing stays Category-driven and independent of
            // ItemType -- a Service can still be billed (Netflix) or not
            // (a one-time labour warranty), so gating it on ItemType would
            // just recreate a different two-fields-same-question problem.
            val showProductDetails = itemType == ItemType.PRODUCT
            val showBilling = category != ItemCategory.WARRANTY

            AnimatedVisibility(visible = showProductDetails) {
                FormSectionCard(title = "Product details") {
                    OutlinedTextField(
                        value = serialNumber,
                        onValueChange = { serialNumber = it },
                        label = { Text("Serial number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelNumber,
                        onValueChange = { modelNumber = it },
                        label = { Text("Model number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = retailer,
                        onValueChange = { retailer = it },
                        label = { Text("Retailer / store") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                        label = { Text("Invoice number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Billing section ──────────────────────────────────────
            AnimatedVisibility(visible = showBilling) {
                FormSectionCard(title = "Billing") {
                    ExposedDropdownMenuBox(
                        expanded = billingCycleMenuExpanded,
                        onExpandedChange = { billingCycleMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = billingCycle?.displayName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Billing cycle") },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = billingCycleMenuExpanded,
                            onDismissRequest = { billingCycleMenuExpanded = false }
                        ) {
                            BillingCycle.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        billingCycle = option
                                        billingCycleMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = billingAmountText,
                        onValueChange = { billingAmountText = it },
                        label = { Text("Billing amount per cycle") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Retaxonomy follow-up, 2026-08-25: was gated to
                    // itemType == SUBSCRIPTION only, which meant an AMC
                    // contract or an Insurance/Membership item could never
                    // record auto-renew even though any of them plausibly
                    // can in real life. Now shown for every category that
                    // shows Billing at all -- the whole section is already
                    // gated by showBilling above.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Auto-renews",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = autoRenew,
                            onCheckedChange = { autoRenew = it }
                        )
                    }
                }
            }

            // ── Sprint 8: Reminder intervals ────────────────────────
            FormSectionCard(title = "Reminders") {
                Text(
                    "Get notified before expiry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AVAILABLE_REMINDER_OFFSETS.forEach { days ->
                        val selected = reminderChecked[days] == true
                        FilterChip(
                            selected = selected,
                            onClick = { reminderChecked[days] = !(reminderChecked[days] ?: false) },
                            label = {
                                Text(
                                    (if (days == 1) "1 day" else "$days days").uppercase(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            shape = CircleShape,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
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
            }

            // ── Notes + Attachments ──────────────────────────────────
            FormSectionCard(title = "Notes & attachments") {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Attachments", style = MaterialTheme.typography.labelLarge)
                    if (isProcessingAttachment) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedIconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) onLaunchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Take photo")
                    }
                    OutlinedIconButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = "Choose from gallery")
                    }
                    OutlinedIconButton(
                        onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Attach PDF")
                    }
                }
                AttachmentThumbnailRow(
                    attachments = attachments + pendingAttachments.map { it.toDisplayAttachment() },
                    onOpen = { viewerAttachment = it },
                    onDelete = { attachment ->
                        if (attachment.id < 0) {
                            pendingAttachments.removeAll { it.id == attachment.id }
                            AttachmentStorage.deleteBackingFile(context, attachment.localFileUri)
                            attachment.thumbnailUri?.let { AttachmentStorage.deleteBackingFile(context, it) }
                        } else {
                            scope.launch {
                                repository.deleteAttachment(attachment)
                                AttachmentStorage.deleteBackingFile(context, attachment.localFileUri)
                                attachment.thumbnailUri?.let { AttachmentStorage.deleteBackingFile(context, it) }
                            }
                        }
                    }
                )
            }

            Text(
                "* Required",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // UI redesign pass: pill-shaped, filled with primaryContainer —
            // matches add_new_product/code.html's "Add Product ✓" CTA.
            Button(
                onClick = {
                    when {
                        name.isBlank() -> error = "Name is required."
                        expiryDate == null -> error = "Pick an expiry / next due date."
                        else -> {
                            error = null
                            viewModel.save(
                                id = savedItemId ?: 0,
                                name = name.trim(),
                                vendor = vendor.trim().ifBlank { null },
                                category = category,
                                subCategory = subCategory.trim().ifBlank { null },
                                itemType = itemType,
                                purchaseDate = purchaseDate,
                                expiryDate = expiryDate!!,
                                cost = costText.toDoubleOrNull(),
                                amcNumber = amcNumber.trim().ifBlank { null },
                                notes = notes.trim().ifBlank { null },
                                serialNumber = serialNumber.trim().ifBlank { null },
                                modelNumber = modelNumber.trim().ifBlank { null },
                                retailer = retailer.trim().ifBlank { null },
                                invoiceNumber = invoiceNumber.trim().ifBlank { null },
                                location = location.trim().ifBlank { null },
                                billingCycle = billingCycle,
                                billingAmount = billingAmountText.toDoubleOrNull(),
                                autoRenew = autoRenew,
                                visitsIncluded = if (category == ItemCategory.AMC) visitsIncludedText.toIntOrNull() else null,
                                serviceIntervalMonths = if (category == ItemCategory.AMC) serviceIntervalText.toIntOrNull() else null,
                                status = existingItem?.status ?: ItemStatus.ACTIVE,
                                reminderOffsets = reminderChecked.filter { it.value }.keys.toList().sorted(),
                                onSaved = { newId ->
                                    scope.launch {
                                        pendingAttachments.forEach { pending ->
                                            repository.addAttachment(
                                                Attachment(
                                                    itemId = newId,
                                                    localFileUri = pending.localUri,
                                                    mimeType = pending.mimeType,
                                                    thumbnailUri = pending.thumbnailUri,
                                                    rawOcrText = pending.rawOcrText,
                                                    source = pending.source
                                                )
                                            )
                                        }
                                        pendingAttachments.clear()
                                        savedItemId = newId
                                        onDone()
                                    }
                                }
                            )
                        }
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(if (itemId == null) "Add item" else "Save changes", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Check, contentDescription = null)
            }

            // Bottom spacer for comfortable scrolling above the gesture bar
            Spacer(Modifier.height(16.dp))
        }
    }

    datePickerTarget?.let { target ->
        val initial = when (target) {
            DateFieldTarget.PURCHASE -> purchaseDate
            DateFieldTarget.EXPIRY -> expiryDate
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (initial ?: LocalDate.now()).toUtcMillis()
        )
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val picked = millis.toLocalDateFromUtcMillis()
                        when (target) {
                            DateFieldTarget.PURCHASE -> purchaseDate = picked
                            DateFieldTarget.EXPIRY -> expiryDate = picked
                        }
                    }
                    datePickerTarget = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    viewerAttachment?.let { attachment ->
        AttachmentViewerDialog(
            attachment = attachment,
            onDismiss = { viewerAttachment = null },
            // Feedback, 2026-08-25: lets the "Detected from scan" block in
            // the viewer (re-run on any attachment, not just the one just
            // captured) push a value into this form's own fields on demand
            // -- an explicit, user-reviewed overwrite, distinct from
            // handleNewAttachment's silent blank-fields-only auto-fill above.
            onApplyVendor = { vendor = it },
            onApplyPurchaseDate = { purchaseDate = it },
            onApplyCost = { costText = it.toString() },
            onApplySerialNumber = { serialNumber = it },
            onApplyModelNumber = { modelNumber = it },
            onApplyRetailer = { retailer = it },
            onApplyInvoiceNumber = { invoiceNumber = it },
            onApplyReferenceNumber = { amcNumber = it }
        )
    }

    // Sprint 8: discard unsaved changes confirmation
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to go back?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDone()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }
}

// ── Helper composables ──────────────────────────────────────────────

private enum class DateFieldTarget { PURCHASE, EXPIRY }

private fun stripFileExtension(fileName: String): String = fileName.substringBeforeLast('.', fileName)

/**
 * Grouped form section — the "fieldset" card pattern from
 * add_new_product/code.html (UI redesign pass, 2026-08-25): a bordered,
 * rounded card with an uppercase micro-label legend and a hairline
 * divider under it, replacing the old flat divider-plus-label
 * [SectionHeader]. Used for every logical group on this screen (core
 * fields, the two conditional sections, reminders, notes+attachments)
 * so the form reads as distinct steps rather than one long field list.
 */
@Composable
private fun FormSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = date?.toIndianDateString() ?: "",
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
