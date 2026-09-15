package com.venunair.warden.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.venunair.warden.data.Region
import java.text.NumberFormat
import java.util.Locale

// en-IN gives ₹ with Indian digit grouping (₹1,23,456 not ₹123,456). This is
// the exact same Locale that was hardcoded here before Region existed --
// the INDIA branch below is that literal value, not a re-derived
// approximation of it, which is what makes adding other regions safe to
// ship without changing anything for an India user who never opens
// Settings. See Region.kt's doc comment.
private val INDIAN_CURRENCY_FORMAT: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val US_CURRENCY_FORMAT: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US)
private val UK_CURRENCY_FORMAT: NumberFormat = NumberFormat.getCurrencyInstance(Locale.UK)
private val CANADA_CURRENCY_FORMAT: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale("en", "CA"))
private val AUSTRALIA_CURRENCY_FORMAT: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale("en", "AU"))

private fun currencyFormatFor(region: Region): NumberFormat = when (region) {
    Region.INDIA -> INDIAN_CURRENCY_FORMAT
    Region.UNITED_STATES -> US_CURRENCY_FORMAT
    Region.UNITED_KINGDOM -> UK_CURRENCY_FORMAT
    Region.CANADA -> CANADA_CURRENCY_FORMAT
    Region.AUSTRALIA -> AUSTRALIA_CURRENCY_FORMAT
}

/** Plain, region-parameterized formatter -- usable anywhere, including
 *  non-Composable contexts (DigestNotificationWorker, buildClaimInfoText)
 *  where there is no composition to read a CompositionLocal from. */
fun Double.toCurrencyString(region: Region): String = currencyFormatFor(region).format(this)

fun Double?.toCurrencyStringOrDash(region: Region): String =
    this?.toCurrencyString(region) ?: "—"

/** Composable convenience overload: reads the ambient Region (see
 *  LocalRegion / WardenNavHost) so the ~28 call sites inside screens don't
 *  need to thread a Region argument through every ViewModel just to
 *  format a number. */
@Composable
@ReadOnlyComposable
fun Double.toCurrencyString(): String = toCurrencyString(LocalRegion.current)

@Composable
@ReadOnlyComposable
fun Double?.toCurrencyStringOrDash(): String = toCurrencyStringOrDash(LocalRegion.current)
