package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.SyncStatus
import java.time.Instant

data class Interaction(
    val mobileSyncId: String,
    val clientId: String,
    val direction: CallDirection = CallDirection.OUTBOUND,
    val callStartedAt: Instant,
    val callEndedAt: Instant,
    val durationSeconds: Int,
    val outcome: CallOutcome,
    val disconnectCause: String?,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
)
