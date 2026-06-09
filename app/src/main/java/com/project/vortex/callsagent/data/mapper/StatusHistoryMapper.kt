package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.common.enums.Role
import com.project.vortex.callsagent.common.enums.StatusChangeSource
import com.project.vortex.callsagent.data.remote.dto.StatusHistoryEntryDto
import com.project.vortex.callsagent.domain.model.ClientStatusChange

/**
 * DTO → domain for status-history entries. Enum parsing is tolerant: an
 * unrecognized `source`/`role` (or an invalid `fromStatus`) becomes `null`
 * rather than crashing the timeline, keeping us forward-compatible with new
 * backend values. An invalid `toStatus` drops the entry (returns `null`).
 */
fun StatusHistoryEntryDto.toDomain(): ClientStatusChange? {
    val to = runCatching { ClientStatus.valueOf(toStatus) }.getOrNull() ?: return null
    return ClientStatusChange(
        id = id,
        fromStatus = fromStatus?.let { runCatching { ClientStatus.valueOf(it) }.getOrNull() },
        toStatus = to,
        removalReason = removalReason?.let { runCatching { RemovalReason.valueOf(it) }.getOrNull() },
        source = source?.let { runCatching { StatusChangeSource.valueOf(it) }.getOrNull() },
        reason = reason?.takeIf { it.isNotBlank() },
        changedByName = changedByName?.takeIf { it.isNotBlank() },
        changedByRole = changedByRole?.let { runCatching { Role.valueOf(it) }.getOrNull() },
        createdAt = createdAt.toInstant(),
    )
}
