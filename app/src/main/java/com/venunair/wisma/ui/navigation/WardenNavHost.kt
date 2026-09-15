package com.venunair.wisma.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
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
import com.venunair.wisma.capture.CameraCaptureScreen
import com.venunair.wisma.data.ItemRepository
import com.venunair.wisma.data.SettingsRepository
import com.venunair.wisma.data.UserPreferences
import com.venunair.wisma.ui.additem.AddEditItemScreen
import com.venunair.wisma.ui.archive.ArchivedItemsScreen
import com.venunair.wisma.ui.autodetect.AutoDetectSuggestionsScreen
import com.venunair.wisma.ui.home.HomeScreen
import com.venunair.wisma.ui.home.HomeViewModel
import com.venunair.wisma.ui.home.OverviewScreen
import com.venunair.wisma.ui.itemdetail.ItemDetailScreen
import com.venunair.wisma.ui.onboarding.OnboardingScreen
import com.venunair.wisma.ui.privacy.PrivacyScreen
import com.venunair.wisma.ui.settings.SettingsScreen
import com.venunair.wisma.ui.common.LocalRegion
import kotlinx.coroutines.launch

sealed class WardenDestination(val route: String) {
    data object Onboarding : WardenDestination("onboarding")
    // Overview screen pass, 2026-08-25: "home" now points at OverviewScreen
    // (the new landing screen) instead of the item list -- unchanged route
    // NAME so Splash/Onboarding's existing navigate-to-Home calls below
    // don't need to change, only what's mounted there. The item list
    // itself (formerly what "home" meant) is now Products, below.
    data object Home : WardenDestination("home")
    // Optional query args: filter names a HomeViewModel.QuickFilter (unset
    // = unfiltered), search opens the list with search already active. Both
    // default off so plain "products" (e.g. Overview's total-items tap)
    // still works. See [route] for the encoder side.
    data object Products : WardenDestination("products?filter={filter}&search={search}") {
        fun route(filter: HomeViewModel.QuickFilter? = null, startInSearch: Boolean = false): String {
            val params = mutableListOf<String>()
            filter?.let { params.add("filter=${it.name}") }
            if (startInSearch) params.add("search=true")
            return if (params.isEmpty()) "products" else "products?" + params.joinToString("&")
        }
    }
    data object AddItem : WardenDestination("item/new")
    data object EditItem : WardenDestination("item/{itemId}/edit")
    data object ItemDetail : WardenDestination("item/{itemId}")
    data object CameraCapture : WardenDestination("camera_capture")
    data object Settings : WardenDestination("settings")
    data object AutoDetectSuggestions : WardenDestination("auto_detect_suggestions")
    // Feedback, 2026-08-26: reachable from Settings -- see
    // ArchivedItemsScreen's own doc comment for why this exists.
    data object ArchivedItems : WardenDestination("archived_items")
    // Phase 2 (privacy): reachable from Settings -- see PrivacyScreen's own
    // doc comment.
    data object Privacy : WardenDestination("privacy")

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

    // 0.15.7: the separate Compose Splash screen (brand gradient + shield
    // glyph + wordmark + tagline, held ~600ms) was removed -- "why is there
    // two launch screens" (2026-09-13): the platform/backport SplashScreen
    // API (see MainActivity.installSplashScreen + themes.xml's
    // Theme.Warden.Splash) already shows a branded moment of its own
    // (the real app icon on the brand-blue background, held for a minimum
    // 200ms) before this NavHost's first composition ever runs, so the two
    // screens back-to-back read as one launch stuttering into another
    // rather than a single moment. One splash now, not two, and it's the
    // real app icon. realStartDestination is therefore always where a cold
    // start lands -- deep link/share hand-offs (see the LaunchedEffects
    // above) still work exactly as before, they just no longer needed a
    // *separate* skip case now that there's nothing to skip past.
    val realStartDestination = if (startAtOnboarding) WardenDestination.Onboarding.route else WardenDestination.Home.route

    // Sprint (international-formatting pass, 2026-09-01): provide the
    // active display Region once here, for every screen in the graph, from
    // the same `preferences` collection already read above -- see
    // ui/common/LocalRegion.kt's doc comment for why this lives at the
    // NavHost level rather than being threaded through each ViewModel.
    CompositionLocalProvider(LocalRegion provides preferences.region) {
    NavHost(
        navController = navController,
        startDestination = realStartDestination,
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
            OverviewScreen(
                repository = repository,
                onAddItem = { navController.navigate(WardenDestination.AddItem.route) },
                onOpenSettings = { navController.navigate(WardenDestination.Settings.route) },
                pendingSuggestionsCount = preferences.pendingAutoDetectSuggestions.size,
                onOpenAutoDetectSuggestions = { navController.navigate(WardenDestination.AutoDetectSuggestions.route) },
                onOpenProducts = { filter, startInSearch ->
                    navController.navigate(WardenDestination.Products.route(filter, startInSearch))
                }
            )
        }
        composable(
            route = WardenDestination.Products.route,
            arguments = listOf(
                navArgument("filter") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("search") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val filter = backStackEntry.arguments?.getString("filter")?.let { name ->
                runCatching { HomeViewModel.QuickFilter.valueOf(name) }.getOrNull()
            }
            val startInSearch = backStackEntry.arguments?.getBoolean("search") ?: false
            HomeScreen(
                repository = repository,
                onAddItem = { navController.navigate(WardenDestination.AddItem.route) },
                onOpenItem = { id -> navController.navigate(WardenDestination.ItemDetail.detailRoute(id)) },
                onEditItem = { id -> navController.navigate(WardenDestination.EditItem.editRoute(id)) },
                onBack = { navController.popBackStack() },
                initialFilter = filter,
                startInSearch = startInSearch
            )
        }
        composable(WardenDestination.AutoDetectSuggestions.route) {
            AutoDetectSuggestionsScreen(
                settingsRepository = settingsRepository,
                onBack = { navController.popBackStack() },
                onAddSuggestion = { suggestion ->
                    // Same SavedStateHandle hand-off the top-level
                    // LaunchedEffect(pendingShare) block above uses for a
                    // notification-tap PendingShare -- reused directly here
                    // (rather than round-tripping through MainActivity) since
                    // this action already runs inside the nav graph. source =
                    // "AUTO_DETECT" so AddEditItemScreen records the same
                    // provenance either entry point produces.
                    navController.navigate(WardenDestination.AddItem.route)
                    navController.currentBackStackEntry?.savedStateHandle?.apply {
                        set(PENDING_SHARE_URI_KEY, suggestion.imageUri)
                        set(PENDING_SHARE_MIME_KEY, "IMAGE")
                        set(PENDING_SHARE_NAME_KEY, "Auto-detected receipt")
                        set(PENDING_SHARE_SOURCE_KEY, "AUTO_DETECT")
                    }
                }
            )
        }
        composable(WardenDestination.Settings.route) {
            SettingsScreen(
                repository = settingsRepository,
                onBack = { navController.popBackStack() },
                onOpenArchivedItems = { navController.navigate(WardenDestination.ArchivedItems.route) },
                onOpenPrivacy = { navController.navigate(WardenDestination.Privacy.route) }
            )
        }
        composable(WardenDestination.ArchivedItems.route) {
            ArchivedItemsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(WardenDestination.Privacy.route) {
            PrivacyScreen(
                repository = repository,
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
                onDeleted = { navController.popBackStack() },
                onArchived = { navController.popBackStack() }
            )
        }
    }
    }
}
