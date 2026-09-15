package com.venunair.wisma.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.venunair.wisma.R

/**
 * "Vault Ledger" three-font system (branding pass, 2026-09-15) — replaces
 * Wisma's Sora/Geist/Geist Mono trio with the same role split (display+
 * headline+title / body / label), just three different families chosen
 * for a "personal records vault" character instead of a fintech one. See
 * the approved branding mockup (published this session) for the full
 * palette/type rationale — short version: Sora/Geist read as confident
 * startup-fintech geometric sans, which is exactly the aesthetic the
 * commercial-readiness review asked to move away from.
 *   - **Fraunces**: display, headline, AND title roles — a warm, slightly
 *     old-fashioned serif with real personality (soft, engraved-feeling
 *     curves), used at its 36pt optical size for on-screen weight. Same
 *     mechanical-swap footprint Sora had: every screen already reading
 *     `titleMedium` etc. picks up the new voice with no call-site changes.
 *   - **Archivo**: body roles — a grounded, sturdy grotesque, plainer and
 *     less "startup-neutral" than Geist.
 *   - **IBM Plex Mono**: label roles — reference numbers, category chips,
 *     dates in tabular contexts read more like ledger entries than
 *     dashboard data with this pairing than Geist Mono's very modern,
 *     product-dashboard character.
 *
 * All three are Google Fonts-hosted, OFL-licensed (Fraunces: Klim Type
 * Foundry/Undercase; Archivo: Omnibus-Type; IBM Plex Mono: IBM). Google's
 * font server returns already-static per-weight TTFs directly when the
 * request pins every variable axis (Fraunces' opsz=40 plus each wght) --
 * no `fonttools varLib.instancer` step needed this time, unlike Sora/
 * Geist's original bundling (verified: none of these 9 files carry an
 * `fvar` table).
 */
val FrauncesFamily = FontFamily(
    Font(R.font.fraunces_400, FontWeight.Normal),
    Font(R.font.fraunces_500, FontWeight.Medium),
    Font(R.font.fraunces_600, FontWeight.SemiBold),
    Font(R.font.fraunces_700, FontWeight.Bold),
)

val ArchivoFamily = FontFamily(
    Font(R.font.archivo_400, FontWeight.Normal),
    Font(R.font.archivo_500, FontWeight.Medium),
    Font(R.font.archivo_600, FontWeight.SemiBold),
)

val IbmPlexMonoFamily = FontFamily(
    Font(R.font.ibm_plex_mono_400, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_500, FontWeight.Medium),
)

/**
 * Type scale. The step pattern (sizes/line-heights) traces back to
 * DESIGN.md's YAML, which only names 7 of Material3's 15 text styles
 * explicitly (display-xl, headline-lg [+ a mobile override], headline-md,
 * body-lg, body-md, label-sm) — those 7 are transcribed DIRECTLY below
 * (marked "direct"). The other 8 roles are EXTRAPOLATED: same
 * font-family-per-role-band rule DESIGN.md establishes, sizes kept close
 * to the pre-redesign scale's own step pattern so the visual rhythm
 * between roles doesn't jump, just the typeface/weight/spacing character.
 * headlineLarge specifically uses DESIGN.md's "headline-lg-mobile"
 * override (28px, not headline-lg's 32px) since this app is Android-only
 * — the mobile override is the only one that actually applies here. This
 * sizing rhythm is layout, not brand voice, so the Vault Ledger branding
 * pass (2026-09-15) left it untouched aside from one exception below.
 */
val WardenTypography = Typography(
    // Display — splash, onboarding, large empty-state headlines
    displayLarge = TextStyle( // direct: display-xl
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 53.sp,
        // Vault Ledger pass: was -0.96sp, tuned for Sora's tight geometric
        // sans -- that much negative tracking on a serif at this size
        // crowds Fraunces' letterforms into each other. Zeroed out rather
        // than re-guessed at a new negative value with no device to
        // actually check kerning against.
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle( // extrapolated
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle( // extrapolated
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),

    // Headline — section headers, summary cards, TCO totals
    headlineLarge = TextStyle( // direct: headline-lg-mobile
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle( // direct: headline-md
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle( // extrapolated
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),

    // Title — TopAppBar titles, card headers (item names), dialog titles
    titleLarge = TextStyle( // extrapolated
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle( // extrapolated
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle( // extrapolated
        fontFamily = FrauncesFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // Body — Geist, the technical/readable workhorse
    bodyLarge = TextStyle( // direct: body-lg
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle( // direct: body-md
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle( // extrapolated
        fontFamily = ArchivoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),

    // Label — IBM Plex Mono, category chips / reminder chips / reference
    // numbers / uppercase micro-labels (see the mockups' fieldset headers)
    labelLarge = TextStyle( // extrapolated
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle( // extrapolated
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle( // direct: label-sm
        fontFamily = IbmPlexMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.6.sp,
    ),
)
