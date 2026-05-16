package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.InterestLevel
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
     * Sub-classification of an INTERESTED client. Always `null` when
     * [status] is anything other than [ClientStatus.INTERESTED] — the
     * backend enforces this invariant on every status transition.
     */
    val interestLevel: InterestLevel?,
    val assignedTo: String?,
    val callAttempts: Int,
    /**
     * Consecutive `WRONG_NUMBER` strikes (HOW_IT_WORKS §6). At
     * threshold (3) the backend moves the client to UNREACHABLE.
     */
    val wrongNumberCount: Int,
    val lastCalledAt: Instant?,
    val lastOutcome: CallOutcome?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>,
)
