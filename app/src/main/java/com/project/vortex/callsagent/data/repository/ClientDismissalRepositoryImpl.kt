package com.project.vortex.callsagent.data.repository

import android.util.Log
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.DismissalReasonCode
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.db.ClientDao
import com.project.vortex.callsagent.data.local.db.ClientDismissalDao
import com.project.vortex.callsagent.data.local.entity.ClientDismissalEntity
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.model.ClientDismissal
import com.project.vortex.callsagent.domain.repository.ClientDismissalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ClientDismissalRepo"

@Singleton
class ClientDismissalRepositoryImpl @Inject constructor(
    private val dao: ClientDismissalDao,
    private val clientDao: ClientDao,
    private val syncScheduler: SyncScheduler,
) : ClientDismissalRepository {

    override suspend fun dismiss(
        clientId: String,
        reasonCode: DismissalReasonCode?,
        freeFormReason: String?,
    ) = withContext(Dispatchers.IO) {
        val client = clientDao.findById(clientId)
        if (client == null) {
            Log.w(TAG, "dismiss($clientId): client not found locally — skipping")
            return@withContext
        }
        val now = Instant.now()
        val event = ClientDismissalEntity(
            mobileSyncId = UUID.randomUUID().toString(),
            clientId = clientId,
            previousStatus = client.status,
            reason = freeFormReason?.takeIf { it.isNotBlank() },
            reasonCode = reasonCode?.name,
            dismissedAt = now,
            undone = false,
            undoneAt = null,
            deviceCreatedAt = now,
            syncStatus = SyncStatus.PENDING,
        )
        dao.upsert(event)
        clientDao.setStatus(clientId, ClientStatus.DISMISSED, now)
        syncScheduler.triggerImmediateSync()
    }

    override suspend fun undo(clientId: String): Boolean = withContext(Dispatchers.IO) {
        val active = dao.findActiveForClient(clientId) ?: return@withContext false
        val now = Instant.now()
        dao.upsert(
            active.copy(
                undone = true,
                undoneAt = now,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        clientDao.setStatus(clientId, active.previousStatus, now)
        syncScheduler.triggerImmediateSync()
        true
    }

    override suspend fun findActiveForClient(clientId: String): ClientDismissal? =
        withContext(Dispatchers.IO) {
            dao.findActiveForClient(clientId)?.toDomain()
        }

    override fun observeActiveSince(since: Instant): Flow<List<ClientDismissal>> =
        dao.observeActiveSince(since).map { list -> list.map { it.toDomain() } }

    override fun observePendingCount(): Flow<Int> =
        dao.observeCountByStatus(SyncStatus.PENDING)
}
