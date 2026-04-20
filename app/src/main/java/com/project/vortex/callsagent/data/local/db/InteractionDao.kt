package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.entity.InteractionEntity

@Dao
interface InteractionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interaction: InteractionEntity)

    @Query("SELECT * FROM interactions WHERE syncStatus = :status ORDER BY deviceCreatedAt ASC")
    suspend fun findBySyncStatus(status: SyncStatus): List<InteractionEntity>

    @Query("SELECT * FROM interactions WHERE mobileSyncId = :id")
    suspend fun findById(id: String): InteractionEntity?

    @Query("SELECT COUNT(*) FROM interactions WHERE syncStatus = :status")
    suspend fun countBySyncStatus(status: SyncStatus): Int

    @Query("UPDATE interactions SET syncStatus = :status WHERE mobileSyncId IN (:ids)")
    suspend fun markSyncStatus(ids: List<String>, status: SyncStatus)
}
