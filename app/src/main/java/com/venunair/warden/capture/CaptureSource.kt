package com.venunair.warden.capture

/**
 * Shared vocabulary for how an attachment entered the app — the concrete
 * capture flows behind each case are implemented in later sprints:
 *   Camera, GalleryPicker, PdfDocumentPicker -> Sprint 2
 *   Share (Android Share sheet, image MIME types AND application/pdf) -> Sprint 3
 *   AutoDetect (opt-in MediaStore watch)                        -> Sprint 6
 *
 * Defined now so AddEditItemScreen's Sprint-2 capture buttons and
 * data.Attachment's `source` field share one vocabulary from the start,
 * rather than each sprint inventing its own.
 */
sealed interface CaptureSource {
    data object Camera : CaptureSource
    data object GalleryPicker : CaptureSource
    data object PdfDocumentPicker : CaptureSource
    data object Share : CaptureSource
    data object AutoDetect : CaptureSource
}
