package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.data.local.entity.ClientEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients WHERE status = :status ORDER BY queueOrder ASC")
    fun observeByStatus(status: ClientStatus): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun findById(id: String): ClientEntity?

    @Query(
        """
        SELECT * FROM clients
        WHERE status = :status
          AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY queueOrder ASC
        """,
    )
    fun searchByStatus(status: ClientStatus, query: String): Flow<List<ClientEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(clients: List<ClientEntity>)

    @Transaction
    suspend fun replaceAll(clients: List<ClientEntity>) {
        deleteAll()
        upsert(clients)
    }

    @Query("DELETE FROM clients")
    suspend fun deleteAll()

    /**
     * Optimistic local update after a call — mirrors server side-effects.
     */
    @Query(
        """
        UPDATE clients
        SET callAttempts = callAttempts + 1,
            lastCalledAt = :lastCalledAt,
            lastOutcome = :lastOutcome,
            status = :newStatus,
            updatedAt = :now
        WHERE id = :clientId
        """,
    )
    suspend fun applyInteractionUpdate(
        clientId: String,
        lastCalledAt: Instant,
        lastOutcome: CallOutcome,
        newStatus: ClientStatus,
        now: Instant,
    )

    @Query("UPDATE clients SET lastNote = :note, updatedAt = :now WHERE id = :clientId")
    suspend fun updateLastNote(clientId: String, note: String, now: Instant)
}
