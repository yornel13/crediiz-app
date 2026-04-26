package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.domain.model.FollowUp
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface FollowUpRepository {

    /** Save a new follow-up locally (syncStatus = PENDING). */
    suspend fun save(followUp: FollowUp)

    /** Observe pending follow-ups from a given instant forward (the agent's agenda). */
    fun observeAgenda(from: Instant): Flow<List<FollowUp>>

    /** Pull the agenda from the server and merge into local DB. */
    suspend fun refreshAgenda(from: String? = null, to: String? = null): Result<Unit>

    /** Mark a follow-up as completed locally (completionSyncStatus = PENDING). */
    suspend fun markCompletedLocally(mobileSyncId: String, completedAt: Instant)

    suspend fun pendingCreationSync(): List<FollowUp>
    suspend fun pendingCompletionSync(): List<FollowUp>

    suspend fun markCreationSynced(mobileSyncIds: List<String>)
    suspend fun markCompletionSynced(mobileSyncIds: List<String>)

    suspend fun countPending(): Int

    /** Live count of follow-ups whose creation OR completion is pending sync. */
    fun observePendingCount(): Flow<Int>
}
