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
)
