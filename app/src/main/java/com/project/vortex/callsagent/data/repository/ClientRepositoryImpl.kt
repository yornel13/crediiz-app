package com.project.vortex.callsagent.data.repository

import android.util.Log
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.InterestLevel
import com.project.vortex.callsagent.data.local.db.ClientDao
import com.project.vortex.callsagent.data.local.db.LocalAgentStatusChangeDao
import com.project.vortex.callsagent.data.local.entity.LocalAgentStatusChangeEntity
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.data.mapper.toEntity
import com.project.vortex.callsagent.data.error.ErrorMapper
import com.project.vortex.callsagent.data.remote.api.ClientsApi
import com.project.vortex.callsagent.data.remote.dto.AgentStatusChangeDto
import com.project.vortex.callsagent.data.remote.dto.UpdateInterestLevelDto
import com.project.vortex.callsagent.domain.error.ClientError
import com.project.vortex.callsagent.domain.model.AgentStatusChangeLocal
import com.project.vortex.callsagent.domain.model.Client
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
 * Optimistic local status guess applied right after the call ends, so the
 * UI moves the client out of "Pendientes — Untouched" before the sync
 * round-trip resolves. This is **NOT** authoritative — the backend is the
 * source of truth and the post-sync refresh overwrites with the real value.
 *
 * In particular: `WRONG_NUMBER` may end up as either `IN_PROGRESS` or
 * `UNREACHABLE` depending on the server-side `wrongNumberCount` threshold,
 * which the mobile cannot see. We guess `IN_PROGRESS` (the common case)
 * and let the refresh correct to `UNREACHABLE` on the 3rd strike.
 */
private val OPTIMISTIC_OUTCOME_TO_STATUS: Map<CallOutcome, ClientStatus> = mapOf(
    CallOutcome.ANSWERED_INTERESTED to ClientStatus.INTERESTED,
    CallOutcome.ANSWERED_NOT_INTERESTED to ClientStatus.REJECTED,
    CallOutcome.ANSWERED_OPT_OUT to ClientStatus.DO_NOT_CALL,
    CallOutcome.ANSWERED_SOLD to ClientStatus.CONVERTED,
    CallOutcome.NO_ANSWER to ClientStatus.IN_PROGRESS,
    CallOutcome.BUSY to ClientStatus.IN_PROGRESS,
    CallOutcome.WRONG_NUMBER to ClientStatus.IN_PROGRESS,
)

/**
 * Default [ClientRepository] backed by Room + Retrofit.
 *
 * **Project invariant — read before adding new methods:**
 * Any operation that performs a local optimistic write and rolls back
 * on HTTP failure MUST return an [OperationResult] with a typed error
 * (see [updateInterestLevel], [agentStatusChange]). Silent rollback
 * is always a bug — the agent must see a snackbar explaining what
 * happened (R2 in `HOW_IT_WORKS_ALIGNMENT.md`).
 */
