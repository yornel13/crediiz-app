package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.common.enums.Role
import com.project.vortex.callsagent.common.enums.StatusChangeSource
import java.time.Instant

/**
 * A single entry from the client's canonical status history
 * (`GET /clients/:id/status-history`). Unlike the local-only
 * [AgentStatusChangeLocal], this comes from the backend and includes
 * changes by **any** actor — this agent, other agents, the admin, or the
 * system — so the per-client timeline can show the full picture.
 */
data class ClientStatusChange(
    /** Canonical backend row id. */
    val id: String,
    /** Previous status; `null` for the initial creation entry. */
    val fromStatus: ClientStatus?,
    val toStatus: ClientStatus,
    /** Removal reason when [toStatus] is `REMOVED`; `null` otherwise / if absent. */
    val removalReason: RemovalReason?,
    /** How the change originated; `null` if the backend sent an unknown source. */
    val source: StatusChangeSource?,
    val reason: String?,
    val changedByName: String?,
    val changedByRole: Role?,
    val createdAt: Instant,
)
