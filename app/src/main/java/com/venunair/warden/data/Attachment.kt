package com.venunair.warden.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * PDF is a first-class citizen here, not bolted on — the whole capture and
 * OCR pipeline (Sprint 2-4) is designed to treat IMAGE and PDF attachments
 * identically wherever possible (see pdf/PdfPageRenderer.kt, which renders a
 * PDF page to the same Bitmap type an image already is before either hits
 * ML Kit).
 */
enum class AttachmentMimeType { IMAGE, PDF }
enum class AttachmentSource { CAMERA, GALLERY_PICKER, PDF_DOCUMENT_PICKER, SHARE, AUTO_DETECT }

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = Item::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** file:// or content:// URI into app-private storage — originals are
     *  copied in on capture so we're not dependent on the source app's URI
     *  staying valid (WhatsApp/Gmail URIs can be short-lived). */
    val localFileUri: String,
    val mimeType: AttachmentMimeType,
    /** Sprint 2: for a PDF attachment, a cached JPEG render of page 1 (see
     *  pdf.PdfPageRenderer.cacheThumbnail) -- generated once at capture time
     *  rather than re-rendering the PDF every time a list/detail screen
     *  needs a preview. Always null for an IMAGE attachment: the image
     *  itself (localFileUri) already serves as its own thumbnail. */
    val thumbnailUri: String? = null,
    /** Populated by Sprint 4's OCR pass; null until then. */
    val rawOcrText: String? = null,
    val source: AttachmentSource,
    val addedAt: Instant = Instant.now()
)
