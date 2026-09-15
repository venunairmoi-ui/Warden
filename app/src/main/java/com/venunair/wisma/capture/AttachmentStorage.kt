package com.venunair.wisma.capture

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.venunair.wisma.data.AttachmentMimeType
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * One storage location and one copy routine for every capture path (camera,
 * gallery picker, PDF picker -- and Sprint 3's Share intent, when it lands).
 * Source URIs from a picker or another app's Share can be short-lived
 * (WhatsApp/Gmail content:// URIs in particular aren't guaranteed to
 * outlive the originating request), so everything gets copied into
 * app-private storage immediately on capture rather than an Attachment row
 * ever referencing an original external URI.
 *
 * Every Uri this object hands out (from copyToAppStorage and uriForFile) is
 * a FileProvider content:// Uri, deliberately -- never a raw file:// Uri.
 * content:// through our own FileProvider is unambiguously, documented-ly
 * readable via ContentResolver (openInputStream / openFileDescriptor),
 * which is what both PdfPageRenderer and the thumbnail decoder in
 * ui/common/LocalBitmap.kt use. Standardizing on one scheme everywhere
 * removes any doubt about whether a given read path supports file://.
 */
object AttachmentStorage {

    private const val DIR_NAME = "attachments"

    fun attachmentsDir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** A fresh, empty destination file for CameraX to capture directly into. */
    fun newCaptureFile(context: Context): File =
        File(attachmentsDir(context), "${UUID.randomUUID()}.jpg")

    /** FileProvider content:// URI for a file this app owns. */
    fun uriForFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Copies any readable source Uri into app-private storage and returns
     * a content:// Uri (via FileProvider) for the copy, ready to store as
     * Attachment.localFileUri / thumbnailUri. Always makes a fresh copy,
     * even if sourceUri already points inside our own attachments dir
     * (e.g. handed back from the camera screen) -- simpler than
     * special-casing "is this already ours," and one extra copy of a
     * photo/PDF is cheap.
     */
    fun copyToAppStorage(context: Context, sourceUri: Uri, mimeType: AttachmentMimeType): Uri {
        val ext = if (mimeType == AttachmentMimeType.PDF) "pdf" else "jpg"
        val destFile = File(attachmentsDir(context), "${UUID.randomUUID()}.$ext")
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: error("Could not open source URI for copying: $sourceUri")
        input.use { source ->
            FileOutputStream(destFile).use { output -> source.copyTo(output) }
        }
        return uriForFile(context, destFile)
    }

    /**
     * Deletes the backing file for a stored attachment/thumbnail Uri.
     * androidx FileProvider implements ContentProvider.delete() by
     * deleting the underlying file it was vended for, so routing through
     * ContentResolver.delete() here (rather than trying to reconstruct a
     * java.io.File from an opaque FileProvider Uri) is both correct and
     * the only reliable option. Failure isn't fatal -- an orphaned file
     * just wastes a little storage, it can't cause a crash or wrong data
     * downstream -- so this is deliberately silent on error.
     */
    fun deleteBackingFile(context: Context, uriString: String) {
        runCatching { context.contentResolver.delete(Uri.parse(uriString), null, null) }
    }
}
