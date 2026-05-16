package com.project.vortex.callsagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.InterestLevel
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
     * Thermometer sub-classification when [status] is INTERESTED.
     * Always `null` for any other status (backend invariant — see
     * `HOW_IT_WORKS.md §4`).
     */
    val interestLevel: InterestLevel?,
    val assignedTo: String?,
    val assignedAt: Instant?,
    val callAttempts: Int,
    /**
     * Consecutive `WRONG_NUMBER` outcomes counted by the backend. Used
     * by the UI to show a "Wrong # ×N" badge in the Retry section and
     * warn the agent how close the client is to the UNREACHABLE
     * threshold (default 3). Reset to 0 by the server when any other
     * outcome lands.
     */
    val wrongNumberCount: Int,
    val lastCalledAt: Instant?,
    val lastOutcome: CallOutcome?,
    val lastNote: String?,
    val queueOrder: Int,
    val extraData: Map<String, Any?>?,
    val uploadBatchId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
