package com.project.vortex.callsagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.common.enums.SyncStatus
import java.time.Instant

/**
 * Local mirror of an agent-initiated `ClientDismissal`. Mirrors the
 * server schema in `calls-core/src/dismissals/schemas/...`. The
 * record is the source of truth for "did this dismissal really
 * happen / is it still active or was it undone".
 *
 * Lifecycle:
 * - Agent dismisses → row inserted with `undone = false`.
 * - Agent taps "Deshacer descarte" within 24 h → same row updated to
 *   `undone = true, undoneAt = now`. Sync pushes the toggle.
 * - 24 h pass → row stays in DB as historical record but the
 *   Recientes view stops returning it (window is over).
 *
 * Idempotency: `mobileSyncId` is the server-side unique key. If we
 * re-push, the server upserts; mobile uses it to dedupe locally.
 */
@Entity(tableName = "client_dismissals")
data class ClientDismissalEntity(
    @PrimaryKey val mobileSyncId: String,
    val clientId: String,
    val previousStatus: ClientStatus,
    val reason: String?,
    val reasonCode: String?,
    val dismissedAt: Instant,
    val undone: Boolean,
    val undoneAt: Instant?,
    val deviceCreatedAt: Instant,
    val syncStatus: SyncStatus,
)
