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

    // AMC service-visit tracking follow-up, 2026-08-26 -- see
    // Attachment.serviceEventId's doc comment.
    @Query("SELECT * FROM attachments WHERE serviceEventId = :serviceEventId ORDER BY addedAt DESC")
    fun observeForServiceEvent(serviceEventId: Long): Flow<List<Attachment>>

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Delete
    suspend fun delete(attachment: Attachment)

    // Sprint 8: search attachments by OCR text, returning the owning item IDs
    @Query(
        """SELECT DISTINCT itemId FROM attachments
           WHERE rawOcrText LIKE '%' || :query || '%'"""
    )
    suspend fun searchItemIdsByOcrText(query: String): List<Long>

    /** Return the first matching OCR snippet for a given item + query (for search result display). */
    @Query(
        """SELECT rawOcrText FROM attachments
           WHERE itemId = :itemId AND rawOcrText LIKE '%' || :query || '%'
           LIMIT 1"""
    )
    suspend fun getOcrSnippetForItem(itemId: Long, query: String): String?

    /** Return the thumbnail URI for the first attachment of a given item (for search result display). */
    @Query(
        """SELECT COALESCE(thumbnailUri, localFileUri) FROM attachments
           WHERE itemId = :itemId
           ORDER BY addedAt ASC LIMIT 1"""
    )
    suspend fun getFirstThumbnailForItem(itemId: Long): String?

    // Settings "Delete all my data" control -- read every attachment BEFORE
    // ItemDao.deleteAll() cascades the rows away, so the caller still has
    // each localFileUri/thumbnailUri to clean up on disk afterward (same
    // division of responsibility as getAttachments()/deleteItem()).
    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<Attachment>
}
