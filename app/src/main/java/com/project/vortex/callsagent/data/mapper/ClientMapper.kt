package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.data.local.entity.ClientEntity
import com.project.vortex.callsagent.data.remote.dto.ClientResponse
import com.project.vortex.callsagent.domain.model.Client
import java.time.Instant

/**
 * Safely parse ISO-8601 date strings to Instant. Returns null on bad input.
 */
internal fun String?.toInstantOrNull(): Instant? =
    if (this.isNullOrBlank()) null
    else try {
        Instant.parse(this)
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }

internal fun String.toInstant(): Instant = Instant.parse(this)

// ─── DTO → Entity ──────────────────────────────────────────────────────────
fun ClientResponse.toEntity(): ClientEntity = ClientEntity(
    id = id,
    name = name,
    phone = phone,
    cedula = cedula,
    ssNumber = ssNumber,
    salary = salary,
    status = runCatching { ClientStatus.valueOf(status) }.getOrDefault(ClientStatus.PENDING),
    assignedTo = assignedTo,
    assignedAt = assignedAt.toInstantOrNull(),
    callAttempts = callAttempts,
    lastCalledAt = lastCalledAt.toInstantOrNull(),
    lastOutcome = lastOutcome?.let { runCatching { CallOutcome.valueOf(it) }.getOrNull() },
    lastNote = lastNote,
    queueOrder = queueOrder,
    extraData = extraData,
    uploadBatchId = uploadBatchId,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

// ─── Entity → Domain ───────────────────────────────────────────────────────
fun ClientEntity.toDomain(): Client = Client(
    id = id,
    name = name,
    phone = phone,
    cedula = cedula,
    ssNumber = ssNumber,
    salary = salary,
    status = status,
    assignedTo = assignedTo,
    callAttempts = callAttempts,
    lastCalledAt = lastCalledAt,
    lastOutcome = lastOutcome,
    lastNote = lastNote,
    queueOrder = queueOrder,
    extraData = extraData ?: emptyMap(),
)
