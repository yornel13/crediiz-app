package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Server representation of a Client (agent assigned).
 * Note: `extraData` is a free-form map of any extra columns from the uploaded Excel.
 */
/**
 * Body for `POST /clients/:id/agent-status-change` — the agent moves
 * a client to a new status **without** placing a call. Typical case:
 * the client wrote on WhatsApp asking not to be called → agent flips
 * to `REMOVED` with `removalReason = DO_NOT_CALL`.
 *
 * The server validates that:
 *  - the client is assigned to the requesting agent (else 403 `CLIENT_NOT_ASSIGNED`),
 *  - the transition is allowed for AGENT actors (high-water mark; blocked = 200 no-op),
 *  - [removalReason] is set when [toStatus] is `REMOVED` (else 400 `CLIENT_REASON_REQUIRED`).
 */
@JsonClass(generateAdapter = true)
data class AgentStatusChangeDto(
    val toStatus: String,
    val removalReason: String? = null,
    val reason: String? = null,
)

@JsonClass(generateAdapter = true)
data class ClientResponse(
    @Json(name = "_id") val id: String,
    val name: String,
    val phone: String,
    /** Panama national ID. Optional — some banking partners accept clients without one. */
    val cedula: String?,
    /** Social security number. Optional — not all source banks provide it. */
    val ssNumber: String?,
    /** Monthly salary in USD. */
    val salary: Double?,
    val status: String,
    /**
     * Removal reason (`RemovalReason.name`). Present **only** when
     * [status] is `REMOVED`; `null` otherwise — backend invariant.
     */
    val removalReason: String?,
    val assignedTo: String?,
    val assignedAt: String?,
    val callAttempts: Int,
    val lastCalledAt: String?,
    val lastOutcome: String?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>?,
    val uploadBatchId: String?,
    val createdAt: String,
    val updatedAt: String,
)
