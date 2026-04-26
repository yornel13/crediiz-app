package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.domain.model.Interaction

interface InteractionRepository {

    /**
     * Upsert a call interaction locally with `syncStatus = PENDING`. The DAO
     * uses `OnConflictStrategy.REPLACE`, so saving an existing
     * `mobileSyncId` updates the row in place — used by Post-Call to amend
     * the outcome captured during the call.
     */
    suspend fun save(interaction: Interaction)

    /** Look up a single interaction by its local mobile-sync id. */
    suspend fun findById(mobileSyncId: String): Interaction?

    /** All pending-sync interactions (to be pushed on next sync). */
    suspend fun pendingSync(): List<Interaction>

    /** Mark these interactions as SYNCED after a successful server ack. */
    suspend fun markSynced(mobileSyncIds: List<String>)

    suspend fun countPending(): Int

    /** Live count of PENDING-sync interactions — drives the sync indicator. */
    fun observePendingCount(): kotlinx.coroutines.flow.Flow<Int>
}
