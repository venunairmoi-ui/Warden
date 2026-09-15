package com.venunair.wisma.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.venunair.wisma.capture.AttachmentStorage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Real-user report, 2026-09-15: an end user's second insurance PDF (shared
 * off WhatsApp/mail, same path every attachment takes) required a password
 * to open. android.graphics.pdf.PdfRenderer -- what PdfPageRenderer.kt uses
 * for every other PDF in this app -- cannot open an encrypted PDF at all;
 * there's no password parameter until API 35 (Android 15), which is nearly
 * this app's whole real userbase given minSdk 26. Opening it in another
 * viewer first doesn't help either: a Share intent hands this app the same
 * still-encrypted file bytes regardless of what unlocked it for viewing
 * elsewhere, so there's no "already-unlocked" copy to receive.
 *
 * This object exists for exactly one job: given a password, open the
 * encrypted PDF with PdfBox-Android, strip its security, and save a plain
 * decrypted copy into this app's own attachment storage. That's the ONLY
 * thing PdfBox-Android is used for anywhere in this app -- every other PDF
 * operation (page count, page rendering, thumbnails, the in-app viewer)
 * still goes through the native PdfPageRenderer/PdfRenderer pipeline,
 * unchanged, now pointed at the decrypted copy instead of the original.
 * The password itself is used only transiently to open the document here;
 * it is never written to disk, the database, or logs.
 */
object PdfDecryptor {

    /**
     * True if [pdfUri] is specifically a PASSWORD-protected PDF, not just
     * any PDF the native renderer happens to fail on. Android's PdfRenderer
     * throws SecurityException for this exact case and only this case (a
     * corrupt file, a zero-page PDF, or an I/O error surfaces as a
     * different exception type) -- so narrowing to SecurityException here
     * keeps this from misfiring on an unrelated bad-PDF failure and routing
     * it into a password prompt that could never help.
     */
    fun isPasswordProtected(context: Context, pdfUri: Uri): Boolean {
        val descriptor = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return false
        return descriptor.use { fd ->
            try {
                PdfRenderer(fd).close()
                false
            } catch (e: SecurityException) {
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    sealed class UnlockResult {
        /** [decryptedUri] is a NEW, plain (unencrypted) local PDF file's content:// Uri -- the original encrypted source is left untouched. */
        data class Success(val decryptedUri: Uri) : UnlockResult()
        data object WrongPassword : UnlockResult()
        data object Failure : UnlockResult()
    }

    /**
     * Attempts to open [pdfUri] with [password] and, on success, writes a
     * decrypted copy to this app's attachments directory (same directory
     * [com.venunair.wisma.capture.AttachmentStorage] already uses for every
     * other attachment, so it's cleaned up the same way on item delete).
     * Distinguishes a wrong password (InvalidPasswordException -- worth
     * showing the user an inline "incorrect password, try again") from any
     * other failure (corrupt data, I/O error -- not something retrying the
     * same password again would ever fix).
     */
    fun removePasswordProtection(context: Context, pdfUri: Uri, password: String): UnlockResult {
        return try {
            val input = context.contentResolver.openInputStream(pdfUri) ?: return UnlockResult.Failure
            input.use { stream ->
                PDDocument.load(stream, password).use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    val outFile = File(AttachmentStorage.attachmentsDir(context), "${UUID.randomUUID()}_unlocked.pdf")
                    FileOutputStream(outFile).use { out -> document.save(out) }
                    UnlockResult.Success(AttachmentStorage.uriForFile(context, outFile))
                }
            }
        } catch (e: InvalidPasswordException) {
            UnlockResult.WrongPassword
        } catch (e: Exception) {
            UnlockResult.Failure
        }
    }
}
