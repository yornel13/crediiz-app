package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.db.InteractionDao
import com.project.vortex.callsagent.data.local.entity.InteractionEntity
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.domain.model.Interaction
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InteractionRepositoryImpl @Inject constructor(
    private val dao: InteractionDao,
) : InteractionRepository {

    override suspend fun save(interaction: Interaction) = withContext(Dispatchers.IO) {
        // Preserve `confirmedByAgent` if the row already exists — Room's
        // REPLACE strategy would otherwise reset it to the default `false`
        // on a Post-Call re-save, defeating the orphan-recovery flag.
        val existingConfirmed = dao.findById(interaction.mobileSyncId)?.confirmedByAgent ?: false
        dao.insert(
            InteractionEntity(
                mobileSyncId = interaction.mobileSyncId,
                clientId = interaction.clientId,
                direction = interaction.direction,
                callStartedAt = interaction.callStartedAt,
                callEndedAt = interaction.callEndedAt,
                durationSeconds = interaction.durationSeconds,
                outcome = interaction.outcome,
                disconnectCause = interaction.disconnectCause,
                deviceCreatedAt = interaction.deviceCreatedAt,
                syncStatus = interaction.syncStatus,
                confirmedByAgent = existingConfirmed,
            ),
        )
    }

    override suspend fun findById(mobileSyncId: String): Interaction? =
        withContext(Dispatchers.IO) { dao.findById(mobileSyncId)?.toDomain() }

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

    override fun observePendingCount(): kotlinx.coroutines.flow.Flow<Int> =
        dao.observeCountBySyncStatus(SyncStatus.PENDING)

    override suspend fun findMostRecentUnconfirmed(since: Instant): Interaction? =
        withContext(Dispatchers.IO) {
            dao.findMostRecentUnconfirmed(since)?.toDomain()
        }

    override suspend fun markConfirmed(mobileSyncId: String) = withContext(Dispatchers.IO) {
        dao.markConfirmed(mobileSyncId)
    }

    override suspend fun autoConfirmStale(before: Instant): Int = withContext(Dispatchers.IO) {
        dao.autoConfirmStale(before)
    }
}
