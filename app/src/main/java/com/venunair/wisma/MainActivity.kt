package com.venunair.wisma

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.venunair.wisma.data.ThemeMode
import com.venunair.wisma.data.UserPreferences
import com.venunair.wisma.ui.navigation.PendingShare
import com.venunair.wisma.ui.navigation.WardenNavHost
import com.venunair.wisma.ui.theme.WardenTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    // coincidence the Uri string were to repeat. Sprint 9: this same
    // hand-off now also carries auto-detect suggestions (source =
    // "AUTO_DETECT") through the identical path a Share-sheet hand-off
    // uses -- see PendingShare.source and NotificationHelper.showAutoDetectSuggestion.
    private var shareNonce = 0
    private val pendingShare = mutableStateOf<PendingShare?>(null)

    // Sprint 9: null = "haven't read DataStore yet" (splash stays up),
    // true/false = onboarding's completed state, resolved once per process
    // start. Deliberately NOT re-collected after the first read -- once
    // onboarding is shown/skipped for this session there's no scenario
    // where it should retroactively change the nav graph's start
    // destination out from under the user.
    private val onboardingCompleted = mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() -- the platform/backport
        // SplashScreen API installs itself by intercepting the window
        // before any content is drawn.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WardenApplication
        val repository = app.repository

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

        // Sprint 9: hold the splash screen up until onboarding's completed
        // flag has been read AND at least SPLASH_MIN_HOLD_MS has elapsed --
        // the second half so a fast DataStore read on a warm process
        // doesn't make the brand moment flash by unreadably.
        val splashStartElapsedMs = SystemClock.elapsedRealtime()
        splashScreen.setKeepOnScreenCondition {
            onboardingCompleted.value == null ||
                SystemClock.elapsedRealtime() - splashStartElapsedMs < SPLASH_MIN_HOLD_MS
        }
        lifecycleScope.launch {
            onboardingCompleted.value = app.settingsRepository.preferences.first().onboardingCompleted
        }

        setContent {
            // Sprint 9: Settings' Theme radio (system/light/dark) —
            // collectAsState's `initial` matters here specifically because
            // WardenTheme must render something on the very first frame,
            // before DataStore's first real emission arrives; SYSTEM is
            // UserPreferences' own default, so this never has to guess.
            val preferences by app.settingsRepository.preferences.collectAsState(initial = UserPreferences())
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WardenTheme(darkTheme = darkTheme) {
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
                val currentOnboardingCompleted by onboardingCompleted

                // Splash covers this composition until currentOnboardingCompleted
                // resolves, so a null value never actually paints -- but
                // NavHost still needs a concrete start destination to
                // render at all, hence the `?: true` (defaults to "skip
                // onboarding" for that one still-invisible frame, never
                // user-visible).
                WardenNavHost(
                    repository = repository,
                    settingsRepository = app.settingsRepository,
                    deepLinkTarget = currentDeepLink,
                    pendingShare = currentPendingShare,
                    startAtOnboarding = currentOnboardingCompleted == false
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
            // Sprint 9: "SHARE" default keeps every pre-Sprint-9 caller
            // (ShareReceiverActivity never sets this extra) behaving
            // exactly as before.
            source = intent.getStringExtra(EXTRA_SHARE_SOURCE) ?: "SHARE",
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

        // Sprint 9: distinguishes an auto-detect suggestion tap from a real
        // Share-sheet hand-off on the same pending-attachment path -- see
        // NotificationHelper.showAutoDetectSuggestion and
        // AddEditItemScreen's AttachmentSource.valueOf(pendingShareSource).
        const val EXTRA_SHARE_SOURCE = "extra_share_source"

        // Not user-perceptible as "waiting" (200ms), but long enough that
        // the brand moment isn't a single-frame flash on a fast device --
        // matches the spec's stated 200ms minimum hold.
        private const val SPLASH_MIN_HOLD_MS = 200L
    }
}
