package com.project.vortex.callsagent.data.repository

import android.util.Log
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.data.local.db.ClientDao
import com.project.vortex.callsagent.data.local.db.LocalAgentStatusChangeDao
import com.project.vortex.callsagent.data.local.entity.LocalAgentStatusChangeEntity
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.data.mapper.toEntity
import com.project.vortex.callsagent.data.error.ErrorMapper
import com.project.vortex.callsagent.data.remote.api.ClientsApi
import com.project.vortex.callsagent.data.remote.dto.AgentStatusChangeDto
import com.project.vortex.callsagent.data.remote.dto.UpsertQuotationDto
import com.project.vortex.callsagent.domain.error.ClientError
import com.project.vortex.callsagent.domain.model.AgentStatusChangeLocal
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.ClientStatusChange
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.result.OperationResult
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only outcomes the app may advance a client to **locally**. They are
 * all monotonic high-water-mark advances (the agent can only move up), so
 * applying them optimistically can never contradict the backend's
 * precedence rules. Every other outcome (no-contact, not-interested, hard
 * reasons) is left to the backend: thresholds and quorum mean a single
 * mobile guess would be wrong, so we wait for the post-sync refresh.
 */
private val SAFE_ADVANCE_OUTCOME_TO_STATUS: Map<CallOutcome, ClientStatus> = mapOf(
    CallOutcome.INTERESTED to ClientStatus.INTERESTED,
    CallOutcome.SCHEDULED to ClientStatus.CITED,
    CallOutcome.SOLD to ClientStatus.CONVERTED,
)

/**
 * Default [ClientRepository] backed by Room + Retrofit.
 *
 * **Project invariant — read before adding new methods:**
 * In the 5-state model the backend is the source of truth for status.
 * The app never decides a status from an outcome except for the safe
 * high-water-mark advances in [SAFE_ADVANCE_OUTCOME_TO_STATUS]. Writes
 * that depend on a server decision (see [agentStatusChange]) reconcile
 * against the returned client instead of guessing.
 */
