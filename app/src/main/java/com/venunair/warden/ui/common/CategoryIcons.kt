package com.venunair.warden.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector
import com.venunair.warden.data.ItemCategory

/** Shared between Home and Detail so a category always reads the same way. */
fun categoryIcon(category: ItemCategory): ImageVector = when (category) {
    ItemCategory.WARRANTY -> Icons.Filled.Shield
    ItemCategory.AMC -> Icons.Filled.Build
    ItemCategory.SUBSCRIPTION -> Icons.Filled.Autorenew
    ItemCategory.DOCUMENT -> Icons.Filled.Description
}
