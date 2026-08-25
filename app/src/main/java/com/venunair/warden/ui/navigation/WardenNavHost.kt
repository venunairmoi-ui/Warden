package com.venunair.warden.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.venunair.warden.capture.CameraCaptureScreen
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.data.SettingsRepository
import com.venunair.warden.data.UserPreferences
import com.venunair.warden.ui.additem.AddEditItemScreen
import com.venunair.warden.ui.home.HomeScreen
import com.venunair.warden.ui.itemdetail.ItemDetailScreen
import com.venunair.warden.ui.onboarding.OnboardingScreen
import com.venunair.warden.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

sealed class WardenDestination(val route: String) {
    data object Onboarding : WardenDestination("onboarding")
    data object Home : WardenDestination("home")
    data object AddItem : WardenDestination("item/new")
    data object EditItem : WardenDestination("item/{itemId}/edit")
    data object ItemDetail : WardenDestination("item/{itemId}")
    data object CameraCapture : WardenDestination("camera_capture")
    data object Settings : WardenDestination("settings")

    fun editRoute(itemId: Long) = "item/$itemId/edit"
    fun detailRoute(itemId: Long) = "item/$itemId"
}

/** Key CameraCaptureScreen writes its result Uri to, on the CALLER's
 *  (AddEditItemScreen's) own back stack entry's SavedStateHandle -- the
 *  standard Compose Navigation pattern for a screen returning a result,
 *  without threading a callback lambda through the NavHost graph. */
const val CAPTURED_URI_KEY = "captured_uri"

// Sprint 3: keys this NavHost writes onto a freshly-navigated-to AddItem
// entry's own SavedStateHandle, the mirror image of CAPTURED_URI_KEY above
// -- there it's the destination screen posting a result backward to its
// caller; here it's this NavHost posting data forward into a screen it just
// navigated to, which works the same way (currentBackStackEntry exists as
// soon as navigate() returns, before that screen's first composition runs).
const val PENDING_SHARE_URI_KEY = "pending_share_uri"
const val PENDING_SHARE_MIME_KEY = "pending_share_mime"
const val PENDING_SHARE_NAME_KEY = "pending_share_name"
const val PENDING_SHARE_SOURCE_KEY = "pending_share_source"

/** A share-sheet attachment ShareReceiverActivity has already copied into
 *  app storage, on its way to a fresh AddItem screen. mimeType is the raw
 *  AttachmentMimeType.name() string rather than the enum itself -- keeps
 *  this navigation-layer file from needing a data-layer import purely for
 *  a pass-through value; AddEditItemScreen parses it back with valueOf().
 *  Sprint 9: [source] is the same kind of raw pass-through string
 *  (AttachmentSource.name()) -- "SHARE" for a real Share-sheet hand-off,
 *  "AUTO_DETECT" when NotificationHelper.showAutoDetectSuggestion is what
 *  triggered this navigation. Defaults to "SHARE" so every pre-Sprint-9
 *  call site (there's exactly one, ShareReceiverActivity, which never sets
 *  MainActivity.EXTRA_SHARE_SOURCE) keeps behaving exactly as before. */
data class PendingShare(
    val uri: String,
    val mimeType: String,
    val displayName: String?,
    val source: String = "SHARE",
    val nonce: Int
)

/** Shared transition duration — 300ms is Material3's recommended medium
 *  emphasis duration, slow enough to read but fast enough to not feel
 *  sluggish on repeated back-and-forth navigation. */
private const val NAV_ANIM_DURATION = 300

