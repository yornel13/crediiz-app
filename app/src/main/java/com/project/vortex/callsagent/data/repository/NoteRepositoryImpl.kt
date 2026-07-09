package com.project.vortex.callsagent.data.repository

import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.db.NoteDao
import com.project.vortex.callsagent.data.local.entity.NoteEntity
import com.project.vortex.callsagent.data.local.preferences.AuthPreferences
import com.project.vortex.callsagent.data.mapper.toDomain
import com.project.vortex.callsagent.domain.model.Note
import com.project.vortex.callsagent.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Author role stamped on locally-created notes — only agents use the app. */
private const val LOCAL_AUTHOR_ROLE = "AGENT"

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val authPreferences: AuthPreferences,
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
                // Author stamped HERE — the single choke point for every
                // note-creating flow (PreCall, PostCall, CallController) —
                // so the timeline can attribute local notes without each
                // caller wiring auth state. Display-only mirror: the push
                // never sends it, the server snapshots the JWT author.
                authorId = note.authorId ?: authPreferences.agentIdFlow.first(),
                authorName = note.authorName ?: authPreferences.agentNameFlow.first(),
                authorRole = note.authorRole ?: LOCAL_AUTHOR_ROLE,
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

    override fun observePendingCount(): Flow<Int> =
        dao.observeCountBySyncStatus(SyncStatus.PENDING)
}
