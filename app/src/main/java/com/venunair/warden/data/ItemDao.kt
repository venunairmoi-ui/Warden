package com.venunair.warden.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE status != 'ARCHIVED' ORDER BY expiryDate ASC")
    fun observeActiveItems(): Flow<List<Item>>

    // Feedback, 2026-08-26: powers the Archived items recovery screen --
    // most recently archived first (archivedAt), since "what did I just
    // archive" is the scenario that screen exists for.
    @Query("SELECT * FROM items WHERE status = 'ARCHIVED' ORDER BY archivedAt DESC")
    fun observeArchivedItems(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observeItem(id: Long): Flow<Item?>

    // Deliberately NOT @Insert(onConflict = REPLACE) as a fake upsert.
    // REPLACE resolves a primary-key conflict by DELETING the existing row
    // and inserting a new one in its place -- and because ReminderRule has
    // a FOREIGN KEY on itemId with onDelete = CASCADE, that delete silently
    // wipes every reminder_rules row for the item being edited, every
    // single time. @Update issues a real SQL UPDATE, which never deletes
    // the row, so the foreign key cascade never fires. Confirmed the
    // REPLACE behavior empirically against SQLite directly, not just from
    // docs -- it reproduces on every edit, not intermittently.
    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Delete
    suspend fun delete(item: Item)

    // Feedback, 2026-08-26: split out of a single generic setStatus(id,
    // status) (this was its only real usage) so archiving can stamp
    // archivedAt in the same statement -- see Item.archivedAt's doc
    // comment for why that field exists.
    @Query("UPDATE items SET status = 'ARCHIVED', archivedAt = :archivedAt WHERE id = :id")
    suspend fun archive(id: Long, archivedAt: LocalDate)

    @Query("UPDATE items SET status = 'ACTIVE', archivedAt = NULL WHERE id = :id")
    suspend fun unarchive(id: Long)

    @Query("UPDATE items SET expiryDate = :newExpiry WHERE id = :id")
    suspend fun updateExpiry(id: Long, newExpiry: LocalDate)

    // AMC service-visit tracking, 2026-08-26: marks that a "service due"
    // nudge was already sent for this specific expected date, so
    // ReminderCheckWorker doesn't repeat it daily -- see
    // Item.serviceDueNotifiedForDate's doc comment.
    @Query("UPDATE items SET serviceDueNotifiedForDate = :forDate WHERE id = :id")
    suspend fun updateServiceDueNotifiedForDate(id: Long, forDate: LocalDate)

    // Sprint 8: full-text search across name and vendor
    @Query(
        """SELECT * FROM items
           WHERE status != 'ARCHIVED'
             AND (name LIKE '%' || :query || '%' OR vendor LIKE '%' || :query || '%')
           ORDER BY expiryDate ASC"""
    )
    suspend fun searchByNameOrVendor(query: String): List<Item>

    @Query("SELECT * FROM items WHERE id IN (:ids) AND status != 'ARCHIVED'")
    suspend fun getByIds(ids: List<Long>): List<Item>
}
