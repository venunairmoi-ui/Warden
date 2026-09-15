package com.venunair.warden.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/**
 * Native PDF handling — built on android.graphics.pdf.PdfRenderer, part of
 * the Android platform SDK since API 21. Deliberately no third-party PDF
 * library: that keeps this inside the spec's $0-new-dependency-cost
 * constraint and avoids taking on a library's maintenance/licensing risk for
 * something the platform already does.
 *
 * Design choice: a PDF page renders to a Bitmap and flows through the SAME
 * ML Kit OCR call an image attachment uses (Sprint 4), rather than a separate
 * PDF-text-extraction path. One capture pipeline for both MIME types, and it
 * works for scanned PDFs (which have no embedded text layer) as well as
 * text-based ones — a native "extract embedded text" API alone would only
 * cover the latter.
 */
object PdfPageRenderer {

    // PdfRenderer's native page size is often too low-resolution for clean
    // OCR (it's tuned for on-screen preview, not text recognition), so pages
    // are upscaled before being handed to ML Kit.
    private const val RENDER_SCALE = 2

    /**
     * Renders a single page of a local PDF to a Bitmap.
     * @param pageIndex 0-based; defaults to the first page, which covers the
     *   large majority of warranty cards / AMC confirmations (single-page docs).
     * @return null if the URI can't be opened or the page index is out of range.
     */
    fun renderPage(context: Context, pdfUri: Uri, pageIndex: Int = 0): Bitmap? {
        val descriptor: ParcelFileDescriptor =
            context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return null

        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = Bitmap.createBitmap(
                        page.width * RENDER_SCALE,
                        page.height * RENDER_SCALE,
                        Bitmap.Config.ARGB_8888
                    )
                    // Root cause of the AMC receipt's empty thumbnail AND
                    // "no text found" -- confirmed via PdfRenderer's own
                    // javadoc ("it is your responsibility to initialize the
                    // bitmap outside the clip") plus Google's own
                    // PrintSpooler source doing exactly this before calling
                    // page.render(). A freshly created Bitmap is NOT
                    // guaranteed to start white -- in practice it's
                    // transparent black (0x00000000) -- and PdfRenderer only
                    // paints what the PDF's own content stream explicitly
                    // draws. A PDF that (like this AMC receipt, unlike the
                    // Chrome-generated invoice that happened to mask this)
                    // doesn't paint its own opaque page-background rectangle
                    // leaves everything else exactly as transparent as it
                    // started. Compose's Image composable alpha-composites
                    // that transparency onto the dialog's own white
                    // background, so the PDF PREVIEW still looked fine --
                    // but Bitmap.compress(JPEG, ...) has no alpha channel to
                    // composite with (Android bitmaps store premultiplied
                    // alpha, so transparent pixels' stored RGB bytes are
                    // literally 0,0,0) and flattens those regions to solid
                    // BLACK, exactly matching the empty/black thumbnail --
                    // and ML Kit's recognizer reads raw RGB the same way,
                    // seeing dark text on a black (not white) background,
                    // exactly matching "no text found" on an otherwise
                    // perfectly legible page.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    fun pageCount(context: Context, pdfUri: Uri): Int {
        val descriptor = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return 0
        descriptor.use { fd ->
            PdfRenderer(fd).use { renderer -> return renderer.pageCount }
        }
    }

    /**
     * Renders and JPEG-encodes a page thumbnail to app-private storage, for
     * fast display in the home list / item detail without re-rendering the
     * PDF every time (Sprint 2 UI hook).
     */
    fun cacheThumbnail(context: Context, pdfUri: Uri, targetFile: File, pageIndex: Int = 0): Boolean {
        val bitmap = renderPage(context, pdfUri, pageIndex) ?: return false
        val saved = saveAsJpeg(bitmap, targetFile)
        bitmap.recycle()
        return saved
    }

    /**
     * JPEG-encode an already-rendered page Bitmap to [targetFile]. Split out
     * of cacheThumbnail (Sprint 4) so a caller that needs the raw Bitmap for
     * something else too -- ML Kit OCR, in AddEditItemScreen.handleNewAttachment
     * -- can render a PDF page ONCE and feed that same Bitmap to both this
     * and OCR, rather than rendering the same page twice to get two
     * different outputs from it. Bitmap lifecycle stays the caller's
     * responsibility either way -- this never recycles it, exactly as
     * cacheThumbnail's inlined version never did before this was extracted.
     */
    fun saveAsJpeg(bitmap: Bitmap, targetFile: File, quality: Int = 85): Boolean {
        // Bitmap.compress() returns whether the encode+write actually
        // succeeded -- this used to discard that and unconditionally
        // return true, which meant a failed compress() still left the
        // caller believing it had a valid thumbnail file to point
        // Attachment.thumbnailUri at (silently producing an empty/corrupt
        // JPEG on disk instead of correctly falling back to no thumbnail).
        // Same class of bug ui/common/LocalBitmap.kt's decodeSampledBitmap
        // doc comment already warns about elsewhere in this codebase:
        // dropping a success/failure signal on the floor.
        return FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }
}
