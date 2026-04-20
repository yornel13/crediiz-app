package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.db.NoteDao
import com.project.vortex.callsagent.data.local.entity.NoteEntity
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
) : NoteRepository {

    override suspend fun save(note: Note) = withContext(Dispatchers.IO) {
        dao.insert(
            NoteEntity(
                mobileSyncId = note.mobileSyncId,
                clientId = note.clientId,
                interactionMobileSyncId = note.interactionMobileSyncId,
                content = note.content,
                type = note.type,
                deviceCreatedAt = note.deviceCreatedAt,
                syncStatus = note.syncStatus,
            ),
        )
    }

    override fun observeByClient(clientId: String): Flow<List<Note>> =
        dao.observeByClient(clientId).map { list -> list.map { it.toDomain() } }

    override suspend fun pendingSync(): List<Note> = withContext(Dispatchers.IO) {
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
