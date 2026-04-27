package com.project.vortex.callsagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
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
    val assignedTo: String?,
    val assignedAt: Instant?,
    val callAttempts: Int,
    val lastCalledAt: Instant?,
    val lastOutcome: CallOutcome?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>?,
    val uploadBatchId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
