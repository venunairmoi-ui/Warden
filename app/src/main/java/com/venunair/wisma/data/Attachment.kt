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
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId"), Index("serviceEventId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /**
     * AMC service-visit tracking follow-up, 2026-08-26: set when this
     * attachment is a receipt captured from the "Log a service" dialog,
     * so it can be shown against that specific visit in the Service
     * history timeline instead of only in the item's general "Invoice
     * available" section. itemId above stays set too (denormalized,
     * always the owning item) even when this is non-null -- deliberately,
     * so item-delete cleanup (which reads all attachments by itemId) and
     * item-wide OCR search keep working unchanged without needing to know
     * about this column at all. Null for every attachment captured the
     * ordinary way (Add/Edit, Share, auto-detect).
     *
     * Deliberately NOT a @ForeignKey to ServiceEvent, unlike itemId above
     * -- this crashed the app on first launch after 0.13.1, fixed here.
     * MIGRATION_9_10 adds this column with a plain ALTER TABLE ADD COLUMN,
     * and SQLite cannot add a new FOREIGN KEY constraint to an existing
     * table that way (foreign keys can only be declared in CREATE TABLE).
     * The entity briefly declared one anyway, so the live table's actual
     * foreign-key list (still just itemId -> items) no longer matched what
     * Room's schema validator expected from the entity (itemId -> items
     * AND serviceEventId -> service_events) -- Room throws on that
     * mismatch every time the database opens, which is every app launch.
     * An indexed-but-unenforced reference costs nothing in practice here:
     * there's no delete-service-event action anywhere in the app, so
     * cascade-on-delete for this column was never going to fire regardless.
     */
    val serviceEventId: Long? = null,
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
