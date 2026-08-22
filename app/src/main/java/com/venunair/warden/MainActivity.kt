package com.venunair.warden

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.venunair.warden.ui.navigation.PendingShare
import com.venunair.warden.ui.navigation.WardenNavHost
import com.venunair.warden.ui.theme.WardenTheme

class MainActivity : ComponentActivity() {

    // Pair(itemId, nonce) rather than a bare Long: tapping a second
    // notification for the SAME item while the app is already warm would
    // set the exact same Long value again, which Compose's mutableStateOf
    // treats as no change at all (structural equality) and so wouldn't
    // re-trigger navigation. The nonce makes every tap distinct.
    private var deepLinkNonce = 0
    private val deepLink = mutableStateOf<Pair<Long, Int>?>(null)

    // Sprint 3: same nonce trick, separate state -- a second share while
    // the app is already warm (unlikely, but free to handle correctly)
    // must re-trigger navigation to a NEW Add screen even if by some
    // coincidence the Uri string were to repeat.
    private var shareNonce = 0
    private val pendingShare = mutableStateOf<PendingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as WardenApplication).repository
        // Only process the launching Intent's deep-link/share extras on a
        // genuinely fresh start. onCreate also re-runs on a plain
        // recreation (e.g. rotation) with the SAME Intent still attached --
        // savedInstanceState is non-null exactly in that case -- and
        // reprocessing it there would re-navigate and, worse for the share
        // path, re-run handleNewAttachment and insert a duplicate
        // Item/Attachment. onNewIntent (below) is unaffected: it only ever
        // fires for a genuinely new incoming Intent, never a recreation.
        if (savedInstanceState == null) {
            updateDeepLink(intent)
            updatePendingShare(intent)
        }

        setContent {
            WardenTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* no-op either way — without it, reminders just can't show; nothing else breaks */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val currentDeepLink by deepLink
                val currentPendingShare by pendingShare
                WardenNavHost(
                    repository = repository,
                    deepLinkTarget = currentDeepLink,
                    pendingShare = currentPendingShare
                )
            }
        }
    }

    // MainActivity is launchMode="singleTop" (see AndroidManifest.xml), so
    // tapping a notification -- or ShareReceiverActivity handing off a
    // share -- while the app is already on top delivers here instead of
    // creating a second Activity instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateDeepLink(intent)
        updatePendingShare(intent)
    }

    private fun updateDeepLink(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_DEEP_LINK_ITEM_ID, -1L) ?: -1L
        if (id != -1L) {
            deepLinkNonce++
            deepLink.value = id to deepLinkNonce
        }
    }

    private fun updatePendingShare(intent: Intent?) {
        val uri = intent?.getStringExtra(EXTRA_SHARE_URI) ?: return
        val mimeType = intent.getStringExtra(EXTRA_SHARE_MIME_TYPE) ?: return
        shareNonce++
        pendingShare.value = PendingShare(
            uri = uri,
            mimeType = mimeType,
            displayName = intent.getStringExtra(EXTRA_SHARE_DISPLAY_NAME),
            nonce = shareNonce
        )
    }

    companion object {
        const val EXTRA_DEEP_LINK_ITEM_ID = "extra_deep_link_item_id"

        // Sprint 3: set by ShareReceiverActivity once it's copied a shared
        // image/PDF into app storage -- see that class's doc comment for
        // why the copy happens there rather than here.
        const val EXTRA_SHARE_URI = "extra_share_uri"
        const val EXTRA_SHARE_MIME_TYPE = "extra_share_mime_type"
        const val EXTRA_SHARE_DISPLAY_NAME = "extra_share_display_name"
    }
}
