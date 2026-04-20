package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import java.time.Instant

data class FollowUp(
    val mobileSyncId: String,
    val clientId: String,
    val clientName: String?,
    val clientPhone: String?,
    val interactionMobileSyncId: String?,
    val scheduledAt: Instant,
    val reason: String,
    val status: FollowUpStatus,
    val completedAt: Instant?,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
    val completionSyncStatus: SyncStatus,
)
