package com.venunair.wisma.ui.common

import androidx.compose.runtime.compositionLocalOf
import com.venunair.wisma.data.Region

/**
 * The active display Region for the composition currently being built.
 * Provided once, near the root (see WardenNavHost), from the same
 * SettingsRepository.preferences collection every other setting already
 * reads through -- so every Composable below that point can call the
 * zero-arg toCurrencyString()/toDateString() overloads without threading
 * a Region parameter through every screen and ViewModel by hand.
 *
 * Defaults to Region.INDIA (matching UserPreferences' own default) so a
 * Composable that somehow renders before the real value is provided --
 * a preview, a test -- still shows today's India-only formatting instead
 * of crashing or silently rendering blank.
 */
val LocalRegion = compositionLocalOf { Region.INDIA }
