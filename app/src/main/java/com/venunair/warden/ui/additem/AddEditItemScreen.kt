package com.venunair.warden.ui.additem

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.venunair.warden.data.Item
import com.venunair.warden.data.ItemCategory
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.ItemStatus
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

/** How many pages of a PDF attachment get OCR'd, at most -- see the doc
 *  comment where this is used, in handleNewAttachment, for the real
 *  invoice that made a single-page-only scan insufficient. 3 covers the
 *  header-page / itemized-bill-page / footer-page shape that invoice (and
 *  most single-item Indian retail e-invoices) follow; revisit if
 *  real-device testing shows relevant fields routinely sitting further in. */
private const val MAX_OCR_PAGES = 3

/**
 * A photo/PDF attached to a brand-new item (itemId == null) before Save has
 * ever been pressed. Held purely in local Compose state -- NOT an
 * Attachment DB row yet, deliberately: an earlier version of this screen
 * auto-saved a draft Item the moment the first photo/PDF was attached (to
 * get a real id for Attachment's NOT NULL itemId foreign key), which meant
 * a share or a stray tap could silently create a permanent database record
 * before the user had reviewed or agreed to anything. Reported directly:
 * "it automatically extracts information and creates a record before I
 * save it, which is incorrect." Now nothing touches the database until the
 * user reviews the (possibly pre-filled) form and presses Save for real --
 * see the Save button below, which flushes every pending attachment into
 * the database once viewModel.save()'s onSaved callback hands back a real
 * item id.
 *
 * localUri already points at a real file in our own app storage (copied
 * there the moment it was attached, same as before) -- what's deferred is
 * only the Attachment DB row and, for a new item, the Item DB row, not the
 * file copy itself.
 */
private data class PendingAttachment(
    val id: Long,
    val localUri: String,
    val mimeType: AttachmentMimeType,
    val thumbnailUri: String?,
    val source: AttachmentSource,
    /** Sprint 4: raw ML Kit text, carried along so it still lands on the
     *  real Attachment row once this is flushed at Save -- see the Save
     *  button below. Purely a pass-through here; nothing in this screen
     *  re-reads it off a pending row (the parsed fields it produced were
     *  already applied to the form once, at attach time). */
    val rawOcrText: String?
)

/** A throwaway Attachment for display only, so a not-yet-saved
 *  PendingAttachment can flow through the exact same AttachmentThumbnailRow
 *  / AttachmentViewerDialog composables a real, DB-backed Attachment does,
 *  with zero changes to either. id is always negative (see pendingIdCounter
 *  below) specifically so it can never collide with a real Room
 *  autoGenerate id (always >= 1), which is what onDelete below uses to
 *  tell a pending row from a saved one. itemId is a meaningless placeholder
 *  here -- this object is never persisted, only rendered. */
private fun PendingAttachment.toDisplayAttachment() = Attachment(
    id = id,
    itemId = 0L,
    localFileUri = localUri,
    mimeType = mimeType,
    thumbnailUri = thumbnailUri,
    rawOcrText = rawOcrText,
    source = source
)

