package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.MissedCallReason
import com.project.vortex.callsagent.data.local.db.MissedCallDao
import com.project.vortex.callsagent.data.local.entity.MissedCallEntity
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.domain.model.MissedCall
import com.project.vortex.callsagent.domain.repository.MissedCallRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MissedCallRepositoryImpl @Inject constructor(
    private val dao: MissedCallDao,
) : MissedCallRepository {

    override suspend fun log(
        phoneNumber: String,
        matchedClientId: String?,
        reason: MissedCallReason,
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            MissedCallEntity(
                id = UUID.randomUUID().toString(),
                phoneNumber = phoneNumber,
                matchedClientId = matchedClientId,
                reason = reason,
                occurredAt = Instant.now(),
            ),
        )
    }

    override fun observeUnacknowledged(): Flow<List<MissedCall>> =
        dao.observeUnacknowledged().map { list -> list.map { it.toDomain() } }

    override fun observeUnacknowledgedCount(): Flow<Int> =
        dao.observeUnacknowledgedCount()

    override suspend fun markAcknowledged(id: String) = withContext(Dispatchers.IO) {
        dao.markAcknowledged(id)
    }

    override suspend fun markAllAcknowledged() = withContext(Dispatchers.IO) {
        dao.markAllAcknowledged()
    }
}
