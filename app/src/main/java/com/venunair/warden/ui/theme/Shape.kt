package com.venunair.warden.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Wisma's shape scale (UI redesign pass, 2026-08-25) — DIRECT from
 * DESIGN.md's `rounded` token block (sm/DEFAULT/md/lg/xl), mapped
 * 1:1 onto Material3's 5 shape slots: DESIGN.md's own scale already has
 * exactly 5 non-pill steps, so no extrapolation was needed here, unlike
 * Type.kt's partial scale. `full` (9999px, i.e. a true pill) has no slot
 * in Material3's 5-size `Shapes` class — DESIGN.md's prose calls for
 * pill buttons/inputs specifically, which components apply directly via
 * their own `shape` parameter (`RoundedCornerShape(percent = 50)` or
 * `CircleShape`) during each screen's restyle, not through this global
 * object.
 */
val WardenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),  // DESIGN.md rounded.sm
    small = RoundedCornerShape(16.dp),      // DESIGN.md rounded.DEFAULT
    medium = RoundedCornerShape(24.dp),     // DESIGN.md rounded.md
    large = RoundedCornerShape(32.dp),      // DESIGN.md rounded.lg
    extraLarge = RoundedCornerShape(48.dp)  // DESIGN.md rounded.xl
)
