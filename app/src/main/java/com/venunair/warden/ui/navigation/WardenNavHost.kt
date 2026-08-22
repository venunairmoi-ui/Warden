package com.venunair.warden.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.venunair.warden.capture.CameraCaptureScreen
import com.venunair.warden.data.ItemRepository
import com.venunair.warden.ui.additem.AddEditItemScreen
import com.venunair.warden.ui.home.HomeScreen
import com.venunair.warden.ui.itemdetail.ItemDetailScreen

sealed class WardenDestination(val route: String) {
    data object Home : WardenDestination("home")
    data object AddItem : WardenDestination("item/new")
    data object EditItem : WardenDestination("item/{itemId}/edit")
    data object ItemDetail : WardenDestination("item/{itemId}")
    data object CameraCapture : WardenDestination("camera_capture")

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

/** A share-sheet attachment ShareReceiverActivity has already copied into
 *  app storage, on its way to a fresh AddItem screen. mimeType is the raw
 *  AttachmentMimeType.name() string rather than the enum itself -- keeps
 *  this navigation-layer file from needing a data-layer import purely for
 *  a pass-through value; AddEditItemScreen parses it back with valueOf(). */
data class PendingShare(
    val uri: String,
    val mimeType: String,
    val displayName: String?,
    val nonce: Int
)

@Composable
fun WardenNavHost(
    repository: ItemRepository,
    // (itemId, nonce) from a notification tap — see MainActivity for why a
    // bare itemId alone can't reliably re-trigger navigation on a repeat tap.
    deepLinkTarget: Pair<Long, Int>? = null,
    // Sprint 3: a share-sheet hand-off from ShareReceiverActivity via
    // MainActivity — see PendingShare and MainActivity.updatePendingShare.
    pendingShare: PendingShare? = null,
    navController: NavHostController = rememberNavController()
) {
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
            }
        }
    }

    NavHost(navController = navController, startDestination = WardenDestination.Home.route) {
        composable(WardenDestination.Home.route) {
            HomeScreen(
                repository = repository,
                onAddItem = { navController.navigate(WardenDestination.AddItem.route) },
                onOpenItem = { id -> navController.navigate(WardenDestination.ItemDetail.detailRoute(id)) }
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
                onPendingShareConsumed = {
                    backStackEntry.savedStateHandle[PENDING_SHARE_URI_KEY] = null
                    backStackEntry.savedStateHandle[PENDING_SHARE_MIME_KEY] = null
                    backStackEntry.savedStateHandle[PENDING_SHARE_NAME_KEY] = null
                }
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
        composable(WardenDestination.CameraCapture.route) {
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
