package com.project.vortex.callsagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import java.time.Instant

/**
 * A free-text note attached to a client. May optionally link to a specific
 * interaction (when type is CALL or POST_CALL). `MANUAL` notes have no
 * interaction — they are standalone observations.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val mobileSyncId: String,
    val clientId: String,
    /** Local mobileSyncId of the interaction this note belongs to (if any). */
    val interactionMobileSyncId: String?,
    val content: String,
    val type: NoteType,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
)
