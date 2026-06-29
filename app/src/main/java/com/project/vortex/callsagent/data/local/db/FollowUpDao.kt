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

    /**
     * Agenda feed. INNER JOIN against `clients` ensures we never
     * surface a follow-up whose client row has been removed from the
     * local cache — those rows would crash with "Client not found"
     * when the agent taps them.
     *
     * Follow-up rows can become orphaned when the client is removed from
     * the local mirror — i.e. unassigned or hard-deleted in the core
     * (`replaceAllAssigned`'s `deleteNotIn`). A client that merely turned
     * terminal (CONVERTED/REMOVED) is still assigned, so it stays and its
     * follow-up is not orphaned. `replaceAgenda` keeps the follow-up either
     * way, so the JOIN guards the genuine unassigned/hard-deleted case.
     *
     * Tracked as KI-04.b — long-term we want a server-fallback
     * `GET /clients/:id` so orphan follow-ups can still be opened.
     * For now the JOIN is the cleanest way to avoid the visible bug.
     */
    /**
     * Active agenda feed: every still-actionable follow-up — PENDING and
     * EXPIRED — regardless of date. We do NOT filter by date here: an EXPIRED
     * follow-up can be days old and must still surface ("se debía llamar"), and
     * the agenda bucketing (Vencidos / Programados / Pendientes) is decided in
     * the ViewModel by re-evaluating against Panama time. COMPLETED/CANCELLED
     * are excluded — they're done.
     */
    @Query(
        """
        SELECT f.* FROM follow_ups f
        INNER JOIN clients c ON c.id = f.clientId
        WHERE f.status IN ('PENDING', 'EXPIRED')
        ORDER BY f.scheduledAt ASC
        """,
    )
    fun observeActiveAgenda(): Flow<List<FollowUpEntity>>

    @Query("SELECT * FROM follow_ups WHERE mobileSyncId = :id")
    suspend fun findById(id: String): FollowUpEntity?

    /**
     * Next pending follow-up for the given client (the soonest
     * `scheduledAt` strictly after [now]). Powers the "Llamada
     * agendada" card on Pre-Call. Returns null when the client has
     * no future pending follow-up.
     */
    @Query(
        """
        SELECT * FROM follow_ups
        WHERE clientId = :clientId
          AND status = 'PENDING'
          AND scheduledAt > :now
        ORDER BY scheduledAt ASC
        LIMIT 1
        """,
    )
    fun observeNextPendingForClient(clientId: String, now: Instant): Flow<FollowUpEntity?>

    @Query("SELECT * FROM follow_ups WHERE syncStatus = :status")
    suspend fun findBySyncStatus(status: SyncStatus): List<FollowUpEntity>

    /** Follow-ups that were marked completed locally but the completion isn't synced yet. */
    @Query("SELECT * FROM follow_ups WHERE status = 'COMPLETED' AND completionSyncStatus = :status")
    suspend fun findPendingCompletions(status: SyncStatus): List<FollowUpEntity>

    @Query("SELECT COUNT(*) FROM follow_ups WHERE syncStatus = :status OR completionSyncStatus = :status")
    suspend fun countPending(status: SyncStatus): Int

    @Query("SELECT COUNT(*) FROM follow_ups WHERE syncStatus = :status OR completionSyncStatus = :status")
    fun observeCountPending(status: SyncStatus): Flow<Int>

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

    /**
     * Auto-close the follow-ups of [clientId] that the just-placed call
     * satisfies: every EXPIRED one (a vencido is always cumplido by the call,
     * regardless of its date) PLUS any PENDING whose `scheduledAt <= asOf`
     * (past-due / due now). Called from PostCall.save().
     *
     * Future-dated PENDING follow-ups (`scheduledAt > asOf`) are deliberately
     * left untouched: "llamar mañana" is still meaningful if the agent called
     * today for another reason.
     *
     * Returns the row count for telemetry / logging.
     */
    @Query(
        """
        UPDATE follow_ups
        SET status = 'COMPLETED',
            completedAt = :asOf,
            completionSyncStatus = 'PENDING'
        WHERE clientId = :clientId
          AND (
            (status = 'PENDING' AND scheduledAt <= :asOf)
            OR status = 'EXPIRED'
          )
        """,
    )
    suspend fun markPendingCompletedForClient(clientId: String, asOf: Instant): Int

    @Transaction
    suspend fun replaceAgenda(followUps: List<FollowUpEntity>) {
        deletePending()
        upsertAll(followUps)
    }

    /**
     * Delete server-sourced ACTIVE follow-ups (PENDING + EXPIRED) so
     * [replaceAgenda] can re-insert the fresh backend snapshot. Keeps
     * locally-created records (syncStatus != SYNCED) and terminal
     * COMPLETED/CANCELLED rows. This is also what makes a reschedule
     * reconcile: the old follow-up the backend cancelled is no longer in the
     * /agenda snapshot, so deleting + re-inserting drops it from the agenda.
     */
    @Query("DELETE FROM follow_ups WHERE status IN ('PENDING', 'EXPIRED') AND syncStatus = 'SYNCED'")
    suspend fun deletePending()
}
