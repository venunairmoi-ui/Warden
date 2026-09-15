package com.venunair.warden.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin suspend wrapper around ML Kit's Task-based TextRecognizer, so a
 * caller already inside a coroutine (every capture path in
 * AddEditItemScreen.handleNewAttachment) can just
 * `runCatching { recognizeText(bitmap) }` instead of juggling
 * addOnSuccessListener/addOnFailureListener by hand.
 *
 * One recognizer client is created lazily and reused for the process
 * lifetime -- ML Kit's own docs describe TextRecognizer as expensive to
 * construct but safe to call concurrently (it queues internally), so a
 * fresh TextRecognition.getClient() per attachment would be pure waste.
 *
 * This project's text-recognition artifact (see libs.versions.toml,
 * "mlkitTextRecognition" = 16.0.1, group com.google.mlkit) is the BUNDLED
 * variant: the recognition model ships inside the APK at build time rather
 * than downloading on first use via Google Play services. That's a fact
 * worth having verified against ML Kit's own docs rather than assumed --
 * the unbundled sibling artifact (same class names, different Gradle
 * coordinate) behaves very differently on first run (a background download
 * that can silently stall with no network), and guessing wrong here would
 * only surface as "OCR just... doesn't work" on a real device with no
 * compiler error to catch it. Bundled means every OCR call below works
 * immediately, fully offline, from the very first attach.
 *
 * All class/package names here (TextRecognition.getClient,
 * com.google.mlkit.vision.text.latin.TextRecognizerOptions,
 * com.google.mlkit.vision.common.InputImage, TextRecognizer.process(
 * InputImage): Task<Text>) were confirmed against Google's own ML Kit API
 * reference before writing this, the same discipline established after the
 * Icons.Filled.Science near-miss earlier in this project -- an unverified
 * guess at a class or package name here fails the same way that one nearly
 * did: a build that doesn't compile, for a change nobody asked to review
 * that hard.
 */
private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
}

/**
 * Runs on-device OCR on [bitmap] and returns ML Kit's hierarchical result
 * (blocks -> lines -> elements -> symbols; callers here only use the flat
 * [Text.text] property). Never called with a UI-critical expectation of
 * success -- see handleNewAttachment's runCatching wrapper -- a failure
 * here (no text found, a corrupt bitmap, whatever) just means no fields get
 * pre-filled, not that the attachment itself failed.
 */
suspend fun recognizeText(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
    val image = InputImage.fromBitmap(bitmap, 0)
    recognizer.process(image)
        .addOnSuccessListener { text -> continuation.resume(text) }
        .addOnFailureListener { exception -> continuation.resumeWithException(exception) }
}
