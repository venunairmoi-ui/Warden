package com.venunair.warden.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class ItemCategory { WARRANTY, AMC, SUBSCRIPTION, DOCUMENT }
enum class ItemStatus { ACTIVE, EXPIRED, ARCHIVED }

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val vendor: String? = null,
    val category: ItemCategory,
    val purchaseDate: LocalDate? = null,
    val expiryDate: LocalDate,
    val cost: Double? = null,
    // Policy/contract/service-request number — mainly relevant for AMC and
    // WARRANTY items, but kept general rather than AMC-only since vendors
    // aren't consistent about what they call it (warranty card number,
    // service agreement no., etc.).
    val amcNumber: String? = null,
    val notes: String? = null,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val createdAt: LocalDate = LocalDate.now()
)
