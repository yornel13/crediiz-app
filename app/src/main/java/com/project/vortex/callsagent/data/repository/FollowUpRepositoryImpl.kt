package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.db.FollowUpDao
import com.project.vortex.callsagent.data.local.entity.FollowUpEntity
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.data.mapper.toEntity
import com.project.vortex.callsagent.data.remote.api.FollowUpsApi
import com.project.vortex.callsagent.domain.model.FollowUp
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FollowUpRepositoryImpl @Inject constructor(
    private val api: FollowUpsApi,
    private val dao: FollowUpDao,
) : FollowUpRepository {

    override suspend fun save(followUp: FollowUp) = withContext(Dispatchers.IO) {
        dao.insert(
            FollowUpEntity(
                mobileSyncId = followUp.mobileSyncId,
                clientId = followUp.clientId,
                interactionMobileSyncId = followUp.interactionMobileSyncId,
                scheduledAt = followUp.scheduledAt,
                reason = followUp.reason,
                status = followUp.status,
                completedAt = followUp.completedAt,
                cancelledAt = null,
                cancelReason = null,
                deviceCreatedAt = followUp.deviceCreatedAt,
                syncStatus = followUp.syncStatus,
                completionSyncStatus = followUp.completionSyncStatus,
                clientName = followUp.clientName,
                clientPhone = followUp.clientPhone,
            ),
        )
    }

    override fun observeAgenda(from: Instant): Flow<List<FollowUp>> =
        dao.observePending(FollowUpStatus.PENDING, from)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun refreshAgenda(from: String?, to: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val envelope = api.getAgenda(from, to)
                val entities = envelope.data.map { it.toEntity() }
                dao.replaceAgenda(entities)
            }
        }

    override suspend fun markCompletedLocally(mobileSyncId: String, completedAt: Instant) =
        withContext(Dispatchers.IO) {
            dao.markCompletedLocally(mobileSyncId, completedAt)
        }

    override suspend fun pendingCreationSync(): List<FollowUp> = withContext(Dispatchers.IO) {
        dao.findBySyncStatus(SyncStatus.PENDING).map { it.toDomain() }
    }

    override suspend fun pendingCompletionSync(): List<FollowUp> = withContext(Dispatchers.IO) {
        dao.findPendingCompletions(SyncStatus.PENDING).map { it.toDomain() }
    }

    override suspend fun markCreationSynced(mobileSyncIds: List<String>) =
        withContext(Dispatchers.IO) {
            if (mobileSyncIds.isNotEmpty()) {
                dao.markSyncStatus(mobileSyncIds, SyncStatus.SYNCED)
            }
        }

    override suspend fun markCompletionSynced(mobileSyncIds: List<String>) =
        withContext(Dispatchers.IO) {
            if (mobileSyncIds.isNotEmpty()) {
                dao.markCompletionSyncStatus(mobileSyncIds, SyncStatus.SYNCED)
            }
        }

    override suspend fun countPending(): Int = withContext(Dispatchers.IO) {
        dao.countPending(SyncStatus.PENDING)
    }
}
