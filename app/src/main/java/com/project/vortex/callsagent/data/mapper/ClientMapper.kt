package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.QuotationValidation
import com.project.vortex.callsagent.common.enums.RemovalReason
import com.project.vortex.callsagent.data.local.entity.ClientEntity
import com.project.vortex.callsagent.data.remote.dto.ClientResponse
import com.project.vortex.callsagent.domain.model.Client
import com.project.vortex.callsagent.domain.model.Quotation
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
    removalReason = removalReason?.let {
        runCatching { RemovalReason.valueOf(it) }.getOrNull()
    },
    assignedTo = assignedTo,
    assignedAt = assignedAt.toInstantOrNull(),
    callAttempts = callAttempts,
    lastCalledAt = lastCalledAt.toInstantOrNull(),
    lastOutcome = lastOutcome?.let { runCatching { CallOutcome.valueOf(it) }.getOrNull() },
    lastNote = lastNote,
    queueOrder = queueOrder,
    extraData = extraData,
    quotationValidation = quotation?.validation?.let {
        runCatching { QuotationValidation.valueOf(it) }.getOrNull()
    },
    quotationBank = quotation?.bank,
    quotationQuotedAmount = quotation?.quotedAmount,
    quotationBiweeklyPayment = quotation?.biweeklyPayment,
    quotationNotes = quotation?.notes,
    quotationUpdatedBy = quotation?.updatedBy,
    quotationUpdatedAt = quotation?.updatedAt.toInstantOrNull(),
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
    removalReason = removalReason,
    assignedTo = assignedTo,
    assignedAt = assignedAt,
    callAttempts = callAttempts,
    lastCalledAt = lastCalledAt,
    lastOutcome = lastOutcome,
    lastNote = lastNote,
    queueOrder = queueOrder,
    extraData = extraData ?: emptyMap(),
    quotation = quotationValidation?.let { v ->
        Quotation(
            validation = v,
            bank = quotationBank.orEmpty(),
            quotedAmount = quotationQuotedAmount ?: 0.0,
            biweeklyPayment = quotationBiweeklyPayment ?: 0.0,
            notes = quotationNotes,
            updatedBy = quotationUpdatedBy,
            updatedAt = quotationUpdatedAt,
        )
    },
)
