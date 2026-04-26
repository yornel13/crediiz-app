package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.entity.InteractionEntity
import java.time.Instant

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

    @Query("SELECT COUNT(*) FROM interactions WHERE syncStatus = :status")
    fun observeCountBySyncStatus(status: SyncStatus): kotlinx.coroutines.flow.Flow<Int>

    @Query("UPDATE interactions SET syncStatus = :status WHERE mobileSyncId IN (:ids)")
    suspend fun markSyncStatus(ids: List<String>, status: SyncStatus)

    /**
     * Most recent interaction the agent never confirmed via Post-Call,
     * within the recovery window (24 h is what the recovery code uses).
     * Drives the orphan-call recovery flow on app start (Phase 7.5).
     */
    @Query(
        """
        SELECT * FROM interactions
        WHERE confirmedByAgent = 0 AND callEndedAt >= :since
        ORDER BY callEndedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findMostRecentUnconfirmed(since: Instant): InteractionEntity?

    @Query("UPDATE interactions SET confirmedByAgent = 1 WHERE mobileSyncId = :id")
    suspend fun markConfirmed(id: String)

    /**
     * Sweep step: any interaction older than the recovery window that
     * is still unconfirmed gets auto-confirmed silently. Prevents
     * eternal re-prompting if the agent ignored the recovery sheet.
     */
    @Query(
        """
        UPDATE interactions
        SET confirmedByAgent = 1
        WHERE confirmedByAgent = 0 AND callEndedAt < :before
        """,
    )
    suspend fun autoConfirmStale(before: Instant): Int
}
