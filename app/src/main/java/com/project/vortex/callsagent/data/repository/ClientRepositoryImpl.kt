package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.data.local.db.ClientDao
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.data.mapper.toEntity
import com.project.vortex.callsagent.data.remote.api.ClientsApi
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.repository.ClientRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val OUTCOME_TO_STATUS: Map<CallOutcome, ClientStatus> = mapOf(
    CallOutcome.INTERESTED to ClientStatus.INTERESTED,
    CallOutcome.NOT_INTERESTED to ClientStatus.REJECTED,
    CallOutcome.NO_ANSWER to ClientStatus.PENDING,
    CallOutcome.BUSY to ClientStatus.PENDING,
    CallOutcome.INVALID_NUMBER to ClientStatus.INVALID_NUMBER,
)

@Singleton
class ClientRepositoryImpl @Inject constructor(
    private val api: ClientsApi,
    private val dao: ClientDao,
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

    override fun observeRecent(since: Instant): Flow<List<Client>> =
        dao.observeRecent(since).map { list -> list.map { it.toDomain() } }

    override fun searchRecent(since: Instant, query: String): Flow<List<Client>> =
        dao.searchRecent(since, query).map { list -> list.map { it.toDomain() } }

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
            newStatus = OUTCOME_TO_STATUS.getValue(outcome),
            now = Instant.now(),
        )
    }

    override suspend fun updateLastNoteLocally(clientId: String, note: String) =
        withContext(Dispatchers.IO) {
            dao.updateLastNote(clientId, note, Instant.now())
        }
}
