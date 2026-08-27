package com.venunair.warden.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Wisma's palette — UI redesign pass (2026-08-25), sourced from
 * `design/screens/wisma/DESIGN.md`'s YAML front matter (a literal
 * Material3 `ColorScheme` export — field names match Compose's
 * `ColorScheme` constructor exactly; verified against the actual hex
 * values used in the mockups' own `code.html`, not just the YAML in
 * isolation). Deep near-black surfaces, an electric-blue primary, and a
 * cool blue-lavender secondary/tertiary pair — replaces Warden's teal/
 * gold palette below.
 *
 * Symbol names stay `Warden*` (values change, names don't) — renaming to
 * `Wisma*` belongs in the eventual package/applicationId rename sweep,
 * not this values-only palette swap. Same reasoning `WardenTheme` in
 * Theme.kt already needs no changes: it only ever references these
 * symbol names, never their values.
 *
 * DESIGN.md is DARK-first (that's the primary brand mode described in
 * its prose: "Experimental Dark Mode Minimalism"). Only a dark scheme is
 * actually specified — the light companion below is DERIVED, not
 * spec-sourced, following Material3's own convention of reusing the
 * spec's "fixed" and "inverse" roles (which DESIGN.md already provides,
 * precisely because Material3 defines them to be the theme-appropriate
 * anchor for the opposite mode) rather than inventing new hex values
 * from scratch. Marked inline below. A prose section further down
 * DESIGN.md cites different, rounder hex values ("Midnight Canvas
 * #020617", primary "#0052FF") as brand narrative — checked directly
 * against `code.html`'s actual literal CSS values, which match the YAML
 * front matter exactly (#101415 background, #b7c4ff primary, etc.), not
 * the prose. Treating the YAML as the implementable source of truth and
 * the prose as descriptive copy, not a competing spec.
 */

// ── Dark scheme — DIRECT from DESIGN.md YAML ────────────────────────

val WardenPrimaryDark = Color(0xFFB7C4FF)
val WardenOnPrimaryDark = Color(0xFF002682)
val WardenPrimaryContainerDark = Color(0xFF0052FF)
val WardenOnPrimaryContainerDark = Color(0xFFDFE3FF)

val WardenSecondaryDark = Color(0xFFB9C7E4)
val WardenOnSecondaryDark = Color(0xFF233148)
val WardenSecondaryContainerDark = Color(0xFF3C4962)
val WardenOnSecondaryContainerDark = Color(0xFFABB9D6)

// Tertiary doubles as the "plenty of time left" urgency color on Home/Detail
// (see ItemUrgency in ui/theme/Urgency.kt) — same role the old teal palette
// used tertiary for, unchanged by this swap.
val WardenTertiaryDark = Color(0xFFB6C6ED)
val WardenOnTertiaryDark = Color(0xFF20304F)
val WardenTertiaryContainerDark = Color(0xFF566688)
val WardenOnTertiaryContainerDark = Color(0xFFDBE4FF)

val WardenBackgroundDark = Color(0xFF101415)
val WardenOnBackgroundDark = Color(0xFFE0E3E5)
val WardenSurfaceDark = Color(0xFF101415)
val WardenOnSurfaceDark = Color(0xFFE0E3E5)
val WardenSurfaceVariantDark = Color(0xFF323537)
val WardenOnSurfaceVariantDark = Color(0xFFC3C5D9)
val WardenOutlineDark = Color(0xFF8D90A2)

val WardenErrorDark = Color(0xFFFFB4AB)
val WardenOnErrorDark = Color(0xFF690005)
val WardenErrorContainerDark = Color(0xFF93000A)
val WardenOnErrorContainerDark = Color(0xFFFFDAD6)

// Dashboard restyle pass (2026-08-25): two of DESIGN.md's extended tonal
// roles, added on demand for the Dashboard's card/bento-grid treatment
// (mockup uses bg-surface-container-low for cards and tiles, and
// border-outline-variant everywhere for hairline borders/dividers).
// DESIGN.md's YAML actually specifies a full 8-role tonal ramp
// (surface-container/-low/-high/-highest/-lowest, surface-bright/-dim,
// inverse-primary) that Material3's ColorScheme also has slots for --
// only wiring the 2 this screen actually uses rather than the full ramp
// speculatively; add the rest here (DIRECT values already sitting in
// wisma_dashboard/code.html's tailwind config, ready to transcribe) the
// day a screen's mockup actually calls for one.
val WardenSurfaceContainerLowDark = Color(0xFF191C1E)
val WardenOutlineVariantDark = Color(0xFF434656)

// Splash screen pass (2026-08-25): a 3rd extended tonal role, wired for the
// exact reason the comment above invites -- the splash's mockup
// (wisma_login/code.html, the closest thing this app has to a splash
// mockup; see SplashScreen.kt's doc comment) specifically calls for a
// `background -> surface-container-high` gradient. DIRECT from that same
// mockup's tailwind config (#272a2c), transcribed rather than invented.
val WardenSurfaceContainerHighDark = Color(0xFF272A2C)

// Feedback pass (2026-08-25): a 4th extended tonal role, wired for the same
// reason as the two above -- ItemDetailScreen's Hero card needed to read as
// visibly darker/more recessed than the DetailSectionCards below it
// (currently both use surfaceContainerLow, so they look identical).
// DIRECT from DESIGN.md/wisma_login's tailwind config (#0b0f10) -- the
// darkest step of the same tonal ramp surfaceContainerLow/-High already
// draw from.
val WardenSurfaceContainerLowestDark = Color(0xFF0B0F10)

// Light — hand-derived (DESIGN.md has no light-mode extended tonal ramp
// to anchor to). Extends the same 2-tone "floating card on a tinted
// page" logic WardenSurface already established: surfaceContainerLow
// sits between background (#E0E3E5) and surface (#F7F8FA) rather than
// reusing either exactly, so a card and the page behind it stay visually
// distinct even where surface itself isn't in play. outlineVariant is a
// lighter, lower-contrast step off WardenOutline (#74777A), matching the
// dark scheme's own outline → outlineVariant relationship (variant is
// the fainter of the pair in both modes).
val WardenSurfaceContainerLow = Color(0xFFEDEFF1)
val WardenOutlineVariant = Color(0xFFC7CACE)

// Light companion to WardenSurfaceContainerHighDark above -- hand-derived
// (same reasoning as WardenSurfaceContainerLow: no DESIGN.md light anchor
// to trace to). Continues the SAME direction that light derivation already
// established -- background(#E0E3E5) < surfaceContainerLow(#EDEFF1) <
// surface(#F7F8FA), each step brighter -- one step brighter again, still
// short of surface itself so the two remain visually distinct.
val WardenSurfaceContainerHigh = Color(0xFFF2F3F5)

// Light companion to WardenSurfaceContainerLowestDark above -- hand-derived
// (no DESIGN.md light anchor, same as the other light extended roles).
// Continues the established light-mode direction (see
// WardenSurfaceContainerHigh's comment): each named step here is BRIGHTER
// than the last, so "lowest" -- the darkest of the group -- goes the other
// way, one step darker than background (#E0E3E5) itself.
val WardenSurfaceContainerLowest = Color(0xFFD3D6D9)

// ── Light scheme — DERIVED (see class doc) ───────────────────────────
// primary/secondary/tertiary reuse DESIGN.md's own "fixed" roles, which
// Material3 defines to already be the correct light-anchored tone for
// each hue (that's what "fixed" means: constant across light/dark) —
// not fabricated new hex values. background/surface reuse the "inverse"
// roles for the same reason: Material3 defines inverse-surface/
// inverse-on-surface as literally "what this neutral would look like in
// the other theme". surfaceVariant/onSurfaceVariant/outline have no
// such anchor in the spec and are genuinely derived by hand (kept in
// the same hue family, adjusted for light-mode contrast) — flagged
// specifically since those four don't trace back to a DESIGN.md value.

val WardenPrimary = Color(0xFF0038B6) // = DESIGN.md on-primary-fixed-variant
val WardenOnPrimary = Color(0xFFFFFFFF)
val WardenPrimaryContainer = Color(0xFFDDE1FF) // = DESIGN.md primary-fixed
val WardenOnPrimaryContainer = Color(0xFF001452) // = DESIGN.md on-primary-fixed

val WardenSecondary = Color(0xFF39475F) // = DESIGN.md on-secondary-fixed-variant
val WardenOnSecondary = Color(0xFFFFFFFF)
val WardenSecondaryContainer = Color(0xFFD6E3FF) // = DESIGN.md secondary-fixed
val WardenOnSecondaryContainer = Color(0xFF0D1C32) // = DESIGN.md on-secondary-fixed

val WardenTertiary = Color(0xFF374767) // = DESIGN.md on-tertiary-fixed-variant
val WardenOnTertiary = Color(0xFFFFFFFF)
val WardenTertiaryContainer = Color(0xFFD8E2FF) // = DESIGN.md tertiary-fixed
val WardenOnTertiaryContainer = Color(0xFF091B39) // = DESIGN.md on-tertiary-fixed

val WardenBackground = Color(0xFFE0E3E5) // = DESIGN.md inverse-surface
val WardenOnBackground = Color(0xFF2D3133) // = DESIGN.md inverse-on-surface
// Brighter than background, same "floating card on a tinted page" 2-tone
// approach the old cream/white light scheme used (see its own doc
// comment, preserved here) — not flattened to a single neutral just
// because DESIGN.md's light anchors happen to give background and
// surface the same starting value.
val WardenSurface = Color(0xFFF7F8FA) // hand-derived, no DESIGN.md anchor
val WardenOnSurface = Color(0xFF2D3133) // = DESIGN.md inverse-on-surface
val WardenSurfaceVariant = Color(0xFFDCE0E3) // hand-derived, no DESIGN.md anchor
val WardenOnSurfaceVariant = Color(0xFF43474A) // hand-derived, no DESIGN.md anchor
val WardenOutline = Color(0xFF74777A) // hand-derived, no DESIGN.md anchor

// Error — not brand-hued in either mode (Material3 convention; DESIGN.md
// doesn't specify a light error set either, only dark). Carried over
// unchanged from the pre-redesign palette: already tested, and error
// semantics have no reason to chase the new brand hue.
val WardenError = Color(0xFFB3261E)
val WardenOnError = Color(0xFFFFFFFF)
val WardenErrorContainer = Color(0xFFF9DEDC)
val WardenOnErrorContainer = Color(0xFF410E0B)

// "Expiring soon but not yet overdue" — not a Material3 ColorScheme role
// (there's no built-in "warning" role), used directly wherever urgency
// is shown. Not part of DESIGN.md's spec (no mockup shows an "expiring
// soon" example); carried over from the pre-redesign palette — a clear
// warm-amber reads as "warning" regardless of brand hue, and both values
// already have verified contrast against their respective backgrounds.
val WardenWarning = Color(0xFFE08214)
val WardenWarningDark = Color(0xFFFFB870)

// "Comfortably active, nothing due soon" — feedback pass (2026-08-25):
// ItemUrgency.COMFORTABLE used to reuse colorScheme.tertiary (a blue-
// lavender, matching the pre-redesign palette's own choice for this urgency
// tier), but a traffic-light convention (red=expired, amber=soon,
// green=safe) reads faster and more unambiguously than a same-family
// blue-vs-lavender distinction between COMFORTABLE and the brand's own
// primary/tertiary hues -- same reasoning WardenWarning above already
// applied to the SOON tier. Same Material "green 600"/"green 300" pairing
// convention as WardenWarning's amber: a clean mid-green for light
// backgrounds, a brighter light-green for dark-mode contrast.
val WardenSuccess = Color(0xFF1E8E3E)
val WardenSuccessDark = Color(0xFF81C995)

// "Overdue/expired" — feedback, 2026-08-26: ItemUrgency.OVERDUE used to
// reuse colorScheme.error directly rather than getting its own pair here
// like WardenWarning/WardenSuccess above already do -- the one tier that
// didn't follow that pattern. That mattered in practice: Material3's dark-
// theme error tone (WardenErrorDark, 0xFFFFB4AB) is DELIBERATELY desaturated
// per the M3 spec (a fully-saturated red on a near-black surface reads as
// too high-contrast/vibrating) -- correct for M3's actual error/validation
// role, but on a dark device it made the Expired accent bar/tile read as a
// washed-out salmon-orange instead of red, which is exactly what was
// reported. Same "red 700 / red 300" Material tone pairing convention as
// WardenWarning's amber and WardenSuccess's green: a clear red for light
// backgrounds, a brighter but still unambiguously-red tone for dark-mode
// contrast -- independent of the actual colorScheme.error role, which
// stays reserved for real validation/failure states (AddEditItemScreen's
// field errors) per Urgency.kt's own doc comment.
val WardenOverdue = Color(0xFFD32F2F)
val WardenOverdueDark = Color(0xFFE57373)
