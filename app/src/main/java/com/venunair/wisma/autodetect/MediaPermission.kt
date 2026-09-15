package com.venunair.warden.autodetect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * The one runtime permission Auto-detect needs to query MediaStore images
 * — which string that is depends on API level (see AndroidManifest.xml's
 * comment on the two declared permissions). Both the Settings screen's
 * permission request and AutoDetectWorker's defensive re-check must use
 * this same value, or the two can disagree about which permission was
 * actually granted.
 */
fun mediaImagesPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * Whether Auto-detect can query MediaStore right now — full access OR the
 * Android 14+ (API 34) "select photos" partial grant.
 *
 * Requesting [mediaImagesPermission] on API 34+ can surface a three-way
 * system dialog: Allow all / Select photos / Don't allow. If the user
 * picks "Select photos", READ_MEDIA_IMAGES itself comes back DENIED — the
 * system instead silently grants READ_MEDIA_VISUAL_USER_SELECTED for just
 * the chosen photos. A caller that only checks [mediaImagesPermission]
 * treats that as a flat denial and no-ops, even though MediaStore queries
 * still work (scoped to the photos the user selected). This is the
 * permission check every access point — the worker's defensive re-check,
 * the Settings toggle, the permission-launcher callback — must use.
 */
fun hasMediaImageAccess(context: Context): Boolean {
    val fullAccess = ContextCompat.checkSelfPermission(
        context, mediaImagesPermission()
    ) == PackageManager.PERMISSION_GRANTED
    if (fullAccess) return true
    if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        ) == PackageManager.PERMISSION_GRANTED
    }
    return false
}
