package com.venunair.wisma.capture

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.venunair.wisma.MainActivity
import com.venunair.wisma.data.AttachmentMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sprint 3: the "I saw a receipt in WhatsApp, tapped Share, done" flow from
 * the spec. Registered in the manifest for ACTION_SEND with the image and
 * PDF MIME types, translucent/no-UI -- this Activity's only job is to grab
 * the shared content while it still has a valid read grant (a WhatsApp/Gmail
 * share Uri is not guaranteed to outlive this request -- see
 * AttachmentStorage's doc comment for why every capture source copies
 * immediately rather than holding onto an external Uri), copy it into our
 * own app-private storage via the exact same AttachmentStorage routine
 * every other capture source uses, then hand the resulting -- permission-
 * free, since it's now our own FileProvider Uri -- content:// Uri off to
 * MainActivity and finish.
 *
 * Deliberately does NO Room/repository work here. Whether a draft Item
 * needs creating, Name/Expiry validation, and the actual Attachment insert
 * all still happen in AddEditItemScreen.handleNewAttachment, exactly like
 * the camera/gallery/PDF-picker paths -- so there's exactly one place
 * attachment-saving logic lives, not a second copy of it duplicated here.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (sourceUri == null) {
            Toast.makeText(this, "Nothing to attach", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        // Our intent-filters only register for these two cases; anything
        // that isn't exactly the PDF mime type is therefore an image, no
        // need to pattern-match the image MIME wildcard back apart.
        val mimeType = if (intent.type == "application/pdf") AttachmentMimeType.PDF else AttachmentMimeType.IMAGE

        lifecycleScope.launch {
            val displayName = withContext(Dispatchers.IO) { queryDisplayName(sourceUri) }
            val copiedUri = withContext(Dispatchers.IO) {
                runCatching { AttachmentStorage.copyToAppStorage(applicationContext, sourceUri, mimeType) }.getOrNull()
            }
            if (copiedUri == null) {
                Toast.makeText(this@ShareReceiverActivity, "Couldn't read the shared file", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            startActivity(
                Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_SHARE_URI, copiedUri.toString())
                    putExtra(MainActivity.EXTRA_SHARE_MIME_TYPE, mimeType.name)
                    displayName?.let { putExtra(MainActivity.EXTRA_SHARE_DISPLAY_NAME, it) }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
            finish()
        }
    }

    /**
     * Best-effort original filename, so the Add screen can pre-fill Name
     * with something more useful than blank (e.g. "AMC_Contract.pdf" rather
     * than making the user type it from scratch while the file sits right
     * there attached). Not every content provider populates DISPLAY_NAME,
     * so this is allowed to come back null -- the Add screen falls back to
     * a plain placeholder in that case.
     */
    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }.getOrNull()
}
