package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.data.local.entity.MissedCallEntity
import com.project.vortex.callsagent.domain.model.MissedCall

fun MissedCallEntity.toDomain(): MissedCall = MissedCall(
    id = id,
    phoneNumber = phoneNumber,
    matchedClientId = matchedClientId,
    reason = reason,
    occurredAt = occurredAt,
    acknowledged = acknowledged,
)
