package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import java.time.Instant

/**
 * Domain mirror of a local agent-status-change row. **Not synced**:
 * the backend has its own canonical `ClientStatusChange` audit record;
 * this is purely the UI memory of "what did the agent do today".
 *
 * See [com.project.vortex.callsagent.data.local.entity.LocalAgentStatusChangeEntity]
 * for the persistence contract and the rationale.
 */
data class AgentStatusChangeLocal(
    val id: String,
    val clientId: String,
    val fromStatus: ClientStatus,
    val toStatus: ClientStatus,
    val removalReason: RemovalReason?,
    val reason: String?,
    val timestamp: Instant,
)
