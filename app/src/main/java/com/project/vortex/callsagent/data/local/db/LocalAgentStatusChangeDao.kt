package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.project.vortex.callsagent.data.local.entity.LocalAgentStatusChangeEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface LocalAgentStatusChangeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocalAgentStatusChangeEntity)

    /**
     * Delete a single record — used by the repository to roll back the
     * local insert when the HTTP `agent-status-change` fails after we
     * had already optimistically marked the action.
     */
    @Query("DELETE FROM local_agent_status_changes WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Recientes feed — agent-initiated status changes inside [since]. */
    @Query(
        """
        SELECT * FROM local_agent_status_changes
        WHERE timestamp >= :since
        ORDER BY timestamp DESC
        """,
    )
    fun observeRecent(since: Instant): Flow<List<LocalAgentStatusChangeEntity>>

    @Query("DELETE FROM local_agent_status_changes")
    suspend fun deleteAll()
}
