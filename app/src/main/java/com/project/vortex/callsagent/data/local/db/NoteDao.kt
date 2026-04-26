package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE clientId = :clientId ORDER BY deviceCreatedAt DESC")
    fun observeByClient(clientId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE syncStatus = :status ORDER BY deviceCreatedAt ASC")
    suspend fun findBySyncStatus(status: SyncStatus): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE syncStatus = :status")
    suspend fun countBySyncStatus(status: SyncStatus): Int

    @Query("SELECT COUNT(*) FROM notes WHERE syncStatus = :status")
    fun observeCountBySyncStatus(status: SyncStatus): kotlinx.coroutines.flow.Flow<Int>

    @Query("UPDATE notes SET syncStatus = :status WHERE mobileSyncId IN (:ids)")
    suspend fun markSyncStatus(ids: List<String>, status: SyncStatus)
}
