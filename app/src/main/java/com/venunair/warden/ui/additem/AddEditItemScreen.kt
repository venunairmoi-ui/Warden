package com.venunair.warden.ui.additem

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.ItemStatus
import com.venunair.warden.data.ItemType
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
    onPendingShareConsumed: () -> Unit = {}
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
    var itemType by remember(existingItem) { mutableStateOf(existingItem?.itemType ?: ItemType.PRODUCT) }
    var purchaseDate by remember(existingItem) { mutableStateOf(existingItem?.purchaseDate) }
    var expiryDate by remember(existingItem) { mutableStateOf(existingItem?.expiryDate) }
    var costText by remember(existingItem) { mutableStateOf(existingItem?.cost?.toString() ?: "") }
    var amcNumber by remember(existingItem) { mutableStateOf(existingItem?.amcNumber ?: "") }
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

    // ── UI state ────────────────────────────────────────────────────
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var itemTypeMenuExpanded by remember { mutableStateOf(false) }
    var billingCycleMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var datePickerTarget by remember { mutableStateOf<DateFieldTarget?>(null) }

    var savedItemId by remember(itemId) { mutableStateOf(itemId) }
    val attachmentsFlow = remember(savedItemId) {
        savedItemId?.let { repository.observeAttachments(it) } ?: flowOf(emptyList())
    }
    val attachments by attachmentsFlow.collectAsState(initial = emptyList())
    val pendingAttachments = remember { mutableStateListOf<PendingAttachment>() }
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
                if (filledAnything) {
                    Toast.makeText(context, "Some fields were filled in from the scan — please check them", Toast.LENGTH_LONG).show()
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
            handleNewAttachment(uriString.toUri(), mt, AttachmentSource.SHARE)
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

    // ── UI ──────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) "Add item" else "Edit item") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Core fields ─────────────────────────────────────────
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

            // Item type dropdown — drives which conditional sections appear
            ExposedDropdownMenuBox(
                expanded = itemTypeMenuExpanded,
                onExpandedChange = { itemTypeMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = itemType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Item type") },
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
                label = { Text("Cost (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amcNumber,
                onValueChange = { amcNumber = it },
                label = { Text("AMC / warranty / policy number (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location (e.g. Home, Office)") },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Product details section ─────────────────────────────
            AnimatedVisibility(visible = itemType == ItemType.PRODUCT) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Product details")
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

            // ── Billing section (subscription / service contract) ───
            AnimatedVisibility(
                visible = itemType == ItemType.SUBSCRIPTION || itemType == ItemType.SERVICE_CONTRACT
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Billing")

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

                    if (itemType == ItemType.SUBSCRIPTION) {
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
            }

            // ── Notes ───────────────────────────────────────────────
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // ── Attachments ─────────────────────────────────────────
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

            Text(
                "* Required",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

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
                                status = existingItem?.status ?: ItemStatus.ACTIVE,
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
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
        AttachmentViewerDialog(attachment = attachment, onDismiss = { viewerAttachment = null })
    }
}

// ── Helper composables ──────────────────────────────────────────────

private enum class DateFieldTarget { PURCHASE, EXPIRY }

private fun stripFileExtension(fileName: String): String = fileName.substringBeforeLast('.', fileName)

/** Thin visual divider with a label, used to separate conditional form
 *  sections (Product details, Billing) from the core fields above. */
@Composable
private fun SectionHeader(title: String) {
    Column {
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
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
