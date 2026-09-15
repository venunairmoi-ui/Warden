package com.venunair.wisma.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.venunair.wisma.data.ItemCategory

/** Shared between Home and Detail so a category always reads the same way. */
fun categoryIcon(category: ItemCategory): ImageVector = when (category) {
    ItemCategory.WARRANTY -> Icons.Filled.Shield
    ItemCategory.INSURANCE -> Icons.Filled.Security
    ItemCategory.SUBSCRIPTION -> Icons.Filled.Autorenew
    ItemCategory.AMC -> Icons.Filled.Build
    ItemCategory.MEMBERSHIP -> Icons.Filled.CardMembership
    // Retaxonomy, 2026-08-25: catch-all migration bucket -- see
    // ItemCategory's own doc comment.
    ItemCategory.OTHER -> Icons.Filled.Category
}
