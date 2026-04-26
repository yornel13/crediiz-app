package com.project.vortex.callsagent.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.SyncStatus
import java.time.Instant

/**
 * A call the agent made or received. Created locally (offline-first) and
 * synced later. The `mobileSyncId` is a UUID generated on the device — it
 * is the primary key locally AND on the server (unique index), providing
 * idempotent sync.
 */
@Entity(tableName = "interactions")
data class InteractionEntity(
    @PrimaryKey val mobileSyncId: String,
    val clientId: String,
    @ColumnInfo(defaultValue = "OUTBOUND")
    val direction: CallDirection = CallDirection.OUTBOUND,
    val callStartedAt: Instant,
    val callEndedAt: Instant,
    val durationSeconds: Int,
    val outcome: CallOutcome,
    val disconnectCause: String?,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
)
