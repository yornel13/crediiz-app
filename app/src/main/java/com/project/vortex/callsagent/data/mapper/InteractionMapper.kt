package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.data.local.entity.InteractionEntity
import com.project.vortex.callsagent.data.remote.dto.SyncInteractionDto
import com.project.vortex.callsagent.domain.model.Interaction

// ─── Domain → SyncDto (outgoing) ───────────────────────────────────────────
fun Interaction.toSyncDto(): SyncInteractionDto = SyncInteractionDto(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    direction = direction.name,
    callStartedAt = callStartedAt.toString(),
    callEndedAt = callEndedAt.toString(),
    durationSeconds = durationSeconds,
    outcome = outcome.name,
    disconnectCause = disconnectCause,
    deviceCreatedAt = deviceCreatedAt.toString(),
)

// ─── Entity → Domain ───────────────────────────────────────────────────────
fun InteractionEntity.toDomain(): Interaction = Interaction(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    direction = direction,
    callStartedAt = callStartedAt,
    callEndedAt = callEndedAt,
    durationSeconds = durationSeconds,
    outcome = outcome,
    disconnectCause = disconnectCause,
    deviceCreatedAt = deviceCreatedAt,
    syncStatus = syncStatus,
)

// ─── Entity → SyncDto (outgoing to server) ─────────────────────────────────
fun InteractionEntity.toSyncDto(): SyncInteractionDto = SyncInteractionDto(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    direction = direction.name,
    callStartedAt = callStartedAt.toString(),
    callEndedAt = callEndedAt.toString(),
    durationSeconds = durationSeconds,
    outcome = outcome.name,
    disconnectCause = disconnectCause,
    deviceCreatedAt = deviceCreatedAt.toString(),
)
