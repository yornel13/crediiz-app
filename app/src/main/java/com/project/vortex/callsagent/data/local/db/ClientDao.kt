package com.project.vortex.callsagent.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.data.local.entity.ClientEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients WHERE status = :status ORDER BY queueOrder ASC")
    fun observeByStatus(status: ClientStatus): Flow<List<ClientEntity>>

    /**
     * Pendientes feed — clients the agent has **never** called.
     *
     * In the 5-state model "new" is derived: `PENDING` with
     * `callAttempts == 0`. The first no-contact outcome keeps the client
     * `PENDING` (the backend never promotes to a separate state) but
     * bumps `callAttempts`, moving it to the [observePendingForRetry] feed.
     */
    @Query(
        """
        SELECT * FROM clients
        WHERE status = 'PENDING' AND callAttempts = 0
        ORDER BY queueOrder ASC
        """,
    )
    fun observePendingNeverCalled(): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT * FROM clients
        WHERE status = 'PENDING' AND callAttempts = 0
          AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY queueOrder ASC
        """,
    )
    fun searchPendingNeverCalled(query: String): Flow<List<ClientEntity>>

    /**
     * "Para reintentar" feed — `PENDING` clients already called at least
     * once without a closing outcome (`callAttempts > 0`). Sort by
     * `lastCalledAt` ascending so the oldest attempt floats to the top:
     * those are the most ready to dial again. NULL `lastCalledAt` sorts
     * first thanks to SQLite's NULL ordering.
     */
    @Query(
        """
        SELECT * FROM clients
        WHERE status = 'PENDING' AND callAttempts > 0
        ORDER BY lastCalledAt ASC
        """,
    )
    fun observePendingForRetry(): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT * FROM clients
        WHERE status = 'PENDING' AND callAttempts > 0
          AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY lastCalledAt ASC
        """,
    )
    fun searchPendingForRetry(query: String): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :id")
    suspend fun findById(id: String): ClientEntity?

    /** Reactive single-client stream — re-emits whenever the row changes. */
    @Query("SELECT * FROM clients WHERE id = :id")
    fun observeById(id: String): Flow<ClientEntity?>

    /**
     * Match by the last 8 digits of the (digits-only) phone number, so a
     * call from `+507 6680-1776` matches an assigned `66801776`,
     * `+5076680-1776`, etc. Returns the first match — KI / UX-12 covers
     * the duplicate-phone case if it ever surfaces.
     */
    @Query(
        """
        SELECT * FROM clients
        WHERE substr(
            replace(replace(replace(replace(phone, ' ', ''), '-', ''), '+', ''), '(', ''),
            -8
        ) = substr(
            replace(replace(replace(replace(:phone, ' ', ''), '-', ''), '+', ''), '(', ''),
            -8
        )
        LIMIT 1
        """,
    )
    suspend fun findByNormalizedPhone(phone: String): ClientEntity?

    @Query(
        """
        SELECT * FROM clients
        WHERE status = :status
          AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY queueOrder ASC
        """,
    )
    fun searchByStatus(status: ClientStatus, query: String): Flow<List<ClientEntity>>

    /**
     * Recent calls feed for the "Recientes" view. Includes any client
     * whose `lastCalledAt` falls inside the rolling window (24 h is the
     * v1.0 default — caller passes the cutoff). Ordered newest first.
     *
     * Outcome-agnostic: every [CallOutcome] shows up. The card variant in
     * the UI picks different visuals based on `lastOutcome`.
     */
    @Query(
        """
        SELECT * FROM clients
        WHERE lastCalledAt IS NOT NULL
          AND lastCalledAt >= :since
        ORDER BY lastCalledAt DESC
        """,
    )
    fun observeRecent(since: Instant): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT * FROM clients
        WHERE lastCalledAt IS NOT NULL
          AND lastCalledAt >= :since
          AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY lastCalledAt DESC
        """,
    )
    fun searchRecent(since: Instant, query: String): Flow<List<ClientEntity>>

    /**
     * "Sin agendar" feed for the Agenda tab — INTERESTED leads that
     * have NO pending follow-up scheduled in the future. Three real
     * paths land a client here:
     *
     *  1. Admin reassigned an INTERESTED client from another agent
     *     (the previous agent's follow-ups got cancelled server-side).
     *  2. The agent completed a follow-up but didn't schedule the next.
     *  3. A follow-up's `scheduledAt` passed without being marked
     *     completed.
     *
     * Sort: oldest `assignedAt` first — the older the orphan, the
     * more urgent (most likely to go cold).
     */
    @Query(
        """
        SELECT c.* FROM clients c
        WHERE c.status = 'INTERESTED'
          AND NOT EXISTS (
            SELECT 1 FROM follow_ups f
            WHERE f.clientId = c.id
              AND f.status = 'PENDING'
              AND f.scheduledAt > :now
          )
        ORDER BY c.assignedAt ASC
        """,
    )
    fun observeUnscheduledInterested(now: Instant): Flow<List<ClientEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(clients: List<ClientEntity>)

    /**
     * Status-scoped replace. Deletes only the rows currently sitting in
     * [status] and inserts the new server snapshot. This is the path
     * Sync uses — calling [replaceAll] consecutively for PENDING and
     * INTERESTED would clobber the first set on the second call (KI-02).
     *
     * Note: a client that moved from PENDING → INTERESTED on the server
     * is correctly removed from the PENDING set on the PENDING refresh,
     * then re-inserted with status=INTERESTED on the INTERESTED refresh.
     */
    @Transaction
    suspend fun replaceAllByStatus(status: ClientStatus, clients: List<ClientEntity>) {
        deleteByStatus(status)
        upsert(clients)
    }

    @Query("DELETE FROM clients WHERE status = :status")
    suspend fun deleteByStatus(status: ClientStatus)

    /**
     * Wipe the whole table — used by logout / fresh-install paths only.
     * Do NOT call from sync; use [replaceAllByStatus] there.
     */
    @Transaction
    suspend fun replaceAll(clients: List<ClientEntity>) {
        deleteAll()
        upsert(clients)
    }

    @Query("DELETE FROM clients")
    suspend fun deleteAll()

    /**
     * Local bookkeeping after a call. Bumps the attempt counter and
     * records the outcome — but **does NOT decide the status**. In the
     * 5-state model the backend derives the status from history; the app
     * only advances it locally for the safe high-water-mark outcomes
     * (INTERESTED/SCHEDULED/SOLD) via a separate [setStatus] call.
     */
    @Query(
        """
        UPDATE clients
        SET callAttempts = callAttempts + 1,
            lastCalledAt = :lastCalledAt,
            lastOutcome = :lastOutcome,
            updatedAt = :now
        WHERE id = :clientId
        """,
    )
    suspend fun applyInteractionUpdate(
        clientId: String,
        lastCalledAt: Instant,
        lastOutcome: CallOutcome,
        now: Instant,
    )

    /**
     * Refine the outcome of a call already applied locally (PostCall
     * save, when the agent confirms or changes the placeholder outcome).
     * Does NOT touch `callAttempts` / `lastCalledAt`, nor the status.
     */
    @Query(
        """
        UPDATE clients
        SET lastOutcome = :outcome,
            updatedAt = :now
        WHERE id = :clientId
        """,
    )
    suspend fun refineOutcome(
        clientId: String,
        outcome: CallOutcome,
        now: Instant,
    )

    @Query("UPDATE clients SET lastNote = :note, updatedAt = :now WHERE id = :clientId")
    suspend fun updateLastNote(clientId: String, note: String, now: Instant)

    @Query("UPDATE clients SET status = :status, updatedAt = :now WHERE id = :clientId")
    suspend fun setStatus(clientId: String, status: ClientStatus, now: Instant)

    /**
     * Set the removal reason alongside a move to [ClientStatus.REMOVED].
     * Caller is responsible for also calling [setStatus]; kept separate
     * so the two writes share one transaction at the repository layer.
     */
    @Query("UPDATE clients SET removalReason = :reason, updatedAt = :now WHERE id = :clientId")
    suspend fun setRemovalReason(
        clientId: String,
        reason: RemovalReason?,
        now: Instant,
    )
}
