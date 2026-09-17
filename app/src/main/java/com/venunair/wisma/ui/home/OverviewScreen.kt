package com.venunair.wisma.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.venunair.wisma.R
import com.venunair.wisma.autodetect.AutoDetectWorker
import com.venunair.wisma.data.ItemRepository
import com.venunair.wisma.data.seedSampleData
import com.venunair.wisma.reminders.ReminderCheckWorker
import com.venunair.wisma.ui.common.toCurrencyString
import com.venunair.wisma.ui.theme.ItemUrgency
import com.venunair.wisma.ui.theme.color
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.temporal.ChronoUnit

// Distinct from WardenApplication.AUTO_DETECT_WORK_NAME (the real periodic
// schedule) so a debug rescan and the background schedule can never be
// confused for each other or cancel one another. Was HomeScreen.kt's, moved
// here with the debug tools menu that uses it -- see HomeScreen.kt's own
// note at the spot this used to live.
private const val DEBUG_AUTO_DETECT_WORK_NAME = "debug_auto_detect_scan"

/**
 * Turns a finished AutoDetectWorker run's [WorkInfo] into a plain-English
 * diagnostic for the debug "run scan now" button -- see AutoDetectWorker's
 * KEY_* output-data constants for what each field means. Checked in
 * priority order: a real blocker (no media access) first, then "nothing to
 * look at", then "looked but no match", then "matched but couldn't
 * notify", then the success case. Moved from HomeScreen.kt along with the
 * debug menu that calls it.
 */
private fun describeAutoDetectResult(info: WorkInfo?): String {
    if (info == null) return "Auto-detect: scan result unavailable (timed out waiting)"
    return when (info.state) {
        WorkInfo.State.FAILED -> "Auto-detect: scan failed — see logcat"
        WorkInfo.State.CANCELLED -> "Auto-detect: scan was cancelled"
        WorkInfo.State.SUCCEEDED -> {
            val data = info.outputData
            val mediaGranted = data.getBoolean(AutoDetectWorker.KEY_MEDIA_PERMISSION_GRANTED, false)
            val notificationGranted = data.getBoolean(AutoDetectWorker.KEY_NOTIFICATION_PERMISSION_GRANTED, false)
            val examined = data.getInt(AutoDetectWorker.KEY_CANDIDATES_EXAMINED, 0)
            val matched = data.getInt(AutoDetectWorker.KEY_HEURISTIC_MATCHES, 0)
            val duplicatesSkipped = data.getInt(AutoDetectWorker.KEY_DUPLICATES_SKIPPED, 0)
            val posted = data.getInt(AutoDetectWorker.KEY_NOTIFICATIONS_POSTED, 0)
            val queued = data.getInt(AutoDetectWorker.KEY_SUGGESTIONS_QUEUED, 0)
            when {
                !mediaGranted -> "Auto-detect: photo access isn't granted"
                examined == 0 -> "Auto-detect: no new photos since the last scan"
                matched == 0 -> {
                    val decoded = data.getBoolean(AutoDetectWorker.KEY_TOP_CANDIDATE_BITMAP_DECODED, false)
                    val ocrOk = data.getBoolean(AutoDetectWorker.KEY_TOP_CANDIDATE_OCR_SUCCEEDED, false)
                    val textLen = data.getInt(AutoDetectWorker.KEY_TOP_CANDIDATE_TEXT_LENGTH, 0)
                    val hasCurrency = data.getBoolean(AutoDetectWorker.KEY_TOP_CANDIDATE_HAS_CURRENCY, false)
                    val hasKeyword = data.getBoolean(AutoDetectWorker.KEY_TOP_CANDIDATE_HAS_KEYWORD, false)
                    val detail = when {
                        !decoded -> "newest photo wouldn't decode"
                        !ocrOk -> "newest photo decoded but OCR failed"
                        else -> "newest photo: OCR read $textLen chars, amount=$hasCurrency keyword=$hasKeyword"
                    }
                    "Auto-detect: checked $examined photo(s), none matched — $detail"
                }
                else -> {
                    val dupSuffix = if (duplicatesSkipped > 0) ", $duplicatesSkipped duplicate(s) skipped" else ""
                    val notifSuffix = if (!notificationGranted) " (notifications off for Warden)" else ""
                    "Auto-detect: checked $examined photo(s) — $queued added to review$notifSuffix, $posted notification(s) posted$dupSuffix"
                }
            }
        }
        else -> "Auto-detect: still ${info.state.name.lowercase()}"
    }
}

