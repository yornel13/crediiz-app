package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.entity.ClientDismissalEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ClientDismissalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dismissal: ClientDismissalEntity)

    @Query("SELECT * FROM client_dismissals WHERE mobileSyncId = :id")
    suspend fun findById(id: String): ClientDismissalEntity?

    /**
     * Most recent active dismissal for a client, if any. "Active"
     * means `undone = false`. Used to power the Recientes "Deshacer"
     * button — an undone dismissal disappears from the UI, only the
     * active one renders.
     */
    @Query(
        """
        SELECT * FROM client_dismissals
        WHERE clientId = :clientId AND undone = 0
        ORDER BY dismissedAt DESC
        LIMIT 1
        """,
    )
    suspend fun findActiveForClient(clientId: String): ClientDismissalEntity?

    /**
     * Active dismissals inside the rolling window. Drives the
     * Recientes view alongside `ClientDao.observeRecent`.
     */
    @Query(
        """
        SELECT * FROM client_dismissals
        WHERE undone = 0 AND dismissedAt >= :since
        ORDER BY dismissedAt DESC
        """,
    )
    fun observeActiveSince(since: Instant): Flow<List<ClientDismissalEntity>>

    /** Items waiting for sync (push). */
    @Query("SELECT * FROM client_dismissals WHERE syncStatus = :status")
    suspend fun findBySyncStatus(status: SyncStatus): List<ClientDismissalEntity>

    @Query("SELECT COUNT(*) FROM client_dismissals WHERE syncStatus = :status")
    fun observeCountByStatus(status: SyncStatus): Flow<Int>

    @Query("UPDATE client_dismissals SET syncStatus = :status WHERE mobileSyncId IN (:ids)")
    suspend fun markSyncStatus(ids: List<String>, status: SyncStatus)
}
