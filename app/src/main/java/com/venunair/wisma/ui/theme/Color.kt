package com.venunair.warden.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * "Vault Ledger" palette — branding pass (2026-09-15), replacing Wisma's
 * fintech electric-blue-on-near-black scheme. Direction: the reassurance
 * of a safety-deposit box crossed with the specificity of a household
 * ledger, not a trading-app dashboard. Full rationale, side-by-side
 * screen recreations, and type pairing are in the approved branding
 * mockup published this session (search this conversation's artifacts
 * for "Vault Ledger Direction" if revisiting this later).
 *
 * Three hue families, one per M3 role, each standing for a real part of
 * the "vault" metaphor rather than being picked for contrast alone:
 *   - **primary — vault green**: the felt-lined safety-deposit-box color
 *     code. Replaces the electric-blue primary everywhere it drove
 *     buttons, focus states, and "protected" framing.
 *   - **secondary — brass**: the vault-handle/wax-seal warm metal. Used
 *     sparingly (secondary role, not primary) — this is the one bold
 *     accent, not a wash applied everywhere.
 *   - **tertiary — steel**: the vault-door metal itself, a cool
 *     blue-grey. Fills the role the old blue-lavender tertiary held.
 *
 * Symbol names stay `Warden*` (values change, names don't) — same
 * reasoning the previous Wisma palette swap used: renaming to match
 * whatever the eventual app name/package identity lands on belongs in
 * that separate rename sweep, not this values-only swap.
 *
 * Dark is the primary/default mode (unchanged from before); the light
 * "stone/paper" companion below is hand-derived (no external design-doc
 * anchor to trace to, same situation the previous palette's light scheme
 * was already in for several of its roles) rather than lifted from a
 * spec. Extended tonal roles (surfaceContainer* ) follow the same
 * elevation convention already established in this file historically:
 * "Lowest" is the darkest tonal step and "Highest" the lightest, in BOTH
 * light and dark mode -- these are tint-overlay elevation steps, not a
 * statement about which one reads as more prominent on screen.
 */

// ── Dark scheme (primary/default mode) ──────────────────────────────

val WardenPrimaryDark = Color(0xFFA9C9BC) // pale sage-mint, legible on dark ground
val WardenOnPrimaryDark = Color(0xFF0F2B22)
val WardenPrimaryContainerDark = Color(0xFF2F5D50) // the vault green itself
val WardenOnPrimaryContainerDark = Color(0xFFDCEDE6)

val WardenSecondaryDark = Color(0xFFC9B98D) // pale brass
val WardenOnSecondaryDark = Color(0xFF3A2E12)
val WardenSecondaryContainerDark = Color(0xFF4A3D22) // deep brass-olive
val WardenOnSecondaryContainerDark = Color(0xFFE4D6AE)

// Tertiary — steel, the vault-door metal. Not an urgency role (see
// ItemUrgency in ui/theme/Urgency.kt, which has its own dedicated
// Warning/Success/Overdue pairs below) — general accent role only.
val WardenTertiaryDark = Color(0xFFAEC2C7)
val WardenOnTertiaryDark = Color(0xFF1B3236)
val WardenTertiaryContainerDark = Color(0xFF3E5559)
val WardenOnTertiaryContainerDark = Color(0xFFD3E4E8)

val WardenBackgroundDark = Color(0xFF1C1A17) // warm graphite, not pure near-black
val WardenOnBackgroundDark = Color(0xFFEDE8DE)
val WardenSurfaceDark = Color(0xFF1C1A17)
val WardenOnSurfaceDark = Color(0xFFEDE8DE)
val WardenSurfaceVariantDark = Color(0xFF3B352B)
val WardenOnSurfaceVariantDark = Color(0xFFC6BEAC)
val WardenOutlineDark = Color(0xFF8A8168)

val WardenErrorDark = Color(0xFFE5A08F) // warm salmon-brick, desaturated for dark-bg comfort
val WardenOnErrorDark = Color(0xFF5C1B0E)
val WardenErrorContainerDark = Color(0xFF7A2E1D)
val WardenOnErrorContainerDark = Color(0xFFFFDBCF)

val WardenSurfaceContainerLowDark = Color(0xFF26221D)
val WardenOutlineVariantDark = Color(0xFF3B352B)
val WardenSurfaceContainerHighDark = Color(0xFF2E2A23)
val WardenSurfaceContainerLowestDark = Color(0xFF141210)

// ── Light scheme ("stone/paper" companion) ───────────────────────────

val WardenSurfaceContainerLow = Color(0xFFF2EDE3)
val WardenOutlineVariant = Color(0xFFCFC5B2)
val WardenSurfaceContainerHigh = Color(0xFFF8F4EC)
val WardenSurfaceContainerLowest = Color(0xFFE6DFD0)

val WardenPrimary = Color(0xFF1E3C34) // deep vault-green ink
val WardenOnPrimary = Color(0xFFFFFFFF)
val WardenPrimaryContainer = Color(0xFFD3E6DD)
val WardenOnPrimaryContainer = Color(0xFF12281F)

val WardenSecondary = Color(0xFF6B4F1E) // deep brass ink
val WardenOnSecondary = Color(0xFFFFFFFF)
val WardenSecondaryContainer = Color(0xFFF0E4C4)
val WardenOnSecondaryContainer = Color(0xFF3A2B0E)

val WardenTertiary = Color(0xFF2C4448) // deep steel ink
val WardenOnTertiary = Color(0xFFFFFFFF)
val WardenTertiaryContainer = Color(0xFFD6E4E6)
val WardenOnTertiaryContainer = Color(0xFF14282B)

val WardenBackground = Color(0xFFEDE8E0) // warm stone, not cream
val WardenOnBackground = Color(0xFF211D17)
val WardenSurface = Color(0xFFF5F1EA)
val WardenOnSurface = Color(0xFF211D17)
val WardenSurfaceVariant = Color(0xFFE2DBCE)
val WardenOnSurfaceVariant = Color(0xFF4A4335)
val WardenOutline = Color(0xFF6F6754)

// Error — not brand-hued (Material3 convention, same reasoning the
// previous palette's error set already followed).
val WardenError = Color(0xFFA8442F) // brick red
val WardenOnError = Color(0xFFFFFFFF)
val WardenErrorContainer = Color(0xFFF5DBD3)
val WardenOnErrorContainer = Color(0xFF431006)

// "Expiring soon but not yet overdue" — traffic-light urgency convention
// kept from the previous palette (the accessibility review confirmed
// this "color + text label" pairing already works well; only the hues
// themselves shift toward the warmer, less fintech-bright Vault Ledger
// family).
val WardenWarning = Color(0xFFB06B16)
val WardenWarningDark = Color(0xFFE3A868)

// "Comfortably active, nothing due soon" — moss green, a close cousin of
// the vault-green primary rather than a separate hue family.
val WardenSuccess = Color(0xFF43714A)
val WardenSuccessDark = Color(0xFF8FC49A)

// "Overdue/expired" — deliberately separate from WardenError (see the
// previous palette's own note on this: colorScheme.error is reserved for
// real validation/failure states in AddEditItemScreen, not urgency).
// Kept more assertively red than the gentler validation-error brick so
// the two stay visually distinct, not just semantically distinct.
val WardenOverdue = Color(0xFFA8442F)
val WardenOverdueDark = Color(0xFFE2896F)
