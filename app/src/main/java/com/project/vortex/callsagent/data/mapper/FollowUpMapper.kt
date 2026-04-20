package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.entity.FollowUpEntity
import com.project.vortex.callsagent.data.remote.dto.FollowUpResponse
import com.project.vortex.callsagent.data.remote.dto.SyncCompletedFollowUpDto
import com.project.vortex.callsagent.data.remote.dto.SyncFollowUpDto
import com.project.vortex.callsagent.domain.model.FollowUp

// ─── Server Response → Entity (agenda fetch) ───────────────────────────────
fun FollowUpResponse.toEntity(): FollowUpEntity = FollowUpEntity(
    mobileSyncId = mobileSyncId,
    clientId = clientId.id,
    interactionMobileSyncId = null, // server gives us interactionId (ObjectId), not the device uuid
    scheduledAt = scheduledAt.toInstant(),
    reason = reason,
    status = runCatching { FollowUpStatus.valueOf(status) }.getOrDefault(FollowUpStatus.PENDING),
    completedAt = completedAt.toInstantOrNull(),
    cancelledAt = cancelledAt.toInstantOrNull(),
    cancelReason = cancelReason,
    deviceCreatedAt = deviceCreatedAt.toInstant(),
    // Records coming from the server are already synced; their completion too (if applicable).
    syncStatus = SyncStatus.SYNCED,
    completionSyncStatus = SyncStatus.SYNCED,
    clientName = clientId.name,
    clientPhone = clientId.phone,
)

// ─── Entity → Domain ───────────────────────────────────────────────────────
fun FollowUpEntity.toDomain(): FollowUp = FollowUp(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    clientName = clientName,
    clientPhone = clientPhone,
    interactionMobileSyncId = interactionMobileSyncId,
    scheduledAt = scheduledAt,
    reason = reason,
    status = status,
    completedAt = completedAt,
    deviceCreatedAt = deviceCreatedAt,
    syncStatus = syncStatus,
    completionSyncStatus = completionSyncStatus,
)

// ─── Entity → Sync DTOs ────────────────────────────────────────────────────
fun FollowUpEntity.toSyncDto(): SyncFollowUpDto = SyncFollowUpDto(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    interactionMobileSyncId = interactionMobileSyncId,
    scheduledAt = scheduledAt.toString(),
    reason = reason,
    deviceCreatedAt = deviceCreatedAt.toString(),
)

fun FollowUpEntity.toCompletedSyncDto(): SyncCompletedFollowUpDto? =
    completedAt?.let {
        SyncCompletedFollowUpDto(
            mobileSyncId = mobileSyncId,
            completedAt = it.toString(),
        )
    }

// ─── Domain → SyncDto (outgoing) ───────────────────────────────────────────
fun FollowUp.toSyncDto(): SyncFollowUpDto = SyncFollowUpDto(
    mobileSyncId = mobileSyncId,
    clientId = clientId,
    interactionMobileSyncId = interactionMobileSyncId,
    scheduledAt = scheduledAt.toString(),
    reason = reason,
    deviceCreatedAt = deviceCreatedAt.toString(),
)

fun FollowUp.toCompletedSyncDto(): SyncCompletedFollowUpDto? =
    completedAt?.let {
        SyncCompletedFollowUpDto(
            mobileSyncId = mobileSyncId,
            completedAt = it.toString(),
        )
    }
