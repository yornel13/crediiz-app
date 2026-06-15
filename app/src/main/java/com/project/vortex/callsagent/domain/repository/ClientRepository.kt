package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.QuotationValidation
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.domain.error.ClientError
import com.project.vortex.callsagent.domain.model.AgentStatusChangeLocal
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.ClientStatusChange
import com.project.vortex.callsagent.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ClientRepository {

    /**
     * Fetch the agent's WHOLE assigned set from the server (any status,
     * `GET /clients/assigned` with no status filter) and mirror it into the
     * local cache: upsert all, drop any local client no longer assigned.
     *
     * The local DB therefore holds every assigned client regardless of status —
     * a client only leaves the device when it is unassigned or hard-deleted in
     * the core, not when it turns terminal (REMOVED/CONVERTED). The status-
     * scoped lists ([observeAssigned], the Pendientes sub-feeds) filter by
     * status locally, so terminal clients never leak into the active queues;
     * they only surface in "Recientes" within its time window.
     */
    suspend fun refreshAssigned(): Result<Unit>

    /** Observe assigned clients by status from the local DB. */
    fun observeAssigned(status: ClientStatus): Flow<List<Client>>

    /** Local search (name/phone substring). */
    fun searchAssigned(status: ClientStatus, query: String): Flow<List<Client>>

    /**
     * Pendientes — "Sin llamar" sub-feed: assigned PENDING clients
     * the agent has never called yet (`lastCalledAt IS NULL`).
     */
    fun observePendingNeverCalled(): Flow<List<Client>>

    /** Local search over the "Sin llamar" sub-feed. */
    fun searchPendingNeverCalled(query: String): Flow<List<Client>>

    /**
     * Pendientes — "Para reintentar" sub-feed: assigned PENDING
     * clients with at least one call attempt (`lastCalledAt
     * IS NOT NULL`). Sorted oldest call first so the most "ready
     * to retry" leads bubble up.
     */
    fun observePendingForRetry(): Flow<List<Client>>

    /** Local search over the "Para reintentar" sub-feed. */
    fun searchPendingForRetry(query: String): Flow<List<Client>>

    /**
     * Observe clients called inside the given time window, regardless of
     * status/outcome. Drives the "Recientes" view (24 h window for v1.0).
     */
    fun observeRecent(since: Instant): Flow<List<Client>>

    /** Local search over the "Recientes" feed. */
    fun searchRecent(since: Instant, query: String): Flow<List<Client>>

    /**
     * Observe INTERESTED clients without an active future follow-up.
     * Drives the "Sin agendar" section in Agenda.
     */
    fun observeUnscheduledInterested(now: Instant): Flow<List<Client>>

    /** Find a single client by id (one-shot snapshot). */
    suspend fun findById(id: String): Client?

    /**
     * Observe a single client reactively. The detail screen uses this so
     * the status pill converges automatically after an agent-status-change
     * or a sync pull, instead of showing a stale snapshot.
     */
    fun observeClient(id: String): Flow<Client?>

    /**
     * Match by the last 8 digits of the phone number (handles country-code
     * prefix variations). Returns null if no assigned client matches —
     * used for incoming-call caller identification.
     */
    suspend fun findByPhone(phone: String): Client?

    /**
     * Apply an interaction's local side-effects — called ONCE per call at
     * call-end with the placeholder outcome (from the SIP-engine insight).
     * Increments `callAttempts`, sets `lastCalledAt` and `lastOutcome`.
     *
     * **Does NOT decide the status.** In the 5-state model the backend
     * derives it from history; the app only advances it locally for the
     * safe high-water-mark outcomes (INTERESTED→INTERESTED,
     * SCHEDULED→CITED, SOLD→CONVERTED). Every other outcome leaves the
     * status untouched and waits for the post-sync refresh.
     */
    suspend fun applyInteractionLocally(
        clientId: String,
        outcome: CallOutcome,
        callStartedAt: Instant,
    )

    /**
     * Refine the outcome on a client row whose call attempt was already
     * applied via [applyInteractionLocally]. Called from PostCall save
     * when the agent confirms or changes the placeholder outcome.
     *
     * **Does NOT increment `callAttempts`** nor touch `lastCalledAt`.
     * Updates `lastOutcome`; advances the status only for the safe
     * high-water-mark outcomes (never downgrades locally).
     */
    suspend fun refineInteractionOutcome(
        clientId: String,
        outcome: CallOutcome,
    )

    /** Update the denormalized lastNote field after saving a note locally. */
    suspend fun updateLastNoteLocally(clientId: String, note: String)

    /**
     * Move an assigned client to [toStatus] without placing a call —
     * the agent flow for out-of-band signals (WhatsApp opt-out, etc.).
     *
     * **No optimistic write.** Posts to `agent-status-change`, then writes
     * the **server-returned client** to Room (the source of truth) and
     * returns the resulting [ClientStatus]. A blocked transition (high-
     * water-mark / quorum) is a 200 no-op: the returned status equals the
     * current one — the caller MUST compare it against [toStatus] and tell
     * the agent when nothing changed (e.g. "falta confirmación de otro
     * agente"). Not asking → the agent thinks it worked when it didn't.
     *
     * [removalReason] is **required** when [toStatus] is REMOVED.
     *
     * The caller MUST handle [OperationResult.Failure] explicitly.
     */
    suspend fun agentStatusChange(
        clientId: String,
        toStatus: ClientStatus,
        removalReason: RemovalReason? = null,
        reason: String? = null,
    ): OperationResult<ClientStatus, ClientError>

    /**
     * Observe agent-initiated status changes inside the given window
     * (24 h for the Recientes feed). Local-only — does NOT reflect
     * status changes done from the admin panel.
     */
    fun observeRecentAgentStatusChanges(since: Instant): Flow<List<AgentStatusChangeLocal>>

    /**
     * Fetch the client's canonical status history from the backend
     * (`GET /clients/:id/status-history`) — changes by any actor, newest
     * first. Returns a [Result] so the caller can degrade gracefully (e.g.
     * 403 if the client is no longer assigned) without breaking the timeline.
     */
    suspend fun fetchStatusHistory(
        clientId: String,
        limit: Int = 50,
    ): Result<List<ClientStatusChange>>

    /**
     * Upsert the client's quotation (`PUT /clients/:id/quotation`,
     * idempotent full replace). Writes the server-returned client to Room so
     * the detail reflects it. The caller MUST handle [OperationResult.Failure]
     * (403 if unassigned, 400 invalid, etc.).
     */
    suspend fun upsertQuotation(
        clientId: String,
        validation: QuotationValidation,
        bank: String,
        quotedAmount: Double,
        biweeklyPayment: Double,
        notes: String?,
    ): OperationResult<Unit, ClientError>
}
