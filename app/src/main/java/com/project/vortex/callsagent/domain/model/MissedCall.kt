package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.MissedCallReason
import java.time.Instant

data class MissedCall(
    val id: String,
    val phoneNumber: String,
    val matchedClientId: String?,
    val reason: MissedCallReason,
    val occurredAt: Instant,
    val acknowledged: Boolean,
)
