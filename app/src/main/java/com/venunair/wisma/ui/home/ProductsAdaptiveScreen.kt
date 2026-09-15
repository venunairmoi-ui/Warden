package com.venunair.wisma.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.venunair.wisma.data.ItemRepository
import com.venunair.wisma.ui.itemdetail.ItemDetailScreen
import kotlinx.coroutines.launch

/**
 * Phase 3 (2026-09-15): two-pane list-detail layout for tablets/foldables
 * wide enough to cross the EXPANDED breakpoint (840dp), reusing Google's
 * Material3 Adaptive library rather than hand-rolling pane arrangement.
 *
 * Built against the real, decompiled 1.2.0 API (no tablet/foldable
 * device or emulator available to verify against visually in this
 * environment -- see WardenNavHost's own doc comment on the
 * WindowSizeClass gate that routes here). Notably: despite some docs
 * describing a "NavigableListDetailPaneScaffold" convenience composable,
 * no such function exists in this stable release -- confirmed by
 * extracting the actual AAR/sources jars from Google's Maven repo and
 * reading them directly, not by trusting a docs summary a second time
 * this session (the first time cost a wasted GoogleSignInClient
 * implementation earlier in this same session). [ListDetailPaneScaffold]
 * + [rememberListDetailPaneScaffoldNavigator] are composed by hand here
 * instead, including manual back-handling via [BackHandler] +
 * `navigator.navigateBack()`.
 *
 * Scope: only wired for the Products list -> item detail relationship.
 * Editing (onEditItem) still goes through the OUTER NavHost as a full
 * screen even on wide layouts -- matches how a list-detail pattern
 * conventionally treats a full edit form, and keeps this addition from
 * needing to touch AddEditItemScreen/WardenNavHost's edit flow at all.
 * The compact/phone path (WardenNavHost's `else` branch, plain
 * [HomeScreen] + a separate ItemDetail destination) is completely
 * unchanged code -- this file's entire contents are unreached on a
 * phone-width window, zero risk to the already device-verified compact
 * experience.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProductsAdaptiveScreen(
    repository: ItemRepository,
    onAddItem: () -> Unit,
    onEditItem: (Long) -> Unit,
    onBack: () -> Unit,
    initialFilter: HomeViewModel.QuickFilter?,
    startInSearch: Boolean
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = navigator.canNavigateBack()) {
        scope.launch { navigator.navigateBack() }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                HomeScreen(
                    repository = repository,
                    onAddItem = onAddItem,
                    onOpenItem = { id ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }
                    },
                    onEditItem = onEditItem,
                    onBack = onBack,
                    initialFilter = initialFilter,
                    startInSearch = startInSearch
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val itemId = navigator.currentDestination?.contentKey
                if (itemId != null) {
                    ItemDetailScreen(
                        repository = repository,
                        itemId = itemId,
                        onEdit = { onEditItem(itemId) },
                        onBack = { scope.launch { navigator.navigateBack() } },
                        onDeleted = { scope.launch { navigator.navigateBack() } },
                        onArchived = { scope.launch { navigator.navigateBack() } }
                    )
                } else {
                    EmptyDetailPanePlaceholder()
                }
            }
        }
    )
}

/** Shown in the detail pane before anything's been selected from the
 *  list pane -- a two-pane-only state; the compact/phone path never
 *  renders this, since there the detail pane doesn't exist until a
 *  separate screen is pushed. */
@Composable
private fun EmptyDetailPanePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Select an item to view its details",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
