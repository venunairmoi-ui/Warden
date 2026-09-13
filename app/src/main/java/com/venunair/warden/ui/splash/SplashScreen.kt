package com.venunair.warden.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.venunair.warden.R
import kotlinx.coroutines.delay

/**
 * App-launch brand screen — the closest thing among the 4 mockups to a
 * "splash screen" is wisma_login/code.html's brand header (logo-in-a-
 * rounded-square, WISMA wordmark, tagline, on a dark gradient) — the login
 * FORM below that header is not built here, since this app has no
 * accounts/auth to log into; only the brand lockup applies.
 *
 * This is deliberately separate from the native Android 12+ SplashScreen
 * (see themes.xml's Theme.Warden.Splash / MainActivity's
 * installSplashScreen()), which stays exactly as-is and still covers the
 * ~200ms+ hold while onboarding's completed flag is read from DataStore.
 * That system API can only show a static icon on a solid color — it can't
 * do a gradient background or a wordmark+tagline lockup, so it can't
 * reproduce this mockup on its own. This screen is what the *system*
 * splash hands off to: a brief, self-contained Compose screen that holds
 * for [SPLASH_HOLD_MS] purely so the brand moment registers, then
 * auto-advances. It never blocks on I/O (onboarding's flag is already
 * resolved by the time it's reachable — see WardenNavHost's skipSplash
 * logic) — the hold is a deliberate pause, not a load time.
 *
 * WardenNavHost skips this screen entirely on a cold start that arrives
 * via a deep link or a share hand-off (a notification tap while the app
 * was fully closed) — that path should land the user on their target
 * screen immediately, not detour through a brand moment first.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(SPLASH_HOLD_MS)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_wisma_splash_mark),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer)
                )
            }
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                // displayLarge, not headlineLarge -- Type.kt's own doc
                // comment earmarks displayLarge specifically for "splash,
                // onboarding, large empty-state headlines" (48sp Sora
                // Bold), matching the mockup's own display-xl treatment.
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// Not user-perceptible as "loading" (onboarding's flag is already resolved
// by the time this screen is reachable — see the class doc), just long
// enough for the brand lockup to actually register rather than flash by.
private const val SPLASH_HOLD_MS = 600L
