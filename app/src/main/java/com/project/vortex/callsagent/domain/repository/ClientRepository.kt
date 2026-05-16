package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.domain.error.ClientError
import com.project.vortex.callsagent.domain.model.AgentStatusChangeLocal
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface ClientRepository {

    /** Fetch assigned clients from the server and replace the local cache. */
    suspend fun refreshAssigned(status: ClientStatus = ClientStatus.PENDING): Result<Unit>

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

    /** Find a single client by id. */
    suspend fun findById(id: String): Client?

    /**
     * Match by the last 8 digits of the phone number (handles country-code
     * prefix variations). Returns null if no assigned client matches —
     * used for incoming-call caller identification.
     */
    suspend fun findByPhone(phone: String): Client?

    /**
     * Optimistically apply an interaction's side-effects on the local client row,
     * mirroring what the backend will do on sync.
     */
    suspend fun applyInteractionLocally(
        clientId: String,
        outcome: CallOutcome,
        callStartedAt: Instant,
    )

    /** Update the denormalized lastNote field after saving a note locally. */
    suspend fun updateLastNoteLocally(clientId: String, note: String)

    /**
     * Promote/demote the thermometer of an INTERESTED client.
     *
     * Optimistic write to Room first, then PATCH to the server. If the
     * network call fails the local value is rolled back to [previous]
     * so the UI doesn't lie, and the typed [ClientError] is
     * returned so the caller can surface a snackbar.
     *
     * **Silent rollback is always a bug.** The caller MUST handle
     * [OperationResult.Failure] explicitly.
     */
    suspend fun updateInterestLevel(
        clientId: String,
        level: InterestLevel,
        previous: InterestLevel?,
    ): OperationResult<Unit, ClientError>

    /**
     * Move an assigned client to [toStatus] without placing a call —
     * the agent flow for out-of-band signals (WhatsApp opt-out, etc.).
     * Optimistic local write, then `POST /clients/:id/agent-status-change`.
     * Rolls back to [previousStatus] / [previousLevel] on failure.
     *
     * [level] is only honored when [toStatus] is INTERESTED.
     *
     * On success, persists a local-only `AgentStatusChangeLocal` row
     * so the action surfaces in the Recientes feed for 24 h even
     * though no call took place.
     *
     * **Silent rollback is always a bug.** The caller MUST handle
     * [OperationResult.Failure] explicitly (snackbar, retry, etc.).
     */
    suspend fun agentStatusChange(
        clientId: String,
        toStatus: ClientStatus,
        previousStatus: ClientStatus,
        previousLevel: InterestLevel?,
        reason: String? = null,
        level: InterestLevel? = null,
    ): OperationResult<Unit, ClientError>

    /**
     * Observe agent-initiated status changes inside the given window
     * (24 h for the Recientes feed). Local-only — does NOT reflect
     * status changes done from the admin panel.
     */
    fun observeRecentAgentStatusChanges(since: Instant): Flow<List<AgentStatusChangeLocal>>
}
