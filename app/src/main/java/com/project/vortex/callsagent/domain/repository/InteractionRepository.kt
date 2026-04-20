package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.domain.model.Interaction

interface InteractionRepository {

    /** Save a newly finished call locally (syncStatus = PENDING). */
    suspend fun save(interaction: Interaction)

    /** All pending-sync interactions (to be pushed on next sync). */
    suspend fun pendingSync(): List<Interaction>

    /** Mark these interactions as SYNCED after a successful server ack. */
    suspend fun markSynced(mobileSyncIds: List<String>)

    suspend fun countPending(): Int
}