private class OverviewViewModelFactory(private val repository: ItemRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
}

/**
 * Landing screen (feedback, 2026-08-25) -- replaces "My Products"
 * (HomeScreen.kt) as the screen shown right after Splash/Onboarding. Where
 * My Products used to lead with its own header + stat row + summary cards
 * before the actual item list, this screen IS just that leading block, on
 * its own: a total, a Active/Due soon/Expired count row, and an At risk /
 * Monthly subscriptions split card -- no item list, no scrolling. Tapping
 * any of them opens My Products already filtered to that slice (via
 * HomeViewModel.QuickFilter), which is where the item list itself still
 * lives, unchanged in substance from before this screen existed.
 *
 * Owns its own HomeViewModel instance (via OverviewViewModelFactory) purely
 * to read groupedItems/summary -- it never calls toggleCategory/
 * selectLocation/toggleQuickFilter on it, so those flows stay at their
 * defaults and this instance's numbers are always the unfiltered, whole-
 * app picture, exactly what a landing screen should show. My Products gets
 * its own separate instance when navigated to (standard Compose Navigation
 * per-destination scoping) -- both instances read the same Room-backed
 * repository, so they can never disagree.
 *
 * Bug fix, 2026-09-16, found via real on-device QA testing: this used to
 * say the layout was a plain Column deliberately NOT wrapped in
 * verticalScroll, with "fits the screen without a scroll" enforced
 * structurally. That didn't hold on a real device with realistic seeded
 * data -- the last card's content rendered underneath the floating Add
 * button, which the content padding never accounted for. Now wrapped in
 * verticalScroll (see the Column's own comment below) -- the only
 * guarantee that actually holds across every real device height and
 * data size, rather than a fixed layout tuned to whichever screen it was
 * last looked at on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    repository: ItemRepository,
    onAddItem: () -> Unit,
    onOpenSettings: () -> Unit,
    pendingSuggestionsCount: Int,
    onOpenAutoDetectSuggestions: () -> Unit,
    onOpenProducts: (filter: HomeViewModel.QuickFilter?, startInSearch: Boolean) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = OverviewViewModelFactory(repository))
    val grouped by viewModel.groupedItems.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Bug fix, 2026-08-26: was `grouped.active.size + grouped.renewalApproaching.size`
    // for Active, and Due soon/Expired read only from `grouped.expiringSoon` --
    // but groupByUrgency's renewalApproaching bucket (any recurring-billed,
    // auto-renewing item due within 30 days, including today) is a DIFFERENT
    // partition than Active/Due soon/Expired, not a subset of Active. A
    // billed, auto-renewing item due today or tomorrow landed in
    // renewalApproaching -- which the old Active math folded in -- so it
    // counted as "Active" and was invisible under Due soon entirely: exactly
    // the "where did my due-today item go" report. Reconstructing the full
    // (always-unfiltered, this screen's own ViewModel instance) item list
    // and classifying by day-math alone -- same convention every other
    // consumer of "is this expired/due soon" already uses -- means an
    // item's bucket here can never again depend on whether it auto-renews.
    // Bug fix, 2026-08-26 (follow-up): boundary moved from "due today counts
    // as Expired" to "due today counts as Due soon" -- see Urgency.kt's
    // urgencyOf and HomeViewModel's matching QuickFilter.DUE_SOON/EXPIRED
    // for the same fix, so a due-today item classifies the same way here,
    // in the drill-down list it opens, and on the item's own accent bar.
    val today = java.time.LocalDate.now()
    // Bug fix, 2026-09-16: grouped.expired must be included here now that
    // groupByUrgency actually populates it (see that function's own doc
    // comment) -- otherwise this line's own expiredCount below would
    // always read 0, since no expired item would appear in this sum at
    // all anymore.
    val allItems = grouped.expiringSoon + grouped.renewalApproaching + grouped.active + grouped.expired
    val expiredCount = allItems.count { it.expiryDate.isBefore(today) }
    val dueSoonCount = allItems.count { ChronoUnit.DAYS.between(today, it.expiryDate) in 0..30 }
    val activeCount = allItems.count { ChronoUnit.DAYS.between(today, it.expiryDate) > 30 }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            var debugMenuExpanded by remember { mutableStateOf(false) }
            TopAppBar(
                title = {
                    // Same icon+wordmark lockup My Products' top bar used
                    // to carry -- this is now the screen that opens it, so
                    // the brand lockup belongs here. Wordmark briefly
                    // changed to "Warden" during the 2026-09-15 Vault
                    // Ledger branding pass, then reverted the same day --
                    // "Warden" turned out to already be in use by several
                    // existing Android apps (WardenGPS, Warden: Security &
                    // Privacy, WardenCam), a real trademark/crowding risk
                    // not worth taking on for an unverified name when
                    // "Wisma" was already the known quantity. The palette/
                    // type direction from that pass stayed; only the name
                    // itself reverted. The mark drawable itself
                    // (ic_wisma_mark -- resource name unchanged, only its
                    // pixels were ever brand-specific) still reads fine
                    // under the new direction; letterSpacing loosened from
                    // -0.5sp, tuned for Sora's tight geometric tracking,
                    // which crowded Fraunces' serif letterforms at this
                    // size -- kept loosened, that part was never about the
                    // name.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_wisma_mark),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "WISMA",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { onOpenProducts(null, true) }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenAutoDetectSuggestions) {
                        BadgedBox(
                            badge = {
                                if (pendingSuggestionsCount > 0) {
                                    Badge { Text(pendingSuggestionsCount.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = "Receipt suggestions")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                    // Feedback, 2026-09-01: "The check reminders and Add
                    // test records are both missing. It needs to be built
                    // back." Both tools were gated behind BuildConfig.DEBUG,
                    // which is false in the signed release APK testers (and
                    // the user himself) actually install -- so this whole
                    // menu never rendered outside a debug-variant build.
                    // Decision: show it unconditionally in every build,
                    // release included, rather than a hidden unlock gesture
                    // or a second debug-signed APK -- these are internal
                    // testing tools, not a security boundary, and the
                    // simplicity of "always there" outweighs a beta tester
                    // possibly noticing a "⋮" debug menu.
                    IconButton(onClick = { debugMenuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Debug tools")
                    }
                    DropdownMenu(
                        expanded = debugMenuExpanded,
                        onDismissRequest = { debugMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Check reminders now") },
                            leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null) },
                            onClick = {
                                debugMenuExpanded = false
                                WorkManager.getInstance(context)
                                    .enqueue(OneTimeWorkRequestBuilder<ReminderCheckWorker>().build())
                                scope.launch { snackbarHostState.showSnackbar("Checking reminders…") }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Run scan now") },
                            leadingIcon = { Icon(Icons.Filled.ImageSearch, contentDescription = null) },
                            onClick = {
                                debugMenuExpanded = false
                                val request = OneTimeWorkRequestBuilder<AutoDetectWorker>()
                                    .setInputData(workDataOf(AutoDetectWorker.KEY_FORCE_FULL_RESCAN to true))
                                    .build()
                                val workManager = WorkManager.getInstance(context)
                                workManager.enqueueUniqueWork(
                                    DEBUG_AUTO_DETECT_WORK_NAME,
                                    ExistingWorkPolicy.REPLACE,
                                    request
                                )
                                scope.launch {
                                    val info = withTimeoutOrNull(90_000) {
                                        workManager.getWorkInfoByIdFlow(request.id)
                                            .first { it == null || it.state.isFinished }
                                    }
                                    val message = describeAutoDetectResult(info)
                                    snackbarHostState.showSnackbar(
                                        message = message,
                                        duration = SnackbarDuration.Long
                                    )
                                }
                            }
                        )
                        // Re-added, 2026-09-01 (was removed 2026-08-25 per
                        // that day's feedback, function kept dormant in
                        // SampleData.kt for exactly this "reconnect it
                        // later" case). Goes through the same
                        // ItemRepository.saveItem() every real Add does --
                        // see seedSampleData's own doc comment -- so these
                        // behave identically to hand-typed items, not a
                        // special-cased shortcut.
                        DropdownMenuItem(
                            text = { Text("Add test records") },
                            leadingIcon = { Icon(Icons.Filled.Science, contentDescription = null) },
                            onClick = {
                                debugMenuExpanded = false
                                scope.launch {
                                    repository.seedSampleData()
                                    snackbarHostState.showSnackbar("Test records added")
                                }
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // Feedback, 2026-09-17: "just show the plus sign inside the
            // circle" -- plain circular FAB instead of the text-label
            // ExtendedFloatingActionButton, so it doesn't compete visually
            // with the now-centered, decluttered cards above it.
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    ) { padding ->
        if (grouped.isEmpty) {
            OverviewEmptyState(modifier = Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                // Bug fix, 2026-09-16, found via real on-device QA testing:
                // this Column's own doc comment used to claim "fits the
                // screen without a scroll, enforced structurally" -- false
                // in practice. Scaffold's floatingActionButton floats over
                // the content area independent of the `padding` this
                // Column already applies (that padding only accounts for
                // the top bar), so nothing here was ever actually
                // reserving room for it. Confirmed live: with realistic
                // seeded data (11 items -- 3 stat tiles + the total header
                // + the money-split row), the last card's bottom portion
                // rendered directly underneath the Add button, and since
                // this Column had no scroll, that content had nowhere to
                // go if the phone's screen weren't tall enough -- silently
                // inaccessible, not just visually crowded. verticalScroll
                // is the only guarantee that holds across every real
                // device height and every realistic amount of data,
                // rather than a fixed layout that happens to fit on
                // whichever screen it was last actually looked at on.
                // Extra bottom space so the last card can scroll clear of
                // the floating Add button instead of stopping flush
                // against it.
                //
                // Bumped 96.dp -> 140.dp, 2026-09-17: the dashboard-
                // declutter pass above (smaller amount font, no per-
                // category breakdown line, centered content, tightened
                // 8.dp gaps) shrank the real content enough that its total
                // height plus the old 96.dp landed BELOW the viewport
                // height on a real device -- meaning this Column had
                // nothing left to scroll at all, so the Monthly recurring
                // costs card rendered at its natural (short) position with
                // the FAB statically parked on top of its corner, with no
                // way to scroll it clear. 140.dp reliably pushes total
                // content past viewport height so scrolling stays possible
                // and the real last card can always be swiped fully clear
                // of the FAB -- confirmed live on-device.
                .padding(bottom = 140.dp),
            // Feedback, 2026-09-17: "the gaps between cards are not even --
            // move the last two cards up to match the difference between
            // the top three cards" -- the three OverviewStatCards used to
            // sit in their own nested Column with spacedBy(8.dp) while
            // everything else on this screen (header-to-stats, stats-to-
            // money-cards, and between the two money cards themselves) used
            // this outer Column's spacedBy(16.dp), so the stat-card trio
            // read visibly tighter than the rest. Unified to 8.dp
            // everywhere and the now-redundant nested Column removed (its
            // only purpose was that different spacing) -- every gap on this
            // screen is the same size now. This also shrinks the total
            // content height, helping the next fix.
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OverviewTotalHeader(
                itemCount = grouped.totalCount,
                onClick = { onOpenProducts(null, false) }
            )
            OverviewStatCard(
                label = "Active",
                count = activeCount,
                tint = ItemUrgency.COMFORTABLE.color(),
                onClick = { onOpenProducts(HomeViewModel.QuickFilter.ACTIVE, false) }
            )
            OverviewStatCard(
                label = "Due soon",
                count = dueSoonCount,
                tint = ItemUrgency.SOON.color(),
                onClick = { onOpenProducts(HomeViewModel.QuickFilter.DUE_SOON, false) }
            )
            OverviewStatCard(
                label = "Expired",
                count = expiredCount,
                tint = ItemUrgency.OVERDUE.color(),
                onClick = { onOpenProducts(HomeViewModel.QuickFilter.EXPIRED, false) }
            )
            MoneyCard(
                icon = Icons.Filled.TrendingDown,
                label = "At risk this month",
                value = summary.moneyAtRisk.toCurrencyString(),
                subtitle = "${summary.expiringCount} item${if (summary.expiringCount != 1) "s" else ""} expiring",
                tint = ItemUrgency.SOON.color(),
                onClick = { onOpenProducts(HomeViewModel.QuickFilter.AT_RISK, false) }
            )
            MoneyCard(
                icon = Icons.Filled.Autorenew,
                label = "Monthly recurring costs",
                value = "${summary.totalRecurringMonthly.toCurrencyString()}/mo",
                // Feedback, 2026-09-17: "for the last card keep only the
                // label and the amount, remove the Insurance/Subscription
                // labels below" -- the per-category breakdown
                // (recurringBreakdownText) is no longer shown here at all;
                // that detail is still reachable by tapping through to the
                // filtered list, which now shows each item's own cost on
                // its own card (see ProductCard's recurring-cost line).
                subtitle = null,
                tint = MaterialTheme.colorScheme.primary,
                onClick = { onOpenProducts(HomeViewModel.QuickFilter.SUBSCRIPTIONS, false) }
            )
        }
    }
}

/**
 * Feedback, 2026-08-25: "the total at the top should not be inside a
 * card" -- plain text sitting directly on the screen background, visually
 * distinct from the bordered tiles/card below it. Still tappable (opens My
 * Products fully unfiltered) so there's always a way to reach every item,
 * not just the filtered slices the tiles below offer.
 */
