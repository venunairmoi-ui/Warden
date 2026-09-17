package com.venunair.wisma.ui.attachment

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.venunair.wisma.data.Attachment
import com.venunair.wisma.data.AttachmentMimeType
import com.venunair.wisma.data.ItemCategory
import com.venunair.wisma.ocr.ParsedReceiptFields
import com.venunair.wisma.ocr.parseReceiptFields
import com.venunair.wisma.pdf.PdfPageRenderer
import com.venunair.wisma.ui.common.rememberLocalThumbnail
import com.venunair.wisma.ui.common.toCurrencyString
import com.venunair.wisma.ui.common.toDateString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * One attachment as a square thumbnail: the image itself for an IMAGE
 * attachment, the cached page-1 render for a PDF (badged so it doesn't get
 * mistaken for a plain photo). Shared between AddEditItemScreen (where
 * onDelete is wired) and ItemDetailScreen (view-only, onDelete = null).
 */
@Composable
fun AttachmentThumbnail(
    attachment: Attachment,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val thumbUri = when (attachment.mimeType) {
        AttachmentMimeType.PDF -> attachment.thumbnailUri
        AttachmentMimeType.IMAGE -> attachment.localFileUri
    }
    val bitmap = thumbUri?.let { rememberLocalThumbnail(it) }

    // Accessibility fix, 2026-09-15 (Phase 2): this clickable region had no
    // accessible label at all -- both branches below pass
    // contentDescription = null on their own Image/Icon (correctly, since
    // they're purely decorative fill), so without this the whole tap
    // target was silently unlabeled for TalkBack. clickable() merges
    // descendant semantics into one node by default, but there was nothing
    // to merge from; this puts the label directly on the clickable node
    // itself instead.
    val thumbnailDescription = if (attachment.mimeType == AttachmentMimeType.PDF) {
        "View PDF attachment"
    } else {
        "View photo attachment"
    }
    // Feedback, 2026-09-17: "make the attachment thumbnail smaller" -- was
    // 88dp; the delete-button/PDF-badge overlays below are scaled down to
    // match rather than left at their old absolute sizes.
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = thumbnailDescription }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (attachment.mimeType == AttachmentMimeType.PDF) Icons.Filled.PictureAsPdf else Icons.Filled.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (attachment.mimeType == AttachmentMimeType.PDF) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(topStart = 8.dp),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    "PDF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        onDelete?.let { delete ->
            // Accessibility fix, 2026-09-15 (Phase 2): the old Surface(onClick
            // = ...) was itself sized to 22dp, well under the 48dp minimum
            // touch target. A plain IconButton (Compose's usual fix, and
            // what ItemDetailScreen's own "Remove receipt" button already
            // uses) reserves a full 48dp -- but tried here first and reverted:
            // on this 88dp thumbnail, a 48dp IconButton anchored TopEnd
            // covers more than half the thumbnail's area, and since it sits
            // ON TOP of the thumbnail's own clickable(onClick) region, that
            // touch area would silently swallow taps a user expects to open
            // the attachment, not delete it -- trading a hard-to-hit target
            // for an accidental-deletion hazard, worse than the original
            // problem. 36dp is a deliberate middle ground: a real, verified
            // improvement over 22dp, while keeping the delete control's
            // footprint from eating into the "view" tap target underneath
            // it. Plain clickable Box, not IconButton, so this size is
            // exact rather than fighting IconButton's own internal 48dp
            // minimum-size enforcement.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = delete),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove attachment",
                        tint = Color.White,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        }
    }
}

/** A horizontal strip of attachment thumbnails. Renders nothing when the list is empty, rather than an empty row taking up layout space. */
@Composable
fun AttachmentThumbnailRow(
    attachments: List<Attachment>,
    onOpen: (Attachment) -> Unit,
    onDelete: ((Attachment) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(attachments, key = { it.id }) { attachment ->
            AttachmentThumbnail(
                attachment = attachment,
                onClick = { onOpen(attachment) },
                onDelete = onDelete?.let { delete -> { delete(attachment) } }
            )
        }
    }
}

