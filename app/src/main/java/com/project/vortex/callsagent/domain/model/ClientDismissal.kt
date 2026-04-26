package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import java.time.Instant

/**
 * Domain mirror of `ClientDismissalEntity`. Pure data; the agent
 * issues one of these by tapping "Descartar" from a Pendientes card,
 * Pre-Call, or the Agenda's "Sin agendar" section.
 */
data class ClientDismissal(
    val mobileSyncId: String,
    val clientId: String,
    val previousStatus: ClientStatus,
    val reason: String?,
    val reasonCode: String?,
    val dismissedAt: Instant,
    val undone: Boolean,
    val undoneAt: Instant?,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
)
