package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.data.local.entity.ClientDismissalEntity
import com.project.vortex.callsagent.data.remote.dto.SyncDismissalDto
import com.project.vortex.callsagent.domain.model.ClientDismissal

fun ClientDismissalEntity.toDomain(): ClientDismissal = ClientDismissal(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    previousStatus = previousStatus,
    reason = reason,
    reasonCode = reasonCode,
    dismissedAt = dismissedAt,
    undone = undone,
    undoneAt = undoneAt,
    deviceCreatedAt = deviceCreatedAt,
    syncStatus = syncStatus,
)

fun ClientDismissal.toEntity(): ClientDismissalEntity = ClientDismissalEntity(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    previousStatus = previousStatus,
    reason = reason,
    reasonCode = reasonCode,
    dismissedAt = dismissedAt,
    undone = undone,
    undoneAt = undoneAt,
    deviceCreatedAt = deviceCreatedAt,
    syncStatus = syncStatus,
)

fun ClientDismissalEntity.toSyncDto(): SyncDismissalDto = SyncDismissalDto(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    previousStatus = previousStatus.name,
    reason = reason,
    reasonCode = reasonCode,
    dismissedAt = dismissedAt.toString(),
    undone = undone,
    undoneAt = undoneAt?.toString(),
    deviceCreatedAt = deviceCreatedAt.toString(),
)
