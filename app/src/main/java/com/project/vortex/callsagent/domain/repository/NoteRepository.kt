package com.project.vortex.callsagent.domain.repository

import com.project.vortex.callsagent.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {

    /** Save a note locally with syncStatus = PENDING. */
    suspend fun save(note: Note)

    /** Observe all notes for a client, newest first. */
    fun observeByClient(clientId: String): Flow<List<Note>>

    suspend fun pendingSync(): List<Note>
    suspend fun markSynced(mobileSyncIds: List<String>)
    suspend fun countPending(): Int
}
