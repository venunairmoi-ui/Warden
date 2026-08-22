package com.venunair.warden.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a stored attachment Uri (always one of our own FileProvider
 * content:// URIs -- see capture/AttachmentStorage) as a downsampled
 * ImageBitmap, decoded off the main thread and sized for a thumbnail
 * rather than the original. A camera photo straight off a modern phone is
 * routinely several MB, and decoding that at full resolution for a small
 * list thumbnail is wasted work and wasted memory. No Coil/Glide in this
 * project (no-new-dependency-cost preference from the spec, and
 * personal-scale item counts don't need a caching image-loading pipeline)
 * -- this is deliberately the simplest thing that works at that scale, not
 * a general-purpose image loader. reqSizePx defaults to a list-thumbnail
 * size; pass a larger value for a bigger preview (e.g. the full-size
 * viewer dialog).
 *
 * Reads via ContentResolver (two passes: bounds, then the real decode --
 * an InputStream can't be rewound, so each pass opens its own stream)
 * rather than BitmapFactory.decodeFile, since a content:// Uri has no
 * directly usable filesystem path.
 */
@Composable
fun rememberLocalThumbnail(uriString: String, reqSizePx: Int = 200): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = uriString, key2 = reqSizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                decodeSampledBitmap(context, Uri.parse(uriString), reqSizePx, reqSizePx)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

/**
 * Same downsampled decode as rememberLocalThumbnail, but callable directly
 * from a coroutine rather than a @Composable, and sized for OCR legibility
 * (Sprint 4) rather than a small list thumbnail. 1600px keeps a typical
 * phone-camera photo (often 3000-4000px on its long edge) well within a
 * useful input size for ML Kit's text recognizer without decoding the
 * original at full resolution just for a one-shot recognition pass --
 * revisit this constant first if real-device testing shows small receipt
 * text isn't being read reliably.
 */
suspend fun decodeBitmapForOcr(context: Context, uriString: String, maxDim: Int = 1600): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching { decodeSampledBitmap(context, Uri.parse(uriString), maxDim, maxDim) }.getOrNull()
    }

private fun decodeSampledBitmap(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
    val resolver = context.contentResolver

    // Pass 1: bounds only. BitmapFactory.decodeStream() ALWAYS returns null
    // when inJustDecodeBounds = true -- by contract, no pixels are decoded,
    // only Options.outWidth/outHeight get populated as a side effect. The
    // earlier version of this function did
    //   resolver.openInputStream(uri)?.use { ... decodeStream(...) } ?: return null
    // which elvis'd off THAT always-null return value, not just a failed
    // stream open -- so every image, on every device, bailed out here
    // unconditionally and the thumbnail/viewer always fell back to the
    // placeholder icon. Keep "did the stream open" and "what did decode
    // return" as two separate checks so this can't happen again.
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val firstStream = resolver.openInputStream(uri) ?: return null
    firstStream.use { stream -> BitmapFactory.decodeStream(stream, null, boundsOptions) }
    if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

    var sampleSize = 1
    val halfWidth = boundsOptions.outWidth / 2
    val halfHeight = boundsOptions.outHeight / 2
    while (halfWidth / sampleSize >= reqWidth && halfHeight / sampleSize >= reqHeight) {
        sampleSize *= 2
    }

    // Pass 2: the real decode. A content:// InputStream can't be rewound,
    // so this opens a fresh stream rather than reusing the bounds-pass one.
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val secondStream = resolver.openInputStream(uri) ?: return null
    return secondStream.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
}