@Composable
private fun OverviewTotalHeader(itemCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            itemCount.toString(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (itemCount == 1) "ITEM" else "ITEMS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Feedback, 2026-08-25, revised same day after a build review: the mockup
 * shows Active/Due Soon/Expired as three separate FULL-WIDTH stacked
 * cards (large centered number, small dot + uppercase label below) -- not
 * the compact 3-across row this composable originally drew (that was my
 * own substitution, made without checking it against the actual image,
 * and it read as "nowhere close" to the attachment once built). Rebuilt
 * to match the mockup's card shape directly: same bordered
 * surfaceContainerLow/outlineVariant/shapes.large convention as
 * OverviewTotalHeader and MoneyCard, sized compactly enough (18dp
 * vertical padding, not the mockup's much taller card) that three of
 * these stacked, plus the total header and the money card, still clear
 * one screen without scrolling -- the mockup's own screenshot does NOT
 * fit one screen at its literal card height (its money card is cut off),
 * which is the exact problem "reduce the height so it fits" asked to fix.
 * Tapping the whole card navigates straight to the filtered item list.
 */
@Composable
private fun OverviewStatCard(
    label: String,
    count: Int,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = tint
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(tint, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Feedback, 2026-09-17: "the last card feels cluttered and not neat like
 * above -- At Risk can be a card by itself and Monthly recurring can be
 * another card" -- replaces the old MoneySplitCard/MoneySplitHalf (one
 * bordered Card split into two halves by a vertical divider) with two
 * independent, full-width stacked cards, same surfaceContainerLow/
 * outlineVariant/shapes.large convention as OverviewStatCard and
 * OverviewTotalHeader above, so this screen reads as one consistent card
 * language throughout instead of the money card alone looking visually
 * denser than everything above it. Card's own onClick (not a Modifier.
 * clickable on an inner Column) for the same built-in ripple/accessibility
 * semantics OverviewStatCard already uses.
 *
 * Also drops the headline amount's font size from 20sp to 16sp per the
 * same feedback ("smaller font size for the amounts") -- each card now has
 * a full line to itself rather than competing with a sibling half for
 * width, so the amount no longer needs to be as large to read clearly.
 *
 * Feedback, 2026-09-17 (follow-up): "centre the contents. For the last
 * card keep only the label and the amount, remove the Insurance/
 * Subscription labels below" -- [subtitle] is now nullable (the Monthly
 * recurring costs call site drops the per-category breakdown entirely,
 * At risk this month keeps its "N items expiring" line), and every line
 * is centered (icon+label row, value, subtitle) to match
 * OverviewTotalHeader/OverviewStatCard's own centered convention above,
 * rather than this card alone being left-aligned.
 */
@Composable
private fun MoneyCard(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String?,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleMaxLines: Int = 2
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Empty state ─────────────────────────────────────────────────────
// Duplicated from HomeScreen.kt's own EmptyState (top-level `private` is
// file-scoped in Kotlin) -- shown here instead of the stat tiles/card when
// there are truly zero items anywhere, the same first-run state My
// Products used to show before this screen existed.

@Composable
private fun OverviewEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Nothing tracked yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Add your first warranty, subscription,\nor service contract to keep it safe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