@Singleton
class ClientRepositoryImpl @Inject constructor(
    private val api: ClientsApi,
    private val dao: ClientDao,
    private val statusChangeDao: LocalAgentStatusChangeDao,
    private val errorMapper: ErrorMapper,
) : ClientRepository {

    override suspend fun refreshAssigned(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // No status filter → the server returns EVERY client assigned to
                // this agent, in any status. Mirror the full set locally so a
                // client stays on the device until it is unassigned/hard-deleted,
                // not when it merely turns terminal (see [ClientDao.replaceAllAssigned]).
                // One round-trip replaces the old per-status fetches.
                val envelope = api.getAssigned(null)
                val entities = envelope.data.map { it.toEntity() }
                dao.replaceAllAssigned(entities)
            }
        }

    override fun observeAssigned(status: ClientStatus): Flow<List<Client>> =
        dao.observeByStatus(status).map { list -> list.map { it.toDomain() } }

    override fun searchAssigned(status: ClientStatus, query: String): Flow<List<Client>> =
        dao.searchByStatus(status, query).map { list -> list.map { it.toDomain() } }

    override fun observePendingNeverCalled(): Flow<List<Client>> =
        dao.observePendingNeverCalled().map { list -> list.map { it.toDomain() } }

    override fun searchPendingNeverCalled(query: String): Flow<List<Client>> =
        dao.searchPendingNeverCalled(query).map { list -> list.map { it.toDomain() } }

    override fun observePendingForRetry(): Flow<List<Client>> =
        dao.observePendingForRetry().map { list -> list.map { it.toDomain() } }

    override fun searchPendingForRetry(query: String): Flow<List<Client>> =
        dao.searchPendingForRetry(query).map { list -> list.map { it.toDomain() } }

    override fun observeRecent(since: Instant): Flow<List<Client>> =
        dao.observeRecent(since).map { list -> list.map { it.toDomain() } }

    override fun searchRecent(since: Instant, query: String): Flow<List<Client>> =
        dao.searchRecent(since, query).map { list -> list.map { it.toDomain() } }

    override fun observeUnscheduledInterested(now: Instant): Flow<List<Client>> =
        dao.observeUnscheduledInterested(now).map { list -> list.map { it.toDomain() } }

    override suspend fun findById(id: String): Client? = withContext(Dispatchers.IO) {
        dao.findById(id)?.toDomain()
    }

    override fun observeClient(id: String): Flow<Client?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun findByPhone(phone: String): Client? = withContext(Dispatchers.IO) {
        dao.findByNormalizedPhone(phone)?.toDomain()
    }

    override suspend fun applyInteractionLocally(
        clientId: String,
        outcome: CallOutcome,
        callStartedAt: Instant,
    ) = withContext(Dispatchers.IO) {
        val now = Instant.now()
        dao.applyInteractionUpdate(
            clientId = clientId,
            lastCalledAt = callStartedAt,
            lastOutcome = outcome,
            now = now,
        )
        val advance = SAFE_ADVANCE_OUTCOME_TO_STATUS[outcome]
        if (advance != null) dao.setStatus(clientId, advance, now)
    }

    override suspend fun refineInteractionOutcome(
        clientId: String,
        outcome: CallOutcome,
    ) = withContext(Dispatchers.IO) {
        val now = Instant.now()
        dao.refineOutcome(clientId = clientId, outcome = outcome, now = now)
        val advance = SAFE_ADVANCE_OUTCOME_TO_STATUS[outcome]
        if (advance != null) dao.setStatus(clientId, advance, now)
    }

    override suspend fun updateLastNoteLocally(clientId: String, note: String) =
        withContext(Dispatchers.IO) {
            dao.updateLastNote(clientId, note, Instant.now())
        }

    override suspend fun agentStatusChange(
        clientId: String,
        toStatus: ClientStatus,
        removalReason: RemovalReason?,
        reason: String?,
    ): OperationResult<ClientStatus, ClientError> = withContext(Dispatchers.IO) {
        val existing = dao.findById(clientId)
        val fromStatus = existing?.status
        try {
            // No optimistic write: the result may be a 200 no-op (blocked
            // transition / quorum). We apply the server-returned client as
            // the single source of truth and report the resulting status.
            val response = api.agentStatusChange(
                clientId,
                AgentStatusChangeDto(
                    toStatus = toStatus.name,
                    removalReason = removalReason?.name?.takeIf { toStatus == ClientStatus.REMOVED },
                    reason = reason?.takeIf { it.isNotBlank() },
                ),
            )
            // This endpoint returns the raw Client doc, which has no synthetic
            // `agentCallAttempts` (only `findAssigned` computes it). Preserve the
            // locally-known per-agent count so a REPLACE upsert doesn't reset it
            // to 0 and bounce a still-PENDING client back to "Sin llamar".
            // Self-heals on the next refreshAssigned, but this avoids the flicker.
            val entity = response.data.toEntity().copy(
                agentCallAttempts = existing?.agentCallAttempts ?: 0,
            )
            dao.upsert(listOf(entity))

            // Record the action for the Recientes feed only when it
            // actually moved the client (a no-op shouldn't show up there).
            if (fromStatus != null && entity.status != fromStatus) {
                statusChangeDao.insert(
                    LocalAgentStatusChangeEntity(
                        id = UUID.randomUUID().toString(),
                        clientId = clientId,
                        fromStatus = fromStatus,
                        toStatus = entity.status,
                        removalReason = entity.removalReason,
                        reason = reason?.takeIf { it.isNotBlank() },
                        timestamp = Instant.now(),
                    ),
                )
            }
            OperationResult.Success(entity.status)
        } catch (err: Throwable) {
            Log.w(TAG, "agentStatusChange($clientId, $toStatus) failed", err)
            OperationResult.Failure(errorMapper.toClientError(err))
        }
    }

    override suspend fun fetchStatusHistory(
        clientId: String,
        limit: Int,
    ): Result<List<ClientStatusChange>> = withContext(Dispatchers.IO) {
        runCatching {
            api.getStatusHistory(clientId, page = 1, limit = limit)
                .data.data.mapNotNull { it.toDomain() }
        }
    }

    override suspend fun upsertQuotation(
        clientId: String,
        bank: String,
        quotedAmount: Double,
        biweeklyPayment: Double,
        notes: String?,
    ): OperationResult<Unit, ClientError> = withContext(Dispatchers.IO) {
        try {
            val response = api.upsertQuotation(
                clientId,
                UpsertQuotationDto(
                    bank = bank,
                    quotedAmount = quotedAmount,
                    biweeklyPayment = biweeklyPayment,
                    notes = notes?.takeIf { it.isNotBlank() },
                ),
            )
            // The PUT returns the full client with the quotation embedded —
            // write it as the source of truth so the detail reflects it.
            // Preserve the synthetic per-agent `agentCallAttempts` (absent from
            // this response) so a REPLACE upsert doesn't reset the queue split.
            val existingAttempts = dao.findById(clientId)?.agentCallAttempts ?: 0
            dao.upsert(
                listOf(response.data.toEntity().copy(agentCallAttempts = existingAttempts)),
            )
            OperationResult.Success(Unit)
        } catch (err: Throwable) {
            Log.w(TAG, "upsertQuotation($clientId) failed", err)
            OperationResult.Failure(errorMapper.toClientError(err))
        }
    }

    override fun observeRecentAgentStatusChanges(
        since: Instant,
    ): Flow<List<AgentStatusChangeLocal>> =
        statusChangeDao.observeRecent(since).map { list ->
            list.map { e ->
                AgentStatusChangeLocal(
                    id = e.id,
                    clientId = e.clientId,
                    fromStatus = e.fromStatus,
                    toStatus = e.toStatus,
                    removalReason = e.removalReason,
                    reason = e.reason,
                    timestamp = e.timestamp,
                )
            }
        }

    companion object {
        private const val TAG = "ClientRepository"
    }
}
