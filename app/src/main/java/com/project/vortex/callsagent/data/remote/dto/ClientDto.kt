package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Server representation of a Client (agent assigned).
 * Note: `extraData` is a free-form map of any extra columns from the uploaded Excel.
 */
/**
 * Body for `PATCH /clients/:id/interest-level` — quick thermometer
 * promotion/demotion of an INTERESTED client. The server rejects with
 * 400 if the client is not currently INTERESTED.
 */
@JsonClass(generateAdapter = true)
data class UpdateInterestLevelDto(
    val level: String,
)

/**
 * Body for `POST /clients/:id/agent-status-change` — the agent moves
 * a client to a new status **without** placing a call. Typical case:
 * the client wrote on WhatsApp asking not to be called → agent flips
 * to `DO_NOT_CALL` with reason "WhatsApp request".
 *
 * The server validates that:
 *  - the client is assigned to the requesting agent,
 *  - the transition is allowed for AGENT actors,
 *  - [interestLevel] is set if and only if [toStatus] is INTERESTED.
 */
@JsonClass(generateAdapter = true)
data class AgentStatusChangeDto(
    val toStatus: String,
    val reason: String? = null,
    val interestLevel: String? = null,
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
     * Thermometer sub-classification (`COLD` / `WARM` / `HOT`) when
     * the client is INTERESTED. Null otherwise — backend invariant.
     */
    val interestLevel: String?,
    val assignedTo: String?,
    val assignedAt: String?,
    val callAttempts: Int,
    /**
     * Consecutive `WRONG_NUMBER` outcomes against this client. Reset to
     * 0 by the backend when any other outcome lands. After threshold
     * (3 by default) the server moves the client to UNREACHABLE.
     */
    val wrongNumberCount: Int?,
    val lastCalledAt: String?,
    val lastOutcome: String?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>?,
    val uploadBatchId: String?,
    val createdAt: String,
    val updatedAt: String,
)