@Composable
fun WardenNavHost(
    repository: ItemRepository,
    settingsRepository: SettingsRepository,
    // (itemId, nonce) from a notification tap — see MainActivity for why a
    // bare itemId alone can't reliably re-trigger navigation on a repeat tap.
    deepLinkTarget: Pair<Long, Int>? = null,
    // Sprint 3: a share-sheet hand-off from ShareReceiverActivity via
    // MainActivity — see PendingShare and MainActivity.updatePendingShare.
    // Sprint 9: also carries an auto-detect suggestion tap; see PendingShare.source.
    pendingShare: PendingShare? = null,
    // Sprint 9: true only for the one process-start frame where onboarding
    // hasn't been completed yet — see MainActivity.onboardingCompleted.
    startAtOnboarding: Boolean = false,
    navController: NavHostController = rememberNavController()
) {
    val scope = rememberCoroutineScope()
    // Sprint 9: read once here so both the AddItem route (new-item reminder
    // defaults) and, later, any other settings-driven UI in this graph
    // share one collector instead of each screen reaching into DataStore
    // itself.
    val preferences by settingsRepository.preferences.collectAsState(initial = UserPreferences())

    LaunchedEffect(deepLinkTarget) {
        deepLinkTarget?.let { (itemId, _) ->
            navController.navigate(WardenDestination.ItemDetail.detailRoute(itemId))
        }
    }

    LaunchedEffect(pendingShare) {
        pendingShare?.let { share ->
            navController.navigate(WardenDestination.AddItem.route)
            // Written onto the entry we just navigated to, not read back
            // here — AddItem's own composable block below picks these up
            // via getStateFlow, same mechanism CAPTURED_URI_KEY uses in the
            // other direction.
            navController.currentBackStackEntry?.savedStateHandle?.apply {
                set(PENDING_SHARE_URI_KEY, share.uri)
                set(PENDING_SHARE_MIME_KEY, share.mimeType)
                set(PENDING_SHARE_NAME_KEY, share.displayName)
                set(PENDING_SHARE_SOURCE_KEY, share.source)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (startAtOnboarding) WardenDestination.Onboarding.route else WardenDestination.Home.route,
        // Default transitions for all destinations: horizontal slide
        // (forward = right-to-left, back = left-to-right) with a subtle
        // fade so the transition doesn't jump harshly at the edges.
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIM_DURATION)
            ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(NAV_ANIM_DURATION)
            ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIM_DURATION)
            ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(NAV_ANIM_DURATION)
            ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
        }
    ) {
        composable(WardenDestination.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    scope.launch { settingsRepository.setOnboardingCompleted(true) }
                    navController.navigate(WardenDestination.Home.route) {
                        popUpTo(WardenDestination.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(WardenDestination.Home.route) {
            HomeScreen(
                repository = repository,
                onAddItem = { navController.navigate(WardenDestination.AddItem.route) },
                onOpenItem = { id -> navController.navigate(WardenDestination.ItemDetail.detailRoute(id)) },
                onOpenSettings = { navController.navigate(WardenDestination.Settings.route) }
            )
        }
        composable(WardenDestination.Settings.route) {
            SettingsScreen(
                repository = settingsRepository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(WardenDestination.AddItem.route) { backStackEntry ->
            val capturedUri by backStackEntry.savedStateHandle
                .getStateFlow<String?>(CAPTURED_URI_KEY, null)
                .collectAsState()
            val pendingShareUri by backStackEntry.savedStateHandle
                .getStateFlow<String?>(PENDING_SHARE_URI_KEY, null)
                .collectAsState()
            val pendingShareMime by backStackEntry.savedStateHandle
                .getStateFlow<String?>(PENDING_SHARE_MIME_KEY, null)
                .collectAsState()
            val pendingShareName by backStackEntry.savedStateHandle
                .getStateFlow<String?>(PENDING_SHARE_NAME_KEY, null)
                .collectAsState()
            val pendingShareSource by backStackEntry.savedStateHandle
                .getStateFlow<String?>(PENDING_SHARE_SOURCE_KEY, null)
                .collectAsState()
            AddEditItemScreen(
                repository = repository,
                itemId = null,
                onDone = { navController.popBackStack() },
                onLaunchCamera = { navController.navigate(WardenDestination.CameraCapture.route) },
                capturedUri = capturedUri,
                onCapturedUriConsumed = { backStackEntry.savedStateHandle[CAPTURED_URI_KEY] = null },
                pendingShareUri = pendingShareUri,
                pendingShareMimeType = pendingShareMime,
                pendingShareDisplayName = pendingShareName,
                pendingShareSource = pendingShareSource,
                onPendingShareConsumed = {
                    backStackEntry.savedStateHandle[PENDING_SHARE_URI_KEY] = null
                    backStackEntry.savedStateHandle[PENDING_SHARE_MIME_KEY] = null
                    backStackEntry.savedStateHandle[PENDING_SHARE_NAME_KEY] = null
                    backStackEntry.savedStateHandle[PENDING_SHARE_SOURCE_KEY] = null
                },
                defaultReminderOffsets = preferences.defaultReminderOffsets
            )
        }
        composable(
            route = WardenDestination.EditItem.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId")
            val capturedUri by backStackEntry.savedStateHandle
                .getStateFlow<String?>(CAPTURED_URI_KEY, null)
                .collectAsState()
            AddEditItemScreen(
                repository = repository,
                itemId = itemId,
                onDone = { navController.popBackStack() },
                onLaunchCamera = { navController.navigate(WardenDestination.CameraCapture.route) },
                capturedUri = capturedUri,
                onCapturedUriConsumed = { backStackEntry.savedStateHandle[CAPTURED_URI_KEY] = null }
            )
        }
        // Camera uses a vertical slide — it's a modal overlay, not a lateral
        // navigation step, so sliding up/down reads as "this is a tool, not
        // a new page". Overrides the NavHost-level horizontal defaults.
        composable(
            route = WardenDestination.CameraCapture.route,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(NAV_ANIM_DURATION)
                ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(NAV_ANIM_DURATION)
                ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(NAV_ANIM_DURATION)
                ) + fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(NAV_ANIM_DURATION)
                ) + fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
            }
        ) {
            CameraCaptureScreen(
                onCaptured = { uri ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(CAPTURED_URI_KEY, uri.toString())
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = WardenDestination.ItemDetail.route,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
            ItemDetailScreen(
                repository = repository,
                itemId = itemId,
                onEdit = { navController.navigate(WardenDestination.EditItem.editRoute(itemId)) },
                onBack = { navController.popBackStack() },
                // Same effect as onBack (this screen's only ever pushed on
                // top of Home), but kept as its own callback so this
                // screen's API stays self-documenting about which action
                // triggered the pop.
                onDeleted = { navController.popBackStack() }
            )
        }
    }
}
