package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.domain.model.Interaction
import java.time.Instant

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

    /**
     * Phase 7.5 — orphan-call recovery.
     *
     * Returns the most recent interaction the agent never confirmed
     * via Post-Call save (within the [since] window). Used at app
     * start to surface a Post-Call screen for any call that ended
     * without the agent finishing the wrap-up (app crashed, force
     * killed, battery died, etc.).
     */
    suspend fun findMostRecentUnconfirmed(since: Instant): Interaction?

    /**
     * Mark the interaction as confirmed by the agent. Called from
     * `PostCallViewModel.save()` so subsequent app launches stop
     * surfacing this interaction as a recovery candidate.
     */
    suspend fun markConfirmed(mobileSyncId: String)

    /**
     * Sweep step: auto-confirm interactions whose `callEndedAt` is
     * older than [before]. Run at app start to prevent eternal
     * recovery prompts for calls the agent will never wrap up.
     * Returns the number of rows updated.
     */
    suspend fun autoConfirmStale(before: Instant): Int
}
