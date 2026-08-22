package com.venunair.warden.ui.common

import java.text.NumberFormat
import java.util.Locale

// en-IN gives ₹ with Indian digit grouping (₹1,23,456 not ₹123,456).
private val INDIAN_CURRENCY_FORMAT: NumberFormat =
    NumberFormat.getCurrencyInstance(Locale("en", "IN"))

fun Double.toIndianCurrencyString(): String = INDIAN_CURRENCY_FORMAT.format(this)

fun Double?.toIndianCurrencyStringOrDash(): String = this?.toIndianCurrencyString() ?: "—"
