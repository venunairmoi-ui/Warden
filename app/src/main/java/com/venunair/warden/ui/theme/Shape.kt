package com.venunair.warden.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material3's default corner radii are fairly tight/corporate. Rounder
 * corners across buttons, text fields, cards and dialogs is one of the
 * cheapest ways to make an app read as "friendly" rather than a bare
 * scaffold — applied globally here so every component picks it up
 * automatically via MaterialTheme(shapes = WardenShapes).
 */
val WardenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
