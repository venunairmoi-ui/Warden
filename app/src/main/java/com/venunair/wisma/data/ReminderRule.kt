package com.venunair.wisma.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "reminder_rules",
    foreignKeys = [ForeignKey(
        entity = Item::class,
        parentColumns = ["id"],
        childColumns = ["itemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("itemId")]
)
data class ReminderRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val daysBeforeExpiry: Int,
    val enabled: Boolean = true,
    // null = never fired. Set the day ReminderCheckWorker fires this rule's
    // notification, so it fires once and only once per rule rather than
    // every day the item stays within the offset window. Reset to null by
    // ItemRepository.markServiced() (a new expiry means the countdown starts
    // over) and by snoozing (re-arms it, gated by snoozedUntil below).
    val lastFiredDate: LocalDate? = null,
    // Set by the notification's "Snooze 7 days" action. The worker treats
    // this rule as not-yet-due again until this date, even though it's
    // otherwise eligible (daysLeft <= daysBeforeExpiry, lastFiredDate null).
    val snoozedUntil: LocalDate? = null
)

/** Default reminder offsets applied to a new item — spec section 4. */
val DEFAULT_REMINDER_OFFSETS = listOf(30, 7, 1)

/**
 * Every interval a user can pick from, on both the Add/Edit form (Sprint 8)
 * and the Settings screen's "Reminder defaults" section (Sprint 9). One
 * shared list so the two screens can't silently drift apart — each used to
 * define its own private copy of the same six numbers.
 */
val AVAILABLE_REMINDER_OFFSETS = listOf(90, 60, 30, 14, 7, 1)
