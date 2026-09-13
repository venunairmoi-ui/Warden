package com.venunair.warden.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.venunair.warden.R

/**
 * Wisma's three-font system (UI redesign pass, 2026-08-25) — replaces the
 * old DM-Sans-plus-system-default setup with the same role split, just
 * one font family per role instead of two:
 *   - **Sora**: display, headline, AND title roles (the old DM Sans
 *     footprint exactly — this is what makes it a mechanical swap, not a
 *     restructure: every screen that already read `titleMedium` etc. for
 *     an item name gets the new brand voice automatically, no call-site
 *     changes needed).
 *   - **Geist**: body roles — the technical/readable workhorse text,
 *     replacing the old system-default (Roboto) body text.
 *   - **Geist Mono**: label roles — NEW; the old scheme left label on
 *     system-default too. DESIGN.md calls this out specifically for
 *     "secondary metadata, transaction IDs, timestamps" — exactly the
 *     category micro-labels, reminder chips, and reference numbers the
 *     mockups apply it to.
 *
 * All three are Google Fonts-hosted, OFL-licensed (Sora: Omnibus-Type;
 * Geist/Geist Mono: Vercel). Fetched as their published variable fonts
 * and instantiated to static per-weight TTFs with `fonttools
 * varLib.instancer` (same reasoning as bundling static DM Sans weights
 * originally: matches this project's one established, working font-
 * bundling mechanism rather than introducing variable-font
 * `FontVariationSettings`, which has zero precedent in this codebase and
 * couldn't be verified on a real device from this sandbox).
 */
val SoraFamily = FontFamily(
    Font(R.font.sora_400, FontWeight.Normal),
    Font(R.font.sora_500, FontWeight.Medium),
    Font(R.font.sora_600, FontWeight.SemiBold),
    Font(R.font.sora_700, FontWeight.Bold),
)

val GeistFamily = FontFamily(
    Font(R.font.geist_400, FontWeight.Normal),
    Font(R.font.geist_500, FontWeight.Medium),
    Font(R.font.geist_600, FontWeight.SemiBold),
)

val GeistMonoFamily = FontFamily(
    Font(R.font.geist_mono_400, FontWeight.Normal),
    Font(R.font.geist_mono_500, FontWeight.Medium),
)

/**
 * Wisma's type scale. DESIGN.md's YAML only names 7 of Material3's 15
 * text styles explicitly (display-xl, headline-lg [+ a mobile override],
 * headline-md, body-lg, body-md, label-sm) — those 7 are transcribed
 * DIRECTLY below (marked "direct"). The other 8 roles are EXTRAPOLATED:
 * same font-family-per-role-band rule DESIGN.md establishes, sizes kept
 * close to the pre-redesign scale's own step pattern so the visual
 * rhythm between roles doesn't jump, just the typeface/weight/spacing
 * character. headlineLarge specifically uses DESIGN.md's
 * "headline-lg-mobile" override (28px, not headline-lg's 32px) since
 * this app is Android-only — the mobile override is the only one that
 * actually applies here.
 */
val WardenTypography = Typography(
    // Display — splash, onboarding, large empty-state headlines
    displayLarge = TextStyle( // direct: display-xl
        fontFamily = SoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 53.sp,
        letterSpacing = (-0.96).sp,
    ),
    displayMedium = TextStyle( // extrapolated
        fontFamily = SoraFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle( // extrapolated
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),

    // Headline — section headers, summary cards, TCO totals
    headlineLarge = TextStyle( // direct: headline-lg-mobile
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle( // direct: headline-md
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle( // extrapolated
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),

    // Title — TopAppBar titles, card headers (item names), dialog titles
    titleLarge = TextStyle( // extrapolated
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle( // extrapolated
        fontFamily = SoraFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle( // extrapolated
        fontFamily = SoraFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),

    // Body — Geist, the technical/readable workhorse
    bodyLarge = TextStyle( // direct: body-lg
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 29.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle( // direct: body-md
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle( // extrapolated
        fontFamily = GeistFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),

    // Label — Geist Mono, category chips / reminder chips / reference
    // numbers / uppercase micro-labels (see the mockups' fieldset headers)
    labelLarge = TextStyle( // extrapolated
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle( // extrapolated
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle( // direct: label-sm
        fontFamily = GeistMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.6.sp,
    ),
)
