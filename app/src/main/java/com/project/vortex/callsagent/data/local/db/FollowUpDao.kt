package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.entity.FollowUpEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface FollowUpDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(followUp: FollowUpEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(followUps: List<FollowUpEntity>)

    @Query(
        """
        SELECT * FROM follow_ups
        WHERE status = :status AND scheduledAt >= :from
        ORDER BY scheduledAt ASC
        """,
    )
    fun observePending(status: FollowUpStatus, from: Instant): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups WHERE mobileSyncId = :id")
    suspend fun findById(id: String): FollowUpEntity?

    @Query("SELECT * FROM follow_ups WHERE syncStatus = :status")
    suspend fun findBySyncStatus(status: SyncStatus): List<FollowUpEntity>

    /** Follow-ups that were marked completed locally but the completion isn't synced yet. */
    @Query("SELECT * FROM follow_ups WHERE status = 'COMPLETED' AND completionSyncStatus = :status")
    suspend fun findPendingCompletions(status: SyncStatus): List<FollowUpEntity>

    @Query("SELECT COUNT(*) FROM follow_ups WHERE syncStatus = :status OR completionSyncStatus = :status")
    suspend fun countPending(status: SyncStatus): Int

    @Query("UPDATE follow_ups SET syncStatus = :status WHERE mobileSyncId IN (:ids)")
    suspend fun markSyncStatus(ids: List<String>, status: SyncStatus)

    @Query("UPDATE follow_ups SET completionSyncStatus = :status WHERE mobileSyncId IN (:ids)")
    suspend fun markCompletionSyncStatus(ids: List<String>, status: SyncStatus)

    @Query(
        """
        UPDATE follow_ups
        SET status = 'COMPLETED',
            completedAt = :completedAt,
            completionSyncStatus = 'PENDING'
        WHERE mobileSyncId = :id
        """,
    )
    suspend fun markCompletedLocally(id: String, completedAt: Instant)

    @Transaction
    suspend fun replaceAgenda(followUps: List<FollowUpEntity>) {
        deletePending()
        upsertAll(followUps)
    }

    /** Delete only server-sourced pending follow-ups; keep locally-created PENDING sync records. */
    @Query("DELETE FROM follow_ups WHERE status = 'PENDING' AND syncStatus = 'SYNCED'")
    suspend fun deletePending()
}
