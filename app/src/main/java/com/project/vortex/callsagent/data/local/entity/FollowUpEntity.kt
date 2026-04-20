package com.project.vortex.callsagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.FollowUpStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import java.time.Instant

/**
 * Scheduled follow-up. Two separate sync-state fields are kept:
 * - `syncStatus`: whether the creation of this follow-up has been pushed.
 * - `completionSyncStatus`: whether its completion (if marked) has been pushed.
 * They are independent because a follow-up may be synced, but its completion later may not be.
 */
@Entity(tableName = "follow_ups")
data class FollowUpEntity(
    @PrimaryKey val mobileSyncId: String,
    val clientId: String,
    /** Local mobileSyncId of the interaction that originated this follow-up (if any). */
    val interactionMobileSyncId: String?,
    val scheduledAt: Instant,
    val reason: String,
    val status: FollowUpStatus,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val cancelReason: String?,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
    val completionSyncStatus: SyncStatus,
    // Denormalized fields (from server populate or saved on local completion for display)
    val clientName: String?,
    val clientPhone: String?,
)
