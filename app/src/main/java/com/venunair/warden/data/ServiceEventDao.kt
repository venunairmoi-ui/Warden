package com.venunair.warden.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceEventDao {
    @Query("SELECT * FROM service_events WHERE itemId = :itemId ORDER BY date DESC")
    fun observeForItem(itemId: Long): Flow<List<ServiceEvent>>

    @Insert
    suspend fun insert(event: ServiceEvent): Long
}
