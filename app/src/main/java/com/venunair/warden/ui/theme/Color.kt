package com.venunair.warden.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Warden's palette — a "guardian" concept: deep teal-green (trust, calm,
 * established) as the brand color, warm gold as the accent (value, warranty,
 * a small nod to the "protecting something worth money" idea), soft warm
 * cream instead of stark white/gray for backgrounds so it doesn't read as a
 * generic default Material app.
 *
 * Every role Material3's ColorScheme defines is set explicitly below (not
 * just primary/secondary/background/error) — leaving the rest unset means
 * Compose fills them from Material3's own default baseline palette, which is
 * purple-leaning and would clash with this green/gold brand on every screen
 * that touches surface, container, or outline colors (i.e. almost all of
 * them). That mismatch is why the original 4-color-only scheme looked
 * generic rather than intentional.
 */

// Brand
val WardenPrimary = Color(0xFF2E5E4E)
val WardenOnPrimary = Color(0xFFFFFFFF)
val WardenPrimaryContainer = Color(0xFFB6E4CE)
val WardenOnPrimaryContainer = Color(0xFF092015)

val WardenSecondary = Color(0xFFB8860B)
val WardenOnSecondary = Color(0xFFFFFFFF)
val WardenSecondaryContainer = Color(0xFFFCE7B0)
val WardenOnSecondaryContainer = Color(0xFF3F2E00)

// Tertiary doubles as the "plenty of time left" urgency color on Home/Detail
// (see ItemUrgency in ui/theme/Urgency.kt) — a cool, calm blue reads as "no
// rush", clearly distinct in hue from both the green brand chrome and the
// gold accent so it never gets mistaken for either.
val WardenTertiary = Color(0xFF3D6B7A)
val WardenOnTertiary = Color(0xFFFFFFFF)
val WardenTertiaryContainer = Color(0xFFCFE9F2)
val WardenOnTertiaryContainer = Color(0xFF0C2229)

// Neutral surfaces — warm cream background with pure-white cards floating
// above it, rather than everything being one flat shade.
val WardenBackground = Color(0xFFFAF9F6)
val WardenOnBackground = Color(0xFF1B1C18)
val WardenSurface = Color(0xFFFFFFFF)
val WardenOnSurface = Color(0xFF1B1C18)
val WardenSurfaceVariant = Color(0xFFEDEAE2)
val WardenOnSurfaceVariant = Color(0xFF4B4A42)
val WardenOutline = Color(0xFFC9C5B8)

// Error — reserved for actual validation/failure states (form errors), not
// reused for "expiring soon" urgency; see Urgency.kt for why that's a
// separate concern.
val WardenError = Color(0xFFB3261E)
val WardenOnError = Color(0xFFFFFFFF)
val WardenErrorContainer = Color(0xFFF9DEDC)
val WardenOnErrorContainer = Color(0xFF410E0B)

// Dark theme variants — same hues, adjusted for a dark surface.
val WardenPrimaryDark = Color(0xFF9BD3BB)
val WardenOnPrimaryDark = Color(0xFF07351F)
val WardenPrimaryContainerDark = Color(0xFF15473A)
val WardenOnPrimaryContainerDark = Color(0xFFB6E4CE)

val WardenSecondaryDark = Color(0xFFE8C468)
val WardenOnSecondaryDark = Color(0xFF3F2E00)
val WardenSecondaryContainerDark = Color(0xFF5A4400)
val WardenOnSecondaryContainerDark = Color(0xFFFCE7B0)

val WardenTertiaryDark = Color(0xFFA6CDDA)
val WardenOnTertiaryDark = Color(0xFF0C2229)
val WardenTertiaryContainerDark = Color(0xFF254A54)
val WardenOnTertiaryContainerDark = Color(0xFFCFE9F2)

val WardenBackgroundDark = Color(0xFF14150F)
val WardenOnBackgroundDark = Color(0xFFE4E3DB)
val WardenSurfaceDark = Color(0xFF1D1E17)
val WardenOnSurfaceDark = Color(0xFFE4E3DB)
val WardenSurfaceVariantDark = Color(0xFF44443A)
val WardenOnSurfaceVariantDark = Color(0xFFC9C5B8)
val WardenOutlineDark = Color(0xFF8F8D80)

val WardenErrorDark = Color(0xFFF2B8B5)
val WardenOnErrorDark = Color(0xFF601410)
val WardenErrorContainerDark = Color(0xFF8C1D18)
val WardenOnErrorContainerDark = Color(0xFFF9DEDC)

// "Expiring soon but not yet overdue" — an orange distinct in hue from the
// gold secondary (olive-gold vs. clear orange) so a warning never reads as
// just brand chrome. Not part of Material3's ColorScheme roles (there's no
// built-in "warning" role), used directly wherever urgency is shown.
val WardenWarning = Color(0xFFE08214)
val WardenWarningDark = Color(0xFFFFB870)
