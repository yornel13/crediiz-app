package com.project.vortex.callsagent.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.QuotationValidation
import com.project.vortex.callsagent.common.enums.RemovalReason
import java.time.Instant

/**
 * Client record mirroring the server Client document. Server `_id` is used
 * as the primary key so that all local references (foreign keys, queries)
 * are stable across sync cycles.
 */
@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    /** Panama national ID. Optional. */
    val cedula: String?,
    /** Social security number. Optional. */
    val ssNumber: String?,
    /** Monthly salary in USD. */
    val salary: Double?,
    val status: ClientStatus,
    /**
     * Removal reason. Non-null **only** when [status] is
     * [ClientStatus.REMOVED]; `null` otherwise (backend invariant).
     */
    val removalReason: RemovalReason?,
    val assignedTo: String?,
    val assignedAt: Instant?,
    /**
     * Team-wide attempt count: dials by ANY agent assigned to this client.
     * Shown in the "intentos" chip. Do NOT use it to decide the queue split —
     * use [agentCallAttempts], which is scoped to the logged-in agent.
     */
    val callAttempts: Int,
    /**
     * Per-agent attempt count: dials the LOGGED-IN agent has made to this
     * client, computed server-side and returned by `GET /clients/assigned`.
     *
     * Drives the PENDING queue split: `== 0` → "Sin llamar", `> 0` → "Para
     * reintentar" (see [ClientDao.observePendingNeverCalled] /
     * [ClientDao.observePendingForRetry]). Because it comes from the server it
     * survives logout/reinstall, unlike the local `interactions` table which is
     * wiped on logout. A client another agent already called therefore stays in
     * "Sin llamar" for an agent who never personally dialed it.
     */
    @ColumnInfo(defaultValue = "0")
    val agentCallAttempts: Int = 0,
    val lastCalledAt: Instant?,
    val lastOutcome: CallOutcome?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>?,
    // ─── Quotation (latest-only, flattened) ──────────────────────────────
    // The whole quotation is null when [quotationValidation] is null; the
    // mapper reconstructs the domain object only in that case.
    val quotationValidation: QuotationValidation?,
    val quotationBank: String?,
    val quotationQuotedAmount: Double?,
    val quotationBiweeklyPayment: Double?,
    val quotationNotes: String?,
    val quotationUpdatedBy: String?,
    val quotationUpdatedAt: Instant?,
    val uploadBatchId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
