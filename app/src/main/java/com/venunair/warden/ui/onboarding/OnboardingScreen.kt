package com.venunair.warden.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.venunair.warden.R
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val headline: String,
    val body: String
)

// UI redesign pass, 2026-08-25: reads from strings.xml's already-existing
// onboarding_page*_headline/body resources instead of a duplicate hardcoded
// copy -- the hardcoded version this replaces had drifted out of sync with
// the externalized strings (page 2's body still said "Warden pulls out...",
// a leftover from before the rename swept strings.xml but never touched
// this file). A function, not a top-level val, since stringResource needs
// a @Composable context.
@Composable
private fun onboardingPages() = listOf(
    OnboardingPage(
        icon = Icons.Filled.Shield,
        headline = stringResource(R.string.onboarding_page1_headline),
        body = stringResource(R.string.onboarding_page1_body)
    ),
    OnboardingPage(
        icon = Icons.Filled.CameraAlt,
        headline = stringResource(R.string.onboarding_page2_headline),
        body = stringResource(R.string.onboarding_page2_body)
    ),
    OnboardingPage(
        icon = Icons.Filled.NotificationsActive,
        headline = stringResource(R.string.onboarding_page3_headline),
        body = stringResource(R.string.onboarding_page3_body)
    ),
)

/**
 * Sprint 9: 3-screen first-launch flow. Shown once — MainActivity reads
 * UserPreferences.onboardingCompleted before the nav graph's start
 * destination is decided (see MainActivity.onboardingCompleted /
 * WardenNavHost's startAtOnboarding), and [onFinished] is what actually
 * persists the completed flag via SettingsRepository (see WardenNavHost's
 * Onboarding route) — this composable itself has no repository dependency,
 * it just reports "the user is done here."
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = onboardingPages()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onFinished) { Text(stringResource(R.string.onboarding_skip)) }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        // Page indicator dots
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            pages.indices.forEach { index ->
                val active = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Button(
            onClick = {
                val lastPage = pages.lastIndex
                if (pagerState.currentPage == lastPage) {
                    onFinished()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            // Pill CTA, 56dp tall -- same treatment as every other primary
            // action button this redesign pass (AddEditItemScreen's Save,
            // ItemDetailScreen's Mark serviced/renewed).
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .height(56.dp)
        ) {
            val isLastPage = pagerState.currentPage == pages.lastIndex
            Text(
                stringResource(if (isLastPage) R.string.onboarding_get_started else R.string.onboarding_next),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            page.headline,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
