package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.data.local.entity.NoteEntity
import com.project.vortex.callsagent.data.remote.dto.SyncNoteDto
import com.project.vortex.callsagent.domain.model.Note

// ─── Domain → SyncDto (outgoing) ───────────────────────────────────────────
fun Note.toSyncDto(): SyncNoteDto = SyncNoteDto(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    interactionMobileSyncId = interactionMobileSyncId,
    content = content,
    type = type.name,
    deviceCreatedAt = deviceCreatedAt.toString(),
)

fun NoteEntity.toDomain(): Note = Note(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    interactionMobileSyncId = interactionMobileSyncId,
    content = content,
    type = type,
    deviceCreatedAt = deviceCreatedAt,
    syncStatus = syncStatus,
)

fun NoteEntity.toSyncDto(): SyncNoteDto = SyncNoteDto(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    interactionMobileSyncId = interactionMobileSyncId,
    content = content,
    type = type.name,
    deviceCreatedAt = deviceCreatedAt.toString(),
)
