package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import java.time.Instant

data class Client(
    val id: String,
    val name: String,
    val phone: String,
    val cedula: String?,
    val ssNumber: String?,
    val salary: Double?,
    val status: ClientStatus,
    val assignedTo: String?,
    val callAttempts: Int,
    val lastCalledAt: Instant?,
    val lastOutcome: CallOutcome?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>,
)