/**
 * Full-size view of one attachment. For a PDF this re-renders page 1 live
 * at full quality via PdfPageRenderer rather than reusing the small cached
 * thumbnail -- the thumbnail is deliberately low-res for fast list display,
 * this dialog is where a user actually reads the document.
 *
 * Also surfaces the raw ML Kit text this attachment's OCR pass produced
 * (Attachment.rawOcrText), collapsed behind a "Scanned text" toggle so it
 * doesn't clutter the common case of just viewing the photo/PDF. This
 * isn't a debug-only affordance -- it's the one place that shows exactly
 * what the on-device OCR actually read, which is what
 * ocr/ReceiptFieldParser.kt's Vendor/Date/Cost guesses are built from.
 * Reading it explains a wrong guess (a misread character, a line the
 * parser's keyword rules didn't anchor to) far better than staring at the
 * wrong pre-filled field alone would -- and the Copy button makes it easy
 * to hand that exact text along when reporting a bad guess.
 *
 * Feedback, 2026-08-25: re-running parseReceiptFields here and showing its
 * result as a labelled "Detected from scan" block -- previously the ONLY
 * place a user could see what OCR extracted was the attach-time auto-fill
 * (AddEditItemScreen.handleNewAttachment), which is silent and blank-
 * fields-only, so a user reopening an attachment later (or one whose
 * fields were already filled) had no way to see, correct or (re)apply
 * what was actually detected. This block is the "verify" step -- values
 * sit right above the raw scanned text below so a wrong guess is obvious
 * -- and each detected field gets its own "Apply" action, wired via the
 * five optional callbacks below. Deliberately per-field rather than one
 * bulk button: applying is an explicit overwrite (unlike the attach-time
 * auto-fill, which only ever touches blank fields), so letting the user
 * pick which fields to trust avoids clobbering a field that's already
 * correct with a bad OCR guess for just one other field. "Edit" is the
 * existing, un-duplicated form field itself -- Apply fills it, the user
 * edits it there with the full existing UI (including the date picker
 * already on the form) rather than a second editor living in this dialog.
 * All five callbacks default to null: AddEditItemScreen's call site wires
 * them, ItemDetailScreen's (view-only, no form to apply into) leaves them
 * unset and the block still shows the detected values with no Apply
 * buttons -- satisfying "so users know what to do" for a screen that has
 * nothing else for them to do here besides open Edit.
 */
