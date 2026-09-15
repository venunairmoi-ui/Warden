package com.venunair.wisma.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderRuleDao {
    @Query("SELECT * FROM reminder_rules WHERE itemId = :itemId")
    fun observeForItem(itemId: Long): Flow<List<ReminderRule>>

    /** Sprint 5: the WorkManager worker reads through this to know what to schedule. */
    @Query("SELECT * FROM reminder_rules WHERE enabled = 1")
    suspend fun getAllEnabled(): List<ReminderRule>

    /** Sprint 5: ReminderActionWorker looks up the specific rule a Snooze tap targets. */
    @Query("SELECT * FROM reminder_rules WHERE id = :id")
    suspend fun getRule(id: Long): ReminderRule?

    @Insert
    suspend fun insertAll(rules: List<ReminderRule>)

    @Update
    suspend fun update(rule: ReminderRule)

    @Query("DELETE FROM reminder_rules WHERE itemId = :itemId")
    suspend fun deleteForItem(itemId: Long)

    /**
     * Sprint 5: called from ItemRepository.markServiced — a new expiry date
     * means every rule's countdown should start over, otherwise a rule that
     * already fired for the OLD expiry would just stay silent forever
     * against the new one.
     *
     * Returns the number of rows updated (Room fills this in automatically
     * for an @Query UPDATE with an Int/Long return type) — ItemRepository
     * uses a 0 here to detect an item with NO rules left to reset at all,
     * the repair-path signal for an item whose rules were previously wiped
     * by the now-fixed upsert(REPLACE) cascade-delete bug.
     */
    @Query("UPDATE reminder_rules SET lastFiredDate = NULL, snoozedUntil = NULL WHERE itemId = :itemId")
    suspend fun resetForItem(itemId: Long): Int

    /** Sprint 8: one-shot read of reminder offsets for the edit form. */
    @Query("SELECT * FROM reminder_rules WHERE itemId = :itemId")
    suspend fun getForItem(itemId: Long): List<ReminderRule>
}
