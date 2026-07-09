package com.project.vortex.callsagent.domain.model

import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import java.time.Instant

data class Note(
    val mobileSyncId: String,
    val clientId: String,
    val interactionMobileSyncId: String?,
    val content: String,
    val type: NoteType,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
    /**
     * Author snapshot — session agent for locally-created notes (stamped by
     * the repository at save time), server snapshot for hydrated ones.
     * Null on pre-v13 rows. Display-only: the sync push never sends it
     * (the server derives authorship from the JWT).
     */
    val authorId: String? = null,
    val authorName: String? = null,
    val authorRole: String? = null,
)