/**
 * Sprint 1 scope, revised: dates are now picked via a native Material3
 * DatePicker rather than typed as ISO text — the earlier free-text version
 * is exactly what produced the "typo'd the date format, then couldn't find
 * Save" bug. Picking a date can't produce an invalid format, which removes
 * that whole failure class rather than just relabeling it dd-MM-yyyy.
 *
 * Sprint 2 adds the three capture buttons here: Camera, Gallery, PDF. On an
 * existing item (itemId != null) each attach commits immediately, same as
 * always. On a brand-new item, every attach -- camera, gallery, PDF picker,
 * or a Sprint 3 share -- is held as a PendingAttachment (see above) until
 * the user actually presses Save; only then do the Item row and every
 * pending Attachment row get created together. Every capture path funnels
 * through handleNewAttachment so camera/gallery/PDF/share share one code
 * path from "got a Uri" onward, regardless of how that Uri arrived.
 *
 * Sprint 4 adds on-device OCR to that same shared path: every attach also
 * runs ML Kit text recognition against the photo (or, for a PDF, up to
 * MAX_OCR_PAGES of its rendered pages -- see that constant's doc comment
 * for why one page alone isn't always enough) and a small heuristic parser
 * (ocr/ReceiptFieldParser.kt) over the combined result, pre-filling
 * Vendor / Purchase date / Cost if they're still blank. This deliberately
 * does NOT open a separate "confirm scan"
 * screen the way the original sprint plan sketched -- since the
 * pending-attachment redesign above, this form already IS the confirm
 * step (nothing commits until Save either way), so a second screen would
 * just be one more tap to get to the same review the user is already
 * doing here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    repository: ItemRepository,
    itemId: Long?,
    onDone: () -> Unit,
    onLaunchCamera: () -> Unit = {},
    // Set by WardenNavHost from this screen's own back-stack-entry
    // SavedStateHandle once CameraCaptureScreen posts a result there and
    // pops back — see WardenNavHost.CAPTURED_URI_KEY.
    capturedUri: String? = null,
    onCapturedUriConsumed: () -> Unit = {},
    // Sprint 3: set by WardenNavHost the moment it navigates here in
    // response to ShareReceiverActivity handing off a share-sheet
    // image/PDF -- see WardenNavHost.PendingShare. mimeType arrives as
    // AttachmentMimeType.name() text rather than the enum for the same
    // reason it does on PendingShare itself.
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

    var name by remember(existingItem) { mutableStateOf(existingItem?.name ?: "") }
    var vendor by remember(existingItem) { mutableStateOf(existingItem?.vendor ?: "") }
    var category by remember(existingItem) { mutableStateOf(existingItem?.category ?: ItemCategory.WARRANTY) }
    var purchaseDate by remember(existingItem) { mutableStateOf(existingItem?.purchaseDate) }
    var expiryDate by remember(existingItem) { mutableStateOf(existingItem?.expiryDate) }
    var costText by remember(existingItem) { mutableStateOf(existingItem?.cost?.toString() ?: "") }
    var amcNumber by remember(existingItem) { mutableStateOf(existingItem?.amcNumber ?: "") }
    var notes by remember(existingItem) { mutableStateOf(existingItem?.notes ?: "") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Which field a tap on a date field should fill in, if any — null means
    // no dialog is showing. One dialog instance, reused for both fields,
    // rather than duplicating the DatePickerDialog block twice.
    var datePickerTarget by remember { mutableStateOf<DateFieldTarget?>(null) }

    // Real, persisted item id. Starts as itemId (already known on Edit) and
    // stays that way for the whole screen lifetime; on Add it starts null
    // and only becomes non-null once the real Save button succeeds (see the
    // Save button below) -- nothing sets it early anymore. Keyed on itemId
    // so a fresh screen instance for a different item doesn't inherit a
    // stale id.
    var savedItemId by remember(itemId) { mutableStateOf(itemId) }
    val attachmentsFlow = remember(savedItemId) {
        savedItemId?.let { repository.observeAttachments(it) } ?: flowOf(emptyList())
    }
    val attachments by attachmentsFlow.collectAsState(initial = emptyList())
    // Add-mode-only holding area -- see PendingAttachment's doc comment.
    // Always empty on Edit (savedItemId is never null there, so
    // handleNewAttachment's "else" branch below never queues anything).
    val pendingAttachments = remember { mutableStateListOf<PendingAttachment>() }
    var pendingIdCounter by remember { mutableStateOf(-1L) }
    var viewerAttachment by remember { mutableStateOf<Attachment?>(null) }
    // Sprint 4: an attach used to be a near-instant file copy; now it also
    // waits on an on-device OCR pass (typically well under a second, but
    // not zero) before the new thumbnail appears. True for the duration of
    // any handleNewAttachment call, purely so the small spinner next to
    // "Attachments" below can tell the user their tap registered instead of
    // it looking like nothing happened for a moment.
    var isProcessingAttachment by remember { mutableStateOf(false) }

    // Cleans up copied-but-never-saved backing files if this screen is left
    // without pressing Save (back button, process death, etc.) -- otherwise
    // every attach-then-abandon leaves an orphaned file in app storage
    // forever. Reads pendingAttachments fresh at dispose time via the
    // running composable's own state, not a stale snapshot. The Save button
    // below clears this list once its contents are safely in the database,
    // so a successful save never triggers this to delete what it just
    // wrote.
    DisposableEffect(Unit) {
        onDispose {
            pendingAttachments.forEach { pending ->
                AttachmentStorage.deleteBackingFile(context, pending.localUri)
                pending.thumbnailUri?.let { AttachmentStorage.deleteBackingFile(context, it) }
            }
        }
    }

    // Sprint 4: on-device OCR + a heuristic field parser, run against every
    // newly attached photo/PDF regardless of Add vs. Edit mode, or which of
    // camera/gallery/PDF-picker/share it came from -- see AddEditItemScreen's
    // own doc comment above for why every capture path funnels through this
    // one function. Only fills fields that are STILL BLANK (never overwrites
    // something the user already typed, and never touches Name -- see
    // ParsedReceiptFields' doc comment for why not), and the user reviews
    // whatever it filled in, same as everything else on this screen, before
    // Save actually commits anything.
    suspend fun handleNewAttachment(sourceUri: Uri, mimeType: AttachmentMimeType, source: AttachmentSource) {
        isProcessingAttachment = true
        try {
            val localUri = AttachmentStorage.copyToAppStorage(context, sourceUri, mimeType)

            // One Bitmap per attachment feeds BOTH the PDF thumbnail (PDF
            // only) and the OCR pass (both mime types) -- rendering/decoding
            // it once and reusing it, rather than doing that work twice for
            // two different outputs of the same underlying page/photo. See
            // PdfPageRenderer.saveAsJpeg's doc comment for the PDF half of
            // this.
            //
            // Wrapped in runCatching -- PdfPageRenderer.renderPage doesn't
            // catch its own PdfRenderer/ParcelFileDescriptor calls (see its
            // own source), so an unusual PDF that trips one of those up
            // would otherwise throw straight out of this whole function,
            // silently losing the attachment itself (the persist calls at
            // the bottom of this try block would never run) instead of just
            // losing the OCR pre-fill, which is the only thing this attach
            // flow ever promises. Same reasoning as the ocrBitmap?.let{}
            // wrapping recognizeText just below -- attaching the file is
            // this screen's real job; OCR pre-fill is best-effort on top.
            val ocrBitmap: Bitmap? = when (mimeType) {
                AttachmentMimeType.PDF -> runCatching { PdfPageRenderer.renderPage(context, localUri) }.getOrNull()
                AttachmentMimeType.IMAGE -> decodeBitmapForOcr(context, localUri.toString())
            }

            // var, not val -- see the multi-page loop below, which fills
            // this in from a later page if page 0's render didn't produce
            // one. Confirmed necessary from a real user's on-device report:
            // a real invoice's OCR pre-fill worked correctly (meaning its
            // page-2 text was read fine) while its thumbnail came back
            // empty -- exactly what page 0's render failing while later
            // pages succeed would produce, since this was the ONLY place
            // a thumbnail ever got generated from.
            var thumbnailUri = if (mimeType == AttachmentMimeType.PDF && ocrBitmap != null) {
                val thumbFile = File(AttachmentStorage.attachmentsDir(context), "${UUID.randomUUID()}_thumb.jpg")
                if (PdfPageRenderer.saveAsJpeg(ocrBitmap, thumbFile)) {
                    AttachmentStorage.uriForFile(context, thumbFile).toString()
                } else {
                    null
                }
            } else {
                null
            }

            // Best-effort -- a failure here (no text found, an unreadable
            // bitmap, whatever) just means no fields get pre-filled. The
            // attachment itself has already succeeded either way by this
            // point, and nothing below ever surfaces an OCR failure as an
            // error to the user.
            val ocrTextParts = mutableListOf<String>()
            ocrBitmap?.let { bitmap ->
                runCatching { recognizeText(bitmap) }.getOrNull()?.text
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ocrTextParts.add(it) }
            }
            ocrBitmap?.recycle()

            // PDF only: a real Reliance Digital e-invoice tested against this
            // screen had the store/billing header on page 1, but the actual
            // item name, purchase date, and total EXCLUSIVELY on page 2 -- a
            // common layout for Indian retail e-invoices (page 1: shipping/
            // billing details; page 2: itemized bill and total; further
            // pages: legal footer/QR code). Scanning page 1 alone, as this
            // screen originally did, can never find those fields no matter
            // how good the OCR or the parser is -- confirmed against that
            // exact invoice before writing this. Capped at MAX_OCR_PAGES so a
            // long multi-page warranty booklet doesn't turn one attach into a
            // dozen OCR passes; each extra page's bitmap is rendered, OCR'd,
            // and recycled one at a time -- never held alongside the others
            // -- to keep peak memory to a single page.
            if (mimeType == AttachmentMimeType.PDF) {
                // Both PdfPageRenderer calls wrapped for the same reason as
                // the page-0 render above -- pageCount() opens its own
                // ParcelFileDescriptor/PdfRenderer independently of the
                // page-0 call already made, so it's an equally real
                // opportunity for an uncaught throw to abort attachment
                // persistence entirely, not just this best-effort loop.
                val pageCount = runCatching { PdfPageRenderer.pageCount(context, localUri) }.getOrDefault(0)
                for (pageIndex in 1 until minOf(pageCount, MAX_OCR_PAGES)) {
                    val pageBitmap = runCatching { PdfPageRenderer.renderPage(context, localUri, pageIndex) }
                        .getOrNull() ?: continue
                    // Page 0's render failed to produce a thumbnail above --
                    // better a later page's thumbnail than none at all, and
                    // this is already a bitmap this loop rendered anyway.
                    // Only ever fires once: as soon as thumbnailUri is set,
                    // every later iteration skips this block.
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
                    vendor = parsed.vendor
                    filledAnything = true
                }
                if (purchaseDate == null && parsed.purchaseDate != null) {
                    purchaseDate = parsed.purchaseDate
                    filledAnything = true
                }
                if (costText.isBlank() && parsed.cost != null) {
                    costText = parsed.cost.toString()
                    filledAnything = true
                }
                if (filledAnything) {
                    Toast.makeText(
                        context,
                        "Some fields were filled in from the scan — please check them",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            val existingId = savedItemId
            if (existingId != null) {
                // Editing an existing item -- persist immediately, same as
                // this screen has always done. There's no "review before
                // saving" ambiguity here: the item itself already exists, this
                // is just adding one more attachment to it.
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
                // Brand-new item, not saved yet -- queue locally instead. See
                // PendingAttachment's doc comment for why.
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

    // CameraCaptureScreen posted its result Uri here and popped back to
    // this screen — process it exactly like a gallery pick, then clear the
    // key so a recomposition (e.g. rotating the phone) doesn't reprocess
    // the same capture a second time.
    LaunchedEffect(capturedUri) {
        capturedUri?.let { uriString ->
            handleNewAttachment(uriString.toUri(), AttachmentMimeType.IMAGE, AttachmentSource.CAMERA)
            onCapturedUriConsumed()
        }
    }

    // Sprint 3: ShareReceiverActivity already copied the shared file into
    // app storage before handing this Uri off (see that class's doc
    // comment), so from here it's exactly the same shape of work
    // capturedUri above does -- it becomes a PendingAttachment, same as a
    // manual camera/gallery/PDF attach on a new item. Name and Expiry get a
    // helpful pre-filled starting point (so there's something to review
    // rather than a blank form) rather than being left empty, but neither
    // is required for the attachment itself to show up -- reviewing and
    // correcting both, then pressing Save, is what actually commits
    // anything. Pre-filling here, directly, rather than via a remember{}
    // default keyed on pendingShareUri, is deliberate: keying state on a
    // value that gets nulled out again the moment onPendingShareConsumed()
    // fires would reset these fields right back to blank the instant the
    // attachment finishes attaching. A plain one-time assignment inside
    // this effect has no such reset risk.
    LaunchedEffect(pendingShareUri) {
        pendingShareUri?.let { uriString ->
            if (name.isBlank()) {
                name = pendingShareDisplayName?.let(::stripFileExtension) ?: "Shared item"
            }
            if (expiryDate == null) {
                // A clearly-placeholder default (matches the "+1 year" the
                // rest of this app already uses for renewal dates, e.g.
                // ItemDetailScreen's "Mark serviced") far enough out that it
                // can't trigger a false reminder (30/7/1-day thresholds)
                // before the user gets back here to correct it. The user
                // still has to review/fix this — nothing here claims to
                // know the real expiry.
                expiryDate = LocalDate.now().plusYears(1)
            }
            val mimeType = pendingShareMimeType
                ?.let { runCatching { AttachmentMimeType.valueOf(it) }.getOrNull() }
                ?: AttachmentMimeType.IMAGE
            handleNewAttachment(uriString.toUri(), mimeType, AttachmentSource.SHARE)
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

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (itemId == null) "Add item" else "Edit item") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                // Without this, 7 fields + dropdown + error text overflow a phone
                // screen once the keyboard is open, and the Save button — being
                // last — gets pushed below the visible area with no way to reach
                // it. This is almost certainly the "Submit button is missing"
                // bug: it's not gone, it's off-screen and unscrollable.
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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

            ExposedDropdownMenuBox(
                expanded = categoryMenuExpanded,
                onExpandedChange = { categoryMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = category.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                // No import for ExposedDropdownMenu — in this Material3 version it's a
                // member function of ExposedDropdownMenuBoxScope, not a top-level
                // function, so it only resolves via the implicit receiver of the
                // ExposedDropdownMenuBox { ... } lambda we're inside right now.
                // Importing it (as the scaffold originally did) breaks with
                // "Unresolved reference" since there's no such top-level symbol.
                ExposedDropdownMenu(
                    expanded = categoryMenuExpanded,
                    onDismissRequest = { categoryMenuExpanded = false }
                ) {
                    ItemCategory.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                category = option
                                categoryMenuExpanded = false
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
            // Icon-only, per user feedback -- the earlier icon+label version
            // ("Camera" / "Gallery" / "PDF") cramped three labels into a
            // third of the screen width each and wrapped mid-word on
            // narrower phones. OutlinedIconButton is the standard Material3
            // icon-only equivalent of OutlinedButton (same outlined style,
            // circular touch target); each Icon keeps a real
            // contentDescription now that there's no visible text carrying
            // that meaning for screen readers.
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
                // DB-backed (Edit mode) and locally-pending (Add mode)
                // attachments are mutually exclusive in practice -- Add
                // mode has no DB rows until Save, Edit mode never queues a
                // pending one -- so simply concatenating them for display
                // is safe; PendingAttachment's negative ids can't collide
                // with the positive ones Room assigns.
                attachments = attachments + pendingAttachments.map { it.toDisplayAttachment() },
                onOpen = { viewerAttachment = it },
                onDelete = { attachment ->
                    if (attachment.id < 0) {
                        // Pending -- plain local state removal, no DB
                        // involved yet.
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
                                purchaseDate = purchaseDate,
                                expiryDate = expiryDate!!,
                                cost = costText.toDoubleOrNull(),
                                amcNumber = amcNumber.trim().ifBlank { null },
                                notes = notes.trim().ifBlank { null },
                                status = existingItem?.status ?: ItemStatus.ACTIVE,
                                onSaved = { newId ->
                                    // Flush any pending attachments into the
                                    // database now that a real item id
                                    // exists, THEN navigate away -- onDone()
                                    // fires only once this whole sequence
                                    // finishes, not right after launch{}
                                    // starts, so leaving the screen can't
                                    // cancel it partway through
                                    // (rememberCoroutineScope's scope dies
                                    // with this composable). Clearing the
                                    // list before onDone() also stops the
                                    // DisposableEffect above from treating
                                    // what was just successfully saved as
                                    // abandoned and deleting its files.
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

private enum class DateFieldTarget { PURCHASE, EXPIRY }

/** "AMC_Contract.pdf" -> "AMC_Contract" -- a shared file's own name, minus
 *  its extension, is usually a more useful starting point for the Name
 *  field than making the user type one from scratch. Falls back to the
 *  original string unchanged if there's no '.' to split on. */
private fun stripFileExtension(fileName: String): String = fileName.substringBeforeLast('.', fileName)

/**
 * A read-only field styled exactly like the other OutlinedTextFields, but
 * tapping it opens a date picker instead of the keyboard. The invisible
 * clickable overlay (rather than relying on the text field's own click
 * handling) is the simplest reliable way to do this — it doesn't depend on
 * any Material3-internal wiring that could shift under us the way the
 * dropdown APIs already have twice in this project.
 */
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
