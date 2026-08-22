package com.venunair.warden.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Created whenever "Mark serviced / renewed" is tapped (Sprint 5). Doubles as
 * a lightweight service-history log per item, which is a useful side effect
 * for warranty claims later even though that wasn't the primary reason it's
 * here — the primary reason is recomputing an AMC's next due date.
 */
@Entity(
    tableName = "service_events",
    foreignKeys = [ForeignKey(
        entity = Item::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class ServiceEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val date: LocalDate,
    val note: String? = null
)
