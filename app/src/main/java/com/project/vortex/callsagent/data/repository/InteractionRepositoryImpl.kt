package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.db.InteractionDao
import com.project.vortex.callsagent.data.local.entity.InteractionEntity
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.domain.model.Interaction
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InteractionRepositoryImpl @Inject constructor(
    private val dao: InteractionDao,
) : InteractionRepository {

    override suspend fun save(interaction: Interaction) = withContext(Dispatchers.IO) {
        dao.insert(
            InteractionEntity(
                mobileSyncId = interaction.mobileSyncId,
                clientId = interaction.clientId,
                callStartedAt = interaction.callStartedAt,
                callEndedAt = interaction.callEndedAt,
                durationSeconds = interaction.durationSeconds,
                outcome = interaction.outcome,
                disconnectCause = interaction.disconnectCause,
                deviceCreatedAt = interaction.deviceCreatedAt,
                syncStatus = interaction.syncStatus,
            ),
        )
    }

    override suspend fun pendingSync(): List<Interaction> = withContext(Dispatchers.IO) {
        dao.findBySyncStatus(SyncStatus.PENDING).map { it.toDomain() }
    }

    override suspend fun markSynced(mobileSyncIds: List<String>) = withContext(Dispatchers.IO) {
        if (mobileSyncIds.isNotEmpty()) {
            dao.markSyncStatus(mobileSyncIds, SyncStatus.SYNCED)
        }
    }

    override suspend fun countPending(): Int = withContext(Dispatchers.IO) {
        dao.countBySyncStatus(SyncStatus.PENDING)
    }
}
