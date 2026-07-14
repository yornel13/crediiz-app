package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.domain.model.FollowUp
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface FollowUpRepository {

    /** Save a new follow-up locally (syncStatus = PENDING). */
    suspend fun save(followUp: FollowUp)

    /**
     * Observe the active agenda: all PENDING + EXPIRED follow-ups (any date).
     * Bucketing into Vencidos / Programados / Pendientes is the ViewModel's
     * job, re-evaluating expiry against Panama time.
     */
    fun observeAgenda(): Flow<List<FollowUp>>

    /**
     * Observe the next pending follow-up for a client (soonest
     * `scheduledAt` after [now]). Drives the "Scheduled call" card on
     * Pre-Call. Emits null while the client has no future follow-up.
     */
    fun observeNextPendingForClient(clientId: String, now: Instant): Flow<FollowUp?>

    /** Pull the agenda from the server and merge into local DB. */
    suspend fun refreshAgenda(from: String? = null, to: String? = null): Result<Unit>

    /** Mark a follow-up as completed locally (completionSyncStatus = PENDING). */
    suspend fun markCompletedLocally(mobileSyncId: String, completedAt: Instant)

    /**
     * Auto-close every PENDING follow-up of [clientId] whose
     * `scheduledAt <= asOf`. Invoked from `PostCallViewModel.save()` so
     * any past-due follow-up is closed by the very call that
     * satisfied it, regardless of which entry point the agent used
     * (agenda card, clients list, auto-call queue, future deep link).
     *
     * Future-dated follow-ups for the same client are preserved.
     *
     * @return number of rows transitioned PENDING → COMPLETED.
     */
    suspend fun markPendingForClientCompleted(clientId: String, asOf: Instant): Int

    /**
     * LOCAL-ONLY cancellation of every still-active (PENDING/EXPIRED)
     * follow-up of [clientId]. Mirrors backend behavior the server performs
     * on its own — the BE-04 status cascade and `create`'s "latest schedule
     * wins" reschedule — so the agenda converges immediately instead of
     * waiting for the next pull. CANCELLED rows are sync-inert: the push
     * contract only carries creations and completions.
     *
     * PostCall's reschedule path MUST run [markPendingForClientCompleted]
     * first, so past-due rows earn their COMPLETED (+ completion push)
     * before the sweep cancels what remains.
     *
     * @return number of rows transitioned to CANCELLED.
     */
    suspend fun cancelActiveForClientLocally(clientId: String): Int

    suspend fun pendingCreationSync(): List<FollowUp>
    suspend fun pendingCompletionSync(): List<FollowUp>

    suspend fun markCreationSynced(mobileSyncIds: List<String>)
    suspend fun markCompletionSynced(mobileSyncIds: List<String>)

    suspend fun countPending(): Int

    /** Live count of follow-ups whose creation OR completion is pending sync. */
    fun observePendingCount(): Flow<Int>
}
