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
    /**
     * Author snapshot. Locally-created notes are stamped with the session
     * agent at save time; hydrated notes carry the server's snapshot —
     * with N:M assignment the timeline shows other agents' notes, and the
     * agent must see WHO wrote them. Null on rows that predate v13.
     * Note: the sync push does NOT send these — the server derives the
     * author from the JWT (client-supplied authorship is never trusted).
     */
    val authorId: String? = null,
    val authorName: String? = null,
    val authorRole: String? = null,
)
