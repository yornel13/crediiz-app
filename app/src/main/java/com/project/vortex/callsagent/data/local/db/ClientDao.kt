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
     * Pendientes feed — clients the LOGGED-IN agent has **never** called.
     *
     * "New" is derived: `PENDING` with `agentCallAttempts == 0` — i.e. THIS
     * agent has not dialed it yet. The split uses the per-agent count, NOT the
     * team-wide `callAttempts`: a client another assigned agent already called
     * still appears here until this agent personally dials it. The first
     * no-contact outcome keeps the client `PENDING` (the backend never promotes
     * to a separate state) but bumps `agentCallAttempts`, moving it to the
     * [observePendingForRetry] feed. See [ClientEntity.agentCallAttempts].
     */
    @Query(
        """
        SELECT * FROM clients
        WHERE status = 'PENDING' AND agentCallAttempts = 0
        ORDER BY queueOrder ASC
        """,
    )
    fun observePendingNeverCalled(): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT * FROM clients
        WHERE status = 'PENDING' AND agentCallAttempts = 0
          AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%')
        ORDER BY queueOrder ASC
        """,
    )
    fun searchPendingNeverCalled(query: String): Flow<List<ClientEntity>>

    /**
     * "Para reintentar" feed — `PENDING` clients the LOGGED-IN agent has
     * already called at least once without a closing outcome
     * (`agentCallAttempts > 0`). Per-agent on purpose: another agent's calls do
     * NOT move a client here for an agent who never dialed it. Sort by
     * `lastCalledAt` ascending so the oldest attempt floats to the top: those
     * are the most ready to dial again. NULL `lastCalledAt` sorts first thanks
     * to SQLite's NULL ordering. See [ClientEntity.agentCallAttempts].
     */
    @Query(
        """
        SELECT * FROM clients
        WHERE status = 'PENDING' AND agentCallAttempts > 0
        ORDER BY lastCalledAt ASC
        """,
    )
    fun observePendingForRetry(): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT * FROM clients
        WHERE status = 'PENDING' AND agentCallAttempts > 0
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
     * Full assignment mirror — the path Sync uses. The local table must equal
     * the agent's currently-assigned clients in ANY status, so [clients] is the
     * complete set from `GET /clients/assigned` (no status filter). Upserts all
     * of them and deletes any local row whose id is absent from the set: those
     * are clients that were **unassigned** or **hard-deleted** in the core.
     *
     * A client that merely turned terminal (REMOVED/CONVERTED) is still
     * assigned, so it stays — that's what keeps a just-removed client visible
     * in "Recientes" (and openable for a note) until it is truly detached,
     * instead of vanishing the instant its status flips. An empty [clients]
     * (no assignments) clears the table.
     */
    @Transaction
    suspend fun replaceAllAssigned(clients: List<ClientEntity>) {
        if (clients.isEmpty()) {
            deleteAll()
            return
        }
        // Floor each incoming `agentCallAttempts` with the value already on the
        // device. The server count is synthetic — it only reflects interactions
        // the backend has ALREADY received, so a just-placed call that is still
        // syncing comes back as 0 and would bounce the client back to "Sin
        // llamar". A pull can race ahead of the async push (pull-to-refresh,
        // reconnect, the Clients screen's init refresh), so we cannot trust the
        // server value to be monotonic in real time. The per-agent count only
        // ever GROWS for a given agent, so max(server, local) never yields a
        // wrong value — it just protects the optimistic bump from
        // [applyInteractionUpdate] until the push lands.
        val localAttempts = agentCallAttemptsByIds(clients.map { it.id })
            .associate { it.id to it.agentCallAttempts }
        val reconciled = clients.map { c ->
            val local = localAttempts[c.id] ?: 0
            if (local > c.agentCallAttempts) c.copy(agentCallAttempts = local) else c
        }
        upsert(reconciled)
        deleteNotIn(reconciled.map { it.id })
    }

    /**
     * Per-agent attempt counts already on the device, keyed by id — the local
     * floor applied in [replaceAllAssigned] so a server snapshot that predates
     * the latest call's sync can't reset the queue split.
     */
    @Query("SELECT id, agentCallAttempts FROM clients WHERE id IN (:ids)")
    suspend fun agentCallAttemptsByIds(ids: List<String>): List<ClientAttemptCount>

    @Query("DELETE FROM clients WHERE id NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    /**
     * Wipe the whole table — used by logout / fresh-install paths only.
     * Do NOT call from sync; use [replaceAllAssigned] there.
     */
    @Transaction
    suspend fun replaceAll(clients: List<ClientEntity>) {
        deleteAll()
        upsert(clients)
    }

    @Query("DELETE FROM clients")
    suspend fun deleteAll()

    /**
     * Local bookkeeping after a call. Bumps the attempt counters and
     * records the outcome — but **does NOT decide the status**. In the
     * 5-state model the backend derives the status from history; the app
     * only advances it locally for the safe high-water-mark outcomes
     * (INTERESTED/SCHEDULED/SOLD) via a separate [setStatus] call.
     *
     * Both counters are bumped because the call was placed BY THIS agent:
     * `callAttempts` (team-wide) and `agentCallAttempts` (per-agent). The
     * per-agent bump is the optimistic local move from "Sin llamar" to "Para
     * reintentar" before the next sync; the next `replaceAllAssigned` overwrites
     * both with the server's authoritative values (also `>= 1`), so they stay
     * consistent.
     */
    @Query(
        """
        UPDATE clients
        SET callAttempts = callAttempts + 1,
            agentCallAttempts = agentCallAttempts + 1,
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

/** Projection for [ClientDao.agentCallAttemptsByIds] — id + per-agent attempt count. */
data class ClientAttemptCount(
    val id: String,
    val agentCallAttempts: Int,
)
