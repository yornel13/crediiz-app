package com.project.vortex.callsagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.InterestLevel
import java.time.Instant

/**
 * **Local-only** record of an agent-initiated status change (the
 * `agent-status-change` endpoint). Used by the Recientes feed to
 * show the action even when no call was placed — otherwise the
 * client disappears from every list as soon as the agent moves it
 * to CONVERTED / DO_NOT_CALL / REJECTED.
 *
 * NOT synced to the server. The backend already has its own
 * `ClientStatusChange` audit row (with the source AGENT_OUT_OF_BAND);
 * this entity is purely a UI affordance so the agent can recall
 * "what did I do with this client this morning?".
 *
 * Lifecycle:
 * - Inserted on successful `agentStatusChange` repo call.
 * - Rolled back (deleted) if the HTTP fails.
 * - Stays in the DB indefinitely (small footprint), but the
 *   Recientes view filters by `timestamp >= since` (24 h window).
 */
@Entity(tableName = "local_agent_status_changes")
data class LocalAgentStatusChangeEntity(
    /** UUID generated locally — no server-side counterpart. */
    @PrimaryKey val id: String,
    val clientId: String,
    val fromStatus: ClientStatus,
    val toStatus: ClientStatus,
    val interestLevel: InterestLevel?,
    val reason: String?,
    val timestamp: Instant,
)
