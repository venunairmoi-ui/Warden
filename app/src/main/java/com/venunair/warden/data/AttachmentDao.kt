package com.venunair.warden.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE itemId = :itemId ORDER BY addedAt DESC")
    fun observeForItem(itemId: Long): Flow<List<Attachment>>

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Delete
    suspend fun delete(attachment: Attachment)
}
