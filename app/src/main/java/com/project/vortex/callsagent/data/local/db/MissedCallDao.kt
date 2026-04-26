package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.vortex.callsagent.data.local.entity.MissedCallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MissedCallDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MissedCallEntity)

    @Query("SELECT * FROM missed_calls ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<MissedCallEntity>>

    @Query("SELECT * FROM missed_calls WHERE acknowledged = 0 ORDER BY occurredAt DESC")
    fun observeUnacknowledged(): Flow<List<MissedCallEntity>>

    @Query("SELECT COUNT(*) FROM missed_calls WHERE acknowledged = 0")
    fun observeUnacknowledgedCount(): Flow<Int>

    @Query("UPDATE missed_calls SET acknowledged = 1 WHERE id = :id")
    suspend fun markAcknowledged(id: String)

    @Query("UPDATE missed_calls SET acknowledged = 1")
    suspend fun markAllAcknowledged()
}
