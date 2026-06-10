package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import java.time.Instant

data class Client(
    val id: String,
    val name: String,
    val phone: String,
    val cedula: String?,
    val ssNumber: String?,
    val salary: Double?,
    val status: ClientStatus,
    /**
     * Reason the client was removed. Non-null **only** when [status] is
     * [ClientStatus.REMOVED]; `null` for every active/converted client.
     * The backend enforces this invariant on every status transition.
     */
    val removalReason: RemovalReason?,
    val assignedTo: String?,
    /**
     * Timestamp at which the admin assigned (or re-assigned) the client
     * to the current agent. Drives the date-grouped headers in the
     * "Sin llamar" Pendientes feed and the "Asignado · …" tag in
     * PreCall. `null` for legacy rows imported before the backend
     * tracked this — those bucket under "Más antiguos".
     */
    val assignedAt: Instant?,
    val callAttempts: Int,
    val lastCalledAt: Instant?,
    val lastOutcome: CallOutcome?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>,
    /** Latest-only quotation; `null` until the agent registers one. */
    val quotation: Quotation?,
)