@Singleton
class ClientRepositoryImpl @Inject constructor(
    private val api: ClientsApi,
    private val dao: ClientDao,
    private val statusChangeDao: LocalAgentStatusChangeDao,
    private val errorMapper: ErrorMapper,
) : ClientRepository {

    override suspend fun refreshAssigned(status: ClientStatus): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val envelope = api.getAssigned(status.name)
                val entities = envelope.data.map { it.toEntity() }
                // Status-scoped replace — calling this for PENDING then
                // INTERESTED used to wipe the PENDING set with `replaceAll`
                // (KI-02). The scoped variant only touches rows of the
                // status we just fetched.
                dao.replaceAllByStatus(status, entities)
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

    override suspend fun findByPhone(phone: String): Client? = withContext(Dispatchers.IO) {
        dao.findByNormalizedPhone(phone)?.toDomain()
    }

    override suspend fun applyInteractionLocally(
        clientId: String,
        outcome: CallOutcome,
        callStartedAt: Instant,
    ) = withContext(Dispatchers.IO) {
        dao.applyInteractionUpdate(
            clientId = clientId,
            lastCalledAt = callStartedAt,
            lastOutcome = outcome,
            newStatus = OPTIMISTIC_OUTCOME_TO_STATUS.getValue(outcome),
            now = Instant.now(),
        )
    }

    override suspend fun refineInteractionOutcome(
        clientId: String,
        outcome: CallOutcome,
    ) = withContext(Dispatchers.IO) {
        dao.refineOutcomeAndStatus(
            clientId = clientId,
            outcome = outcome,
            newStatus = OPTIMISTIC_OUTCOME_TO_STATUS.getValue(outcome),
            now = Instant.now(),
        )
    }

    override suspend fun updateLastNoteLocally(clientId: String, note: String) =
        withContext(Dispatchers.IO) {
            dao.updateLastNote(clientId, note, Instant.now())
        }

    override suspend fun updateInterestLevel(
        clientId: String,
        level: InterestLevel,
        previous: InterestLevel?,
    ): OperationResult<Unit, ClientError> = withContext(Dispatchers.IO) {
        // 1. Optimistic write — the UI reflects the new level instantly.
        dao.setInterestLevel(clientId, level, Instant.now())
        // 2. Server PATCH. Server returns 400 if the client isn't
        //    INTERESTED; we map every failure to a typed error so the
        //    ViewModel can surface a snackbar. Silent rollback is a bug.
        try {
            api.updateInterestLevel(clientId, UpdateInterestLevelDto(level = level.name))
            OperationResult.Success(Unit)
        } catch (err: Throwable) {
            Log.w(TAG, "updateInterestLevel($clientId, $level) failed, rolling back", err)
            dao.setInterestLevel(clientId, previous, Instant.now())
            OperationResult.Failure(errorMapper.toClientError(err))
        }
    }

    override suspend fun agentStatusChange(
        clientId: String,
        toStatus: ClientStatus,
        previousStatus: ClientStatus,
        previousLevel: InterestLevel?,
        reason: String?,
        level: InterestLevel?,
    ): OperationResult<Unit, ClientError> = withContext(Dispatchers.IO) {
        val now = Instant.now()
        // Optimistic: status + (maybe) level. Backend resets level to
        // null on non-INTERESTED targets; we mirror that here so the
        // chip disappears immediately when moving to e.g. DO_NOT_CALL.
        dao.setStatus(clientId, toStatus, now)
        dao.setInterestLevel(
            clientId,
            if (toStatus == ClientStatus.INTERESTED) level else null,
            now,
        )
        try {
            api.agentStatusChange(
                clientId,
                AgentStatusChangeDto(
                    toStatus = toStatus.name,
                    reason = reason?.takeIf { it.isNotBlank() },
                    interestLevel = level?.name?.takeIf { toStatus == ClientStatus.INTERESTED },
                ),
            )
            // Persist a local-only record of the action so the
            // Recientes feed can surface "this client moved without a
            // call" inside the 24 h window. Survives across app
            // restarts; only purged by the destructive migration in
            // dev or by `deleteAll` on logout.
            statusChangeDao.insert(
                LocalAgentStatusChangeEntity(
                    id = UUID.randomUUID().toString(),
                    clientId = clientId,
                    fromStatus = previousStatus,
                    toStatus = toStatus,
                    interestLevel = if (toStatus == ClientStatus.INTERESTED) level else null,
                    reason = reason?.takeIf { it.isNotBlank() },
                    timestamp = Instant.now(),
                ),
            )
            OperationResult.Success(Unit)
        } catch (err: Throwable) {
            Log.w(
                TAG,
                "agentStatusChange($clientId, $toStatus) failed, rolling back",
                err,
            )
            dao.setStatus(clientId, previousStatus, Instant.now())
            dao.setInterestLevel(clientId, previousLevel, Instant.now())
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
                    interestLevel = e.interestLevel,
                    reason = e.reason,
                    timestamp = e.timestamp,
                )
            }
        }

    companion object {
        private const val TAG = "ClientRepository"
    }
}