@Composable
fun AttachmentViewerDialog(
    attachment: Attachment,
    onDismiss: () -> Unit,
    onApplyVendor: ((String) -> Unit)? = null,
    onApplyPurchaseDate: ((LocalDate) -> Unit)? = null,
    // Bug fix, 2026-09-15: same optional-callback pattern as every other
    // field here -- see ParsedReceiptFields.expiryDate's own doc comment
    // for why this didn't exist until now.
    onApplyExpiryDate: ((LocalDate) -> Unit)? = null,
    onApplyCategory: ((ItemCategory) -> Unit)? = null,
    onApplyCost: ((Double) -> Unit)? = null,
    onApplySerialNumber: ((String) -> Unit)? = null,
    onApplyModelNumber: ((String) -> Unit)? = null,
    // Feedback, 2026-08-26: retailer/invoiceNumber/referenceNumber added to
    // ParsedReceiptFields alongside the OCR improvements described there --
    // same optional-callback pattern as the five above (null on
    // ItemDetailScreen's view-only call site).
    onApplyRetailer: ((String) -> Unit)? = null,
    onApplyInvoiceNumber: ((String) -> Unit)? = null,
    onApplyReferenceNumber: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showRawOcrText by remember { mutableStateOf(false) }
    val parsed: ParsedReceiptFields? = remember(attachment.rawOcrText) {
        attachment.rawOcrText?.takeIf { it.isNotBlank() }?.let(::parseReceiptFields)
    }
    val canApplyAnything = onApplyVendor != null || onApplyPurchaseDate != null ||
        onApplyExpiryDate != null || onApplyCategory != null || onApplyCost != null ||
        onApplySerialNumber != null || onApplyModelNumber != null || onApplyRetailer != null ||
        onApplyInvoiceNumber != null || onApplyReferenceNumber != null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Feedback, 2026-09-17: "the attached file cannot be opened
                // normally -- you can see a snapshot but it cannot be
                // expanded... if the user has to view and verify the
                // attachment." The Image() below is a static, non-zoomable
                // render (page 1 only, for a PDF) -- fine for a quick
                // glance, not for actually reading a multi-page document or
                // zooming into fine print. This button hands the file to
                // whatever real PDF viewer / gallery app is installed via a
                // normal ACTION_VIEW, which supports pinch-zoom and (for
                // PDFs) paging through every page, not just the first.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { openAttachmentExternally(context, attachment) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Open", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                when (attachment.mimeType) {
                    AttachmentMimeType.IMAGE -> {
                        val bitmap = rememberLocalThumbnail(attachment.localFileUri, reqSizePx = 1200)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                                    .clickable { openAttachmentExternally(context, attachment) },
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().size(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    AttachmentMimeType.PDF -> {
                        val pageBitmap = produceState<ImageBitmap?>(initialValue = null, key1 = attachment.localFileUri) {
                            value = withContext(Dispatchers.IO) {
                                runCatching {
                                    PdfPageRenderer.renderPage(context, attachment.localFileUri.toUri())?.asImageBitmap()
                                }.getOrNull()
                            }
                        }.value
                        if (pageBitmap != null) {
                            Image(
                                bitmap = pageBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(pageBitmap.width.toFloat() / pageBitmap.height)
                                    .clickable { openAttachmentExternally(context, attachment) },
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(Modifier.fillMaxWidth().size(200.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                // This dialog's preview above is deliberately page-1-only
                // (PDF) / a single static render -- a real multi-page PDF
                // needs the "Open" button above (or tapping the preview
                // itself, wired the same way) to actually read past page 1.
                if (attachment.mimeType == AttachmentMimeType.PDF) {
                    Text(
                        "Tap the preview or \"Open\" above to view all pages, zoom, or search text in your PDF viewer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                parsed?.takeIf {
                    it.vendor != null || it.purchaseDate != null || it.expiryDate != null ||
                        it.category != null || it.cost != null || it.serialNumber != null ||
                        it.modelNumber != null || it.retailer != null || it.invoiceNumber != null ||
                        it.referenceNumber != null
                }?.let { fields ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Detected from scan", style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (canApplyAnything) {
                                "Compare these with the photo above, then tap Apply to fill in the item's fields — you can still edit them afterwards."
                            } else {
                                "Picked up from the scan. Open Edit on this item to add them to its details."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        fields.vendor?.let { value ->
                            DetectedFieldRow(
                                label = "Vendor",
                                value = value,
                                onApply = onApplyVendor?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Vendor applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.purchaseDate?.let { value ->
                            DetectedFieldRow(
                                label = "Purchase date",
                                value = value.toDateString(),
                                onApply = onApplyPurchaseDate?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Purchase date applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.expiryDate?.let { value ->
                            DetectedFieldRow(
                                label = "Expiry / next due date",
                                value = value.toDateString(),
                                onApply = onApplyExpiryDate?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Expiry date applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.category?.let { value ->
                            DetectedFieldRow(
                                label = "Category",
                                value = value.displayName,
                                onApply = onApplyCategory?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Category applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.cost?.let { value ->
                            DetectedFieldRow(
                                label = "Cost",
                                value = value.toCurrencyString(),
                                onApply = onApplyCost?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Cost applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.serialNumber?.let { value ->
                            DetectedFieldRow(
                                label = "Serial number",
                                value = value,
                                onApply = onApplySerialNumber?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Serial number applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.modelNumber?.let { value ->
                            DetectedFieldRow(
                                label = "Model number",
                                value = value,
                                onApply = onApplyModelNumber?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Model number applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.retailer?.let { value ->
                            DetectedFieldRow(
                                label = "Retailer",
                                value = value,
                                onApply = onApplyRetailer?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Retailer applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.invoiceNumber?.let { value ->
                            DetectedFieldRow(
                                label = "Invoice number",
                                value = value,
                                onApply = onApplyInvoiceNumber?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Invoice number applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        fields.referenceNumber?.let { value ->
                            DetectedFieldRow(
                                label = "Reference / policy / contract number",
                                value = value,
                                onApply = onApplyReferenceNumber?.let { apply ->
                                    {
                                        apply(value)
                                        Toast.makeText(context, "Reference number applied", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                attachment.rawOcrText?.takeIf { it.isNotBlank() }?.let { rawText ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clickable { showRawOcrText = !showRawOcrText },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Scanned text", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Icon(
                            if (showRawOcrText) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showRawOcrText) "Hide scanned text" else "Show scanned text"
                        )
                    }
                    if (showRawOcrText) {
                        Row(verticalAlignment = Alignment.Top) {
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(
                                    rawText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .heightIn(max = 220.dp)
                                        .verticalScroll(rememberScrollState())
                                        .padding(top = 4.dp)
                                )
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(rawText))
                                Toast.makeText(context, "Scanned text copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy scanned text")
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * Feedback, 2026-09-17: hands the attachment off to a real external viewer
 * (the device's default PDF app / gallery) via a normal ACTION_VIEW --
 * [Attachment.localFileUri] is already a FileProvider content:// Uri (see
 * AttachmentStorage.uriForFile's doc comment), never a raw file:// one, so
 * this works without any extra permission grant beyond the READ flag below.
 * A generic image MIME wildcard for IMAGE attachments (real files are
 * always .jpg per AttachmentStorage, but the wildcard is harmless and
 * future-proof) rather than hardcoding a single image type. Wrapped in a chooser
 * so the user picks an app even when several could handle it, with a
 * Toast fallback for the rare case nothing on the device can.
 */
private fun openAttachmentExternally(context: Context, attachment: Attachment) {
    val mimeType = when (attachment.mimeType) {
        AttachmentMimeType.PDF -> "application/pdf"
        AttachmentMimeType.IMAGE -> "image/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(attachment.localFileUri.toUri(), mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

/**
 * One labelled row inside the "Detected from scan" block above: a field
 * name + the value parseReceiptFields extracted, with an "Apply" text
 * button when the caller wired a callback for this specific field (null
 * on ItemDetailScreen's view-only call site -- see AttachmentViewerDialog's
 * doc comment).
 */
@Composable
private fun DetectedFieldRow(label: String, value: String, onApply: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (onApply != null) {
            TextButton(onClick = onApply) { Text("Apply") }
        }
    }
}
