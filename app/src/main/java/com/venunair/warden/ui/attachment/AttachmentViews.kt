package com.venunair.warden.ui.attachment

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.venunair.warden.data.Attachment
import com.venunair.warden.data.AttachmentMimeType
import com.venunair.warden.pdf.PdfPageRenderer
import com.venunair.warden.ui.common.rememberLocalThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    Box(
        modifier = modifier
            .size(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
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
            Surface(
                onClick = delete,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove attachment",
                    tint = Color.White,
                    modifier = Modifier.padding(3.dp)
                )
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
 */
@Composable
fun AttachmentViewerDialog(attachment: Attachment, onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        text = {
            when (attachment.mimeType) {
                AttachmentMimeType.IMAGE -> {
                    val bitmap = rememberLocalThumbnail(attachment.localFileUri, reqSizePx = 1200)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height),
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
                            modifier = Modifier.fillMaxWidth().aspectRatio(pageBitmap.width.toFloat() / pageBitmap.height),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().size(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    )
}
