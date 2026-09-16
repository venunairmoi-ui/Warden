package com.venunair.wisma.ui.additem

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.DocumentScanner
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venunair.wisma.R
import com.venunair.wisma.capture.AttachmentStorage
import com.venunair.wisma.data.Attachment
import com.venunair.wisma.data.AttachmentMimeType
import com.venunair.wisma.data.AttachmentSource
import com.venunair.wisma.data.BillingCycle
import com.venunair.wisma.data.Item
import com.venunair.wisma.data.ItemCategory
import com.venunair.wisma.data.AVAILABLE_REMINDER_OFFSETS
import com.venunair.wisma.data.DEFAULT_REMINDER_OFFSETS
import com.venunair.wisma.data.ItemRepository
import com.venunair.wisma.data.ItemStatus
import com.venunair.wisma.data.ItemType
import com.venunair.wisma.data.costLabel
import com.venunair.wisma.data.defaultItemType
import com.venunair.wisma.data.referenceNumberLabel
import com.venunair.wisma.data.WarrantyType
import com.venunair.wisma.data.billingAmountLabel
import com.venunair.wisma.data.billingCycleLabel
import com.venunair.wisma.data.billingSectionLabel
import com.venunair.wisma.data.planTierLabel
import com.venunair.wisma.data.subcategoriesFor
import com.venunair.wisma.WardenApplication
import com.venunair.wisma.license.currentLicenseState
import com.venunair.wisma.ocr.parseReceiptFields
import com.venunair.wisma.ocr.recognizeText
import com.venunair.wisma.pdf.PdfDecryptor
import com.venunair.wisma.pdf.PdfPageRenderer
import com.venunair.wisma.ui.attachment.AttachmentThumbnailRow
import com.venunair.wisma.ui.attachment.AttachmentViewerDialog
import com.venunair.wisma.ui.common.decodeBitmapForOcr
import com.venunair.wisma.ui.common.toDateString
import com.venunair.wisma.ui.common.toLocalDateFromUtcMillis
import com.venunair.wisma.ui.common.toRelativeDueString
import com.venunair.wisma.ui.common.toUtcMillis
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

// Bumped 3 -> 5, 2026-09-15: a real user shared a single PDF bundling a
// covering letter, a tax invoice, AND the actual policy as sequential
// pages -- a common shape for insurer-issued documents in general, not
// a one-off. The policy's own schedule/dates page could easily sit
// beyond page 3 once two unrelated cover pages precede it, so the old
// cap risked never even attempting OCR on the one page that actually
// has the expiry/policy-period info findExpiryDate looks for. Each
// extra page costs one more bitmap render + on-device ML Kit pass,
// which is why this stays a bounded cap rather than "OCR every page" --
// 5 is a deliberate, modest headroom increase, not "no limit".
private const val MAX_OCR_PAGES = 5

// Bug fix, 2026-09-16 (static audit finding): the Name field had no length
// cap at all -- a pasted multi-paragraph name saved fine (SQLite has no
// practical string limit) and then blew out HomeScreen's product-card and
// ItemDetailScreen's HeroCard title, both of which render it at a
// title/headline text style. 100 is generous for a real item name
// ("Whirlpool Washing Machine AMC" is ~30) while still bounding worst case.
private const val MAX_NAME_LENGTH = 100

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

/**
 * Password-protected PDF support: holds the already-copied-to-app-storage
 * local PDF ([localUri], a content:// Uri) that PdfDecryptor.isPasswordProtected
 * flagged, so the password dialog can retry it with PdfDecryptor once the
 * user enters a password -- see AddEditItemScreen's handleNewAttachment/
 * processLocalAttachment split.
 */
