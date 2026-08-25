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

    @Query("UPDATE items SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: ItemStatus)

    @Query("UPDATE items SET expiryDate = :newExpiry WHERE id = :id")
    suspend fun updateExpiry(id: Long, newExpiry: LocalDate)

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