private data class PdfPasswordPrompt(
    val localUri: Uri,
    val source: AttachmentSource
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

    // Resolved here (composable scope) rather than at their call sites,
    // since those sites are non-composable lambdas/coroutines
    // (handleNewAttachment, cameraPermissionLauncher's callback,
    // pendingShareUri's LaunchedEffect) where stringResource() can't be
    // called directly.
    val ocrPrefillMessage = stringResource(R.string.ocr_prefill_toast)
    val ocrLockedMessage = stringResource(R.string.ocr_locked_toast)
    val cameraPermissionError = stringResource(R.string.error_camera_permission)
    val sharedItemDefaultName = stringResource(R.string.shared_item_default_name)

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

    // ── Category-specific fields (2026-08-30 pass) ───────────────────
    // Each is shown for exactly one category (see the AnimatedVisibility
    // gates below) and blanked to null on save for every other category --
    // see viewModel.save()'s call site.
    var nomineeName by remember(existingItem) { mutableStateOf(existingItem?.nomineeName ?: "") }
    var serviceProviderContact by remember(existingItem) { mutableStateOf(existingItem?.serviceProviderContact ?: "") }
    var warrantyType by remember(existingItem) { mutableStateOf(existingItem?.warrantyType) }
    var planTier by remember(existingItem) { mutableStateOf(existingItem?.planTier ?: "") }
    var membersCoveredText by remember(existingItem) { mutableStateOf(existingItem?.membersCovered?.toString() ?: "") }
    var warrantyTypeMenuExpanded by remember { mutableStateOf(false) }

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
    // Inline per-field validation: shown once the field has been visited
    // (name) or a save attempt was made (expiry, which has no "visit" of
    // its own since it's a picker, not typed text), then kept live from
    // then on so it clears itself as soon as the user fixes it -- instead
    // of the old "one generic error banner only after Save" behavior.
    // Bug fix, 2026-09-16, found via real on-device E2E testing (a static
    // read alone didn't catch this): Compose's onFocusChanged fires once
    // on a field's INITIAL composition reporting the baseline "not
    // focused" state, not only on a real focus->unfocus transition. A
    // naive `if (!it.isFocused) touched = true` therefore fires on that
    // very first spurious callback, before the user has ever touched the
    // field -- so a brand-new Add Item screen opened showing a red "Name
    // is required" error immediately, with zero interaction. Fixed by
    // gating on "was this field ever actually focused" first -- see
    // hasEverBeenFocused below and its three call sites (name, cost,
    // billing amount).
    var nameTouched by remember { mutableStateOf(false) }
    var nameHasBeenFocused by remember { mutableStateOf(false) }
    var attemptedSave by remember { mutableStateOf(false) }
    val nameError = (nameTouched || attemptedSave) && name.isBlank()
    val expiryError = attemptedSave && expiryDate == null

    // Bug fix, 2026-09-16 (static audit finding): Cost/Billing amount had
    // no validation at all -- a negative number parsed fine as a Double
    // and flowed straight into HomeViewModel's unguarded sums (moneyAtRisk,
    // totalRecurringMonthly), silently corrupting both the headline totals
    // and the per-category breakdown (which drops negative categories via
    // filterValues { it > 0.0 } while the headline sum still counts them).
    // Same isBlank-is-fine-but-touched-and-invalid-is-an-error shape as
    // nameError above -- optional fields stay optional, only a genuinely
    // invalid (unparseable or negative) non-blank entry blocks Save.
    var costTouched by remember { mutableStateOf(false) }
    var costHasBeenFocused by remember { mutableStateOf(false) }
    var billingAmountTouched by remember { mutableStateOf(false) }
    var billingAmountHasBeenFocused by remember { mutableStateOf(false) }
    val costInvalid = costText.isNotBlank() && (costText.toDoubleOrNull()?.let { it < 0 } ?: true)
    val billingAmountInvalid = billingAmountText.isNotBlank() &&
        (billingAmountText.toDoubleOrNull()?.let { it < 0 } ?: true)
    val costError = (costTouched || attemptedSave) && costInvalid
    val billingAmountError = (billingAmountTouched || attemptedSave) && billingAmountInvalid

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
        // Bug fix, 2026-09-01: "when I select Insurance, product details
        // still appear (serial number, model number, retailer, invoice
        // number)". Root cause -- itemType is deliberately independent of
        // category (see ItemType's doc comment: an RO-purifier AMC is a
        // Product, a housekeeping AMC is a Service) and is only ever
        // defaulted ONCE, when the form first opens. Switching the
        // Category dropdown afterward never re-derives it, so an item
        // started as Warranty (itemType defaults to Product) and then
        // switched to Insurance kept itemType = Product, and Product
        // details stayed visible -- unlike Warranty/AMC/Subscription,
        // there's no real-world case of an Insurance or Membership item
        // having a serial number/model number/retailer/invoice, so those
        // two categories don't get the Product/Service choice at all;
        // force it to Service whenever the category becomes one of them.
        if (category == ItemCategory.INSURANCE || category == ItemCategory.MEMBERSHIP) {
            itemType = ItemType.SERVICE
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

    // Password-protected PDF support -- see PdfDecryptor.kt and
    // PdfPasswordPrompt's own doc comment. pdfPasswordPrompt being non-null
    // is what drives the password AlertDialog below; pdfPasswordError shows
    // an inline "incorrect password" message without dismissing the dialog,
    // so the user can just retry without re-picking the file.
    var pdfPasswordPrompt by remember { mutableStateOf<PdfPasswordPrompt?>(null) }
    var pdfPasswordInput by remember { mutableStateOf("") }
    var pdfPasswordError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pendingAttachments.forEach { pending ->
                AttachmentStorage.deleteBackingFile(context, pending.localUri)
                pending.thumbnailUri?.let { AttachmentStorage.deleteBackingFile(context, it) }
            }
        }
    }

    // ── OCR + attachment handling (unchanged from Sprint 4) ─────────
    // Split from handleNewAttachment (below) so the password-unlock path
    // (submitPdfPassword) can feed an already-local, already-decrypted PDF
    // straight into the same OCR/thumbnail/attach logic every other
    // attachment goes through, without re-copying it or re-checking it for
    // a password it no longer has.
    suspend fun processLocalAttachment(localUri: Uri, mimeType: AttachmentMimeType, source: AttachmentSource) {
            // Freemium gating (see LicenseState): once the 30-day OCR trial
            // lapses and Premium isn't unlocked, attachments still save and
            // still get a thumbnail -- they just stop auto-filling fields,
            // same "best-effort" degradation as OCR finding no usable text.
            val ocrUnlocked = (context.applicationContext as WardenApplication)
                .settingsRepository.currentLicenseState().isOcrUnlocked
            val ocrBitmap: Bitmap? = when (mimeType) {
                AttachmentMimeType.PDF -> runCatching { PdfPageRenderer.renderPage(context, localUri) }.getOrNull()
                AttachmentMimeType.IMAGE -> decodeBitmapForOcr(context, localUri.toString())
            }
            if (!ocrUnlocked && ocrBitmap != null) {
                scope.launch { snackbarHostState.showSnackbar(ocrLockedMessage) }
            }

            var thumbnailUri = if (mimeType == AttachmentMimeType.PDF && ocrBitmap != null) {
                val thumbFile = File(AttachmentStorage.attachmentsDir(context), "${UUID.randomUUID()}_thumb.jpg")
                if (PdfPageRenderer.saveAsJpeg(ocrBitmap, thumbFile)) {
                    AttachmentStorage.uriForFile(context, thumbFile).toString()
                } else null
            } else null

            val ocrTextParts = mutableListOf<String>()
            if (ocrUnlocked) {
                ocrBitmap?.let { bitmap ->
                    runCatching { recognizeText(bitmap) }.getOrNull()?.text
                        ?.takeIf { it.isNotBlank() }
                        ?.let { ocrTextParts.add(it) }
                }
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
                    if (ocrUnlocked) {
                        runCatching { recognizeText(pageBitmap) }.getOrNull()?.text
                            ?.takeIf { it.isNotBlank() }
                            ?.let { ocrTextParts.add(it) }
                    }
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
                // Bug fix, 2026-09-15: see ParsedReceiptFields.expiryDate's
                // own doc comment -- OCR now actually looks for an expiry/
                // valid-until/policy-period date instead of this field
                // never being attempted at all.
                if (expiryDate == null && parsed.expiryDate != null) {
                    expiryDate = parsed.expiryDate; filledAnything = true
                }
                if (costText.isBlank() && parsed.cost != null) {
                    costText = parsed.cost.toString(); filledAnything = true
                }
                // Bug fix, 2026-09-15: a real user's shared insurance
                // policy never changed the category off its WARRANTY
                // default -- OCR was never taught to look at all. Category
                // has no "blank" state to gate on like the text fields
                // above, so "still a brand-new item, still sitting at the
                // untouched WARRANTY default" stands in for that same
                // "hasn't been filled in yet" check -- an existing item's
                // category was already a deliberate choice and is never
                // touched by adding a new attachment to it.
                if (existingItem == null && category == ItemCategory.WARRANTY && parsed.category != null) {
                    category = parsed.category; filledAnything = true
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
                    scope.launch { snackbarHostState.showSnackbar(ocrPrefillMessage) }
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
    }

    suspend fun handleNewAttachment(sourceUri: Uri, mimeType: AttachmentMimeType, source: AttachmentSource) {
        isProcessingAttachment = true
        try {
            // "pick the file name as default" -- same default the Share-
            // intent flow already applies (see the pendingShareUri
            // LaunchedEffect below), now also applied here so the in-app
            // PDF/gallery pickers behave the same way. Queried from
            // sourceUri (the ORIGINAL content:// Uri) before it's copied
            // below -- copyToAppStorage's local copy gets a random UUID
            // filename with no connection to what the user actually picked.
            // Gated to GALLERY_PICKER/PDF_DOCUMENT_PICKER only: CAMERA has
            // no pre-existing filename to reuse (CameraX writes straight
            // into a fresh UUID-named file), and SHARE already has its own
            // handling below with a friendlier fallback name.
            if (name.isBlank() && (source == AttachmentSource.GALLERY_PICKER || source == AttachmentSource.PDF_DOCUMENT_PICKER)) {
                queryDisplayName(context, sourceUri)?.let { fileName -> name = stripFileExtension(fileName) }
            }
            val localUri = AttachmentStorage.copyToAppStorage(context, sourceUri, mimeType)
            // Password-protected PDF support -- checked BEFORE the normal
            // OCR/thumbnail pipeline runs at all, since renderPage would
            // just throw SecurityException for this exact document shape
            // (see PdfDecryptor.isPasswordProtected's own doc comment for
            // why that's a reliable signal specifically for "encrypted",
            // not any other kind of bad-PDF failure). Puts up the password
            // dialog instead of silently producing an attachment with no
            // thumbnail and no OCR text, which is what used to happen here.
            if (mimeType == AttachmentMimeType.PDF && PdfDecryptor.isPasswordProtected(context, localUri)) {
                pdfPasswordPrompt = PdfPasswordPrompt(localUri, source)
                pdfPasswordInput = ""
                pdfPasswordError = null
                return
            }
            processLocalAttachment(localUri, mimeType, source)
        } finally {
            isProcessingAttachment = false
        }
    }

    suspend fun submitPdfPassword() {
        val pending = pdfPasswordPrompt ?: return
        isProcessingAttachment = true
        pdfPasswordError = null
        try {
            when (val result = PdfDecryptor.removePasswordProtection(context, pending.localUri, pdfPasswordInput)) {
                is PdfDecryptor.UnlockResult.Success -> {
                    pdfPasswordPrompt = null
                    pdfPasswordInput = ""
                    // The original encrypted local copy (pending.localUri,
                    // made by handleNewAttachment's copyToAppStorage before
                    // the password check) is now orphaned -- nothing ever
                    // references it once result.decryptedUri takes over, so
                    // it's cleaned up here rather than left as dead storage.
                    AttachmentStorage.deleteBackingFile(context, pending.localUri.toString())
                    processLocalAttachment(result.decryptedUri, AttachmentMimeType.PDF, pending.source)
                }
                PdfDecryptor.UnlockResult.WrongPassword -> {
                    pdfPasswordError = context.getString(R.string.pdf_password_incorrect)
                }
                PdfDecryptor.UnlockResult.Failure -> {
                    pdfPasswordError = context.getString(R.string.pdf_password_failure)
                }
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
                name = pendingShareDisplayName?.let(::stripFileExtension) ?: sharedItemDefaultName
            }
            // Bug fix, 2026-09-15: this used to blindly default expiryDate
            // to "today + 1 year" the moment a file was shared, BEFORE OCR
            // even ran -- a guess with no connection to anything on the
            // actual document, and one OCR could never correct afterward,
            // since the auto-fill below only ever fills fields that are
            // still blank. A real user hit this directly: shared a
            // multi-page insurance policy, and the saved expiry was just
            // "day I happened to share it + 1 year", not the policy's
            // real end date. Removed -- handleNewAttachment's OCR pass
            // now looks for an actual expiry-labeled date itself (see
            // ReceiptFieldParser.findExpiryDate), and if it finds nothing,
            // leaving the field blank (same as the camera/gallery/PDF-
            // picker attachment flows already did) is honest: the
            // required-field validation on Save then correctly asks the
            // user for the real date, instead of quietly shipping a wrong
            // one that looks like it was already filled in correctly.
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
        if (granted) onLaunchCamera() else error = cameraPermissionError
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
                            stringResource(if (itemId == null) R.string.add_item_title else R.string.edit_item_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            stringResource(if (itemId == null) R.string.add_item_subtitle else R.string.edit_item_subtitle),
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
            // ── Scan to auto-fill (moved to the top, 2026-09-01) ─────
            // Feedback: "a new user may enter information, then discover,
            // when trying to attach, that the system could have extracted
            // that data automatically." This exact block (buttons +
            // thumbnail row) used to sit at the very bottom of the form,
            // under "Notes & attachments" -- by the time a user scrolled
            // that far they'd usually already hand-typed everything OCR
            // could have filled for them (parseReceiptFields only fills
            // fields that are still blank -- see handleNewAttachment --
            // so nothing was ever silently overwritten, but the effort was
            // still wasted). Moved here, first thing on the screen, and
            // reframed from a passive "Attachments" label to an explicit
            // call-to-action so the scan option is the first thing anyone
            // sees, not a footnote discovered after the fact. Kept as one
            // section with the attachment thumbnails below it (rather than
            // splitting "scan" from "attachments") so this stays the one
            // place to both start a scan and review what's already
            // attached, instead of two separate UI locations for the same
            // underlying attachment list.
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Feedback, 2026-09-01: the original title + full
                    // explanatory sentence took too much vertical space and
                    // pushed the actual icons (the part people act on)
                    // further down -- shortened to one line that states the
                    // action itself, per "scan/pick/attach instead of
                    // typing" rather than a title plus a separate
                    // explanation of what that means.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.scan_prompt_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
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
                            Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.action_take_photo))
                        }
                        OutlinedIconButton(
                            onClick = {
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = stringResource(R.string.action_choose_gallery))
                        }
                        OutlinedIconButton(
                            onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = stringResource(R.string.action_attach_pdf))
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
            }

            // ── Core fields ─────────────────────────────────────────
            FormSectionCard(title = stringResource(R.string.section_item_information)) {
                OutlinedTextField(
                    value = name,
                    // Bug fix, 2026-09-16 (static audit finding): no cap
                    // existed at all -- see HomeScreen's product-card Text
                    // and ItemDetailScreen's HeroCard title, both of which
                    // needed a display-side maxLines/ellipsis fix for
                    // exactly this. This is the input-side half of that fix
                    // so the underlying data itself can't grow unbounded.
                    onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                    label = { Text(stringResource(R.string.field_name)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.error_name_required)) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                nameHasBeenFocused = true
                            } else if (nameHasBeenFocused) {
                                nameTouched = true
                            }
                        }
                )
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text(stringResource(R.string.field_vendor)) },
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
                        label = { Text(stringResource(R.string.field_category)) },
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
                            label = { Text(stringResource(R.string.field_subcategory)) },
                            placeholder = { Text(stringResource(R.string.placeholder_optional)) },
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
                // Bug fix, 2026-09-01: hidden entirely for Insurance and
                // Membership -- there's no real case of either being a
                // physical "Product" in this app's own field set (no
                // serial number/model number/retailer/invoice makes sense
                // for a policy or a membership), so showing a dropdown
                // that's forced to Service anyway (see the LaunchedEffect
                // above) would just be confusing dead UI.
                AnimatedVisibility(visible = category != ItemCategory.INSURANCE && category != ItemCategory.MEMBERSHIP) {
                ExposedDropdownMenuBox(
                    expanded = itemTypeMenuExpanded,
                    onExpandedChange = { itemTypeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = itemType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.field_product_or_service)) },
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
                }

                DateField(
                    label = stringResource(R.string.field_purchase_date),
                    date = purchaseDate,
                    onClick = { datePickerTarget = DateFieldTarget.PURCHASE },
                    modifier = Modifier.fillMaxWidth()
                )
                DateField(
                    label = stringResource(R.string.field_expiry_date),
                    date = expiryDate,
                    onClick = { datePickerTarget = DateFieldTarget.EXPIRY },
                    isError = expiryError,
                    supportingText = if (expiryError) {
                        stringResource(R.string.error_expiry_required)
                    } else {
                        expiryDate?.toRelativeDueString()
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text(stringResource(R.string.label_optional_suffix, category.costLabel())) },
                    isError = costError,
                    supportingText = if (costError) {
                        { Text(stringResource(R.string.error_invalid_amount)) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                costHasBeenFocused = true
                            } else if (costHasBeenFocused) {
                                costTouched = true
                            }
                        }
                )
                OutlinedTextField(
                    value = amcNumber,
                    onValueChange = { amcNumber = it },
                    label = { Text(stringResource(R.string.label_optional_suffix, category.referenceNumberLabel())) },
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
                        label = { Text(stringResource(R.string.field_visits_included)) },
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
                        label = { Text(stringResource(R.string.field_service_interval)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // AMC-only: who to actually call for a visit -- see
                // Item.serviceProviderContact's doc comment for why this is
                // distinct from the general Vendor field above.
                AnimatedVisibility(visible = category == ItemCategory.AMC) {
                    OutlinedTextField(
                        value = serviceProviderContact,
                        onValueChange = { serviceProviderContact = it },
                        label = { Text(stringResource(R.string.field_service_provider_contact)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Warranty-only: who's on the hook for a claim. Placed here
                // rather than in a Billing section since Warranty is the one
                // category that never shows Billing at all (showBilling
                // below).
                AnimatedVisibility(visible = category == ItemCategory.WARRANTY) {
                    ExposedDropdownMenuBox(
                        expanded = warrantyTypeMenuExpanded,
                        onExpandedChange = { warrantyTypeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = warrantyType?.displayName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.field_warranty_type)) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = warrantyTypeMenuExpanded,
                            onDismissRequest = { warrantyTypeMenuExpanded = false }
                        ) {
                            WarrantyType.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        warrantyType = option
                                        warrantyTypeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                // Insurance-only: who the payout goes to.
                AnimatedVisibility(visible = category == ItemCategory.INSURANCE) {
                    OutlinedTextField(
                        value = nomineeName,
                        onValueChange = { nomineeName = it },
                        label = { Text(stringResource(R.string.field_nominee)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.field_location)) },
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
            // Bug fix, 2026-09-01: also excludes Insurance/Membership
            // outright, on top of the LaunchedEffect(category) above that
            // forces itemType to Service for them -- belt-and-suspenders
            // against any item already saved with itemType = Product from
            // before this fix (e.g. an Insurance item created while this
            // bug was live), so re-opening it for edit can't still show
            // Product details either.
            val showProductDetails = itemType == ItemType.PRODUCT &&
                category != ItemCategory.INSURANCE && category != ItemCategory.MEMBERSHIP
            val showBilling = category != ItemCategory.WARRANTY

            AnimatedVisibility(visible = showProductDetails) {
                FormSectionCard(title = stringResource(R.string.section_product_details)) {
                    OutlinedTextField(
                        value = serialNumber,
                        onValueChange = { serialNumber = it },
                        label = { Text(stringResource(R.string.field_serial_number)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = modelNumber,
                        onValueChange = { modelNumber = it },
                        label = { Text(stringResource(R.string.field_model_number)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = retailer,
                        onValueChange = { retailer = it },
                        label = { Text(stringResource(R.string.field_retailer)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                        label = { Text(stringResource(R.string.field_invoice_number)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Billing section ──────────────────────────────────────
            // Category-specific fields pass, 2026-08-30: section title and
            // both field labels are now category-aware (billingSectionLabel/
            // billingCycleLabel/billingAmountLabel) -- Insurance reads
            // "Premium" / "Premium frequency" / "Premium paid", Membership
            // reads "Membership fee" / ".../ Membership fee amount", and
            // Subscription/AMC/Other keep the original "Billing" wording.
            // Same underlying billingCycle/billingAmount fields throughout;
            // see those functions' doc comments for why this is a relabel,
            // not a new column.
            AnimatedVisibility(visible = showBilling) {
                FormSectionCard(title = category.billingSectionLabel()) {
                    ExposedDropdownMenuBox(
                        expanded = billingCycleMenuExpanded,
                        onExpandedChange = { billingCycleMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = billingCycle?.displayName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(category.billingCycleLabel()) },
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
                        label = { Text(category.billingAmountLabel()) },
                        isError = billingAmountError,
                        supportingText = if (billingAmountError) {
                            { Text(stringResource(R.string.error_invalid_amount)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    billingAmountHasBeenFocused = true
                                } else if (billingAmountHasBeenFocused) {
                                    billingAmountTouched = true
                                }
                            }
                    )

                    // Subscription: plan name. Membership: tier name. Same
                    // field, category-aware label -- see planTierLabel.
                    AnimatedVisibility(visible = category == ItemCategory.SUBSCRIPTION || category == ItemCategory.MEMBERSHIP) {
                        OutlinedTextField(
                            value = planTier,
                            onValueChange = { planTier = it },
                            label = { Text(stringResource(R.string.label_optional_suffix, category.planTierLabel())) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Membership-only: how many people this covers.
                    AnimatedVisibility(visible = category == ItemCategory.MEMBERSHIP) {
                        OutlinedTextField(
                            value = membersCoveredText,
                            onValueChange = { membersCoveredText = it },
                            label = { Text(stringResource(R.string.field_members_covered)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

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
                            stringResource(R.string.field_auto_renew),
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
            FormSectionCard(title = stringResource(R.string.section_reminders)) {
                Text(
                    stringResource(R.string.reminders_subtitle),
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
                                    (if (days == 1) {
                                        stringResource(R.string.reminder_day)
                                    } else {
                                        stringResource(R.string.reminder_days, days)
                                    }).uppercase(),
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

            // ── Notes ─────────────────────────────────────────────────
            // Attachments moved to the top of the form, 2026-09-01 -- see
            // the "Scan instead of typing" card above. Kept as its own
            // section (not folded into "Item information") purely because
            // Notes is free text and reads better with room to breathe,
            // same reasoning as before -- it just no longer shares this
            // card with Attachments.
            FormSectionCard(title = stringResource(R.string.detail_notes)) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.field_notes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                stringResource(R.string.required_fields),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            // UI redesign pass: pill-shaped, filled with primaryContainer —
            // matches add_new_product/code.html's "Add Product ✓" CTA.
            Button(
                onClick = {
                    attemptedSave = true
                    when {
                        name.isBlank() -> Unit
                        expiryDate == null -> Unit
                        costInvalid -> Unit
                        billingAmountInvalid -> Unit
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
                                nomineeName = if (category == ItemCategory.INSURANCE) nomineeName.trim().ifBlank { null } else null,
                                serviceProviderContact = if (category == ItemCategory.AMC) serviceProviderContact.trim().ifBlank { null } else null,
                                warrantyType = if (category == ItemCategory.WARRANTY) warrantyType else null,
                                planTier = if (category == ItemCategory.SUBSCRIPTION || category == ItemCategory.MEMBERSHIP) planTier.trim().ifBlank { null } else null,
                                membersCovered = if (category == ItemCategory.MEMBERSHIP) membersCoveredText.toIntOrNull() else null,
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
                Text(
                    stringResource(if (itemId == null) R.string.add_item_title else R.string.save_changes_button),
                    style = MaterialTheme.typography.titleMedium
                )
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
                }) { Text(stringResource(R.string.ok_button)) }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text(stringResource(R.string.cancel_button)) }
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
            onApplyExpiryDate = { expiryDate = it },
            onApplyCategory = { category = it },
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
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDone()
                }) { Text(stringResource(R.string.discard_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.keep_editing_button)) }
            }
        )
    }

    // Password-protected PDF support -- see PdfPasswordPrompt/PdfDecryptor.
    // Cancel discards the encrypted local copy handleNewAttachment already
    // made (nothing else ever references it) rather than leaving it as
    // dead storage, same cleanup handleNewAttachment/DisposableEffect
    // already does for a fully-processed pending attachment.
    pdfPasswordPrompt?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                AttachmentStorage.deleteBackingFile(context, pending.localUri.toString())
                pdfPasswordPrompt = null
                pdfPasswordInput = ""
                pdfPasswordError = null
            },
            title = { Text(stringResource(R.string.pdf_password_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.pdf_password_dialog_message))
                    OutlinedTextField(
                        value = pdfPasswordInput,
                        onValueChange = { pdfPasswordInput = it; pdfPasswordError = null },
                        label = { Text(stringResource(R.string.pdf_password_field_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = pdfPasswordError != null,
                        supportingText = pdfPasswordError?.let { error -> { Text(error) } },
                        enabled = !isProcessingAttachment,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isProcessingAttachment) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { scope.launch { submitPdfPassword() } },
                    enabled = pdfPasswordInput.isNotBlank() && !isProcessingAttachment
                ) { Text(stringResource(R.string.unlock_button)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        AttachmentStorage.deleteBackingFile(context, pending.localUri.toString())
                        pdfPasswordPrompt = null
                        pdfPasswordInput = ""
                        pdfPasswordError = null
                    },
                    enabled = !isProcessingAttachment
                ) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}

// ── Helper composables ──────────────────────────────────────────────

private enum class DateFieldTarget { PURCHASE, EXPIRY }

private fun stripFileExtension(fileName: String): String = fileName.substringBeforeLast('.', fileName)

/**
 * Feedback, 2026-09-15: "pick the file name as default" -- Name already
 * defaulted from the shared file's display name on the Share-intent path
 * (ShareReceiverActivity queries this same DISPLAY_NAME column for that
 * flow), but the in-app "Attach PDF"/"Choose from gallery" pickers never
 * did the same thing, purely because handleNewAttachment never looked --
 * not a deliberate scope limit, just an inconsistency between the two ways
 * of getting a file into this screen. Not every content provider populates
 * DISPLAY_NAME, so a null return here is an ordinary, expected outcome
 * (same "found nothing" convention ReceiptFieldParser.kt documents for
 * OCR), not an error.
 */
private fun queryDisplayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    }
}.getOrNull()

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
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    Box(modifier = modifier) {
        OutlinedTextField(
            value = date?.toDateString() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = isError,
            supportingText = when {
                isError && supportingText != null -> {
                    { Text(supportingText, color = MaterialTheme.colorScheme.error) }
                }
                !isError && supportingText != null -> {
                    { Text(supportingText) }
                }
                else -> null
            },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.pick_date)) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick)
        )
    }
}
