package com.project.vortex.callsagent.data.remote.dto

/**
 * Response of `GET clients/{id}/activity` — the server-side history used to
 * re-hydrate the local activity cache (notes + call interactions) after the
 * device lost its Room data (agent change wipe, fresh install, destructive
 * schema migration). STATUS_CHANGE notes are excluded server-side: the
 * timeline already renders those from `GET clients/{id}/status-history`.
 */
data class ClientActivityResponse(
    val notes: List<ActivityNoteDto>,
    val interactions: List<ActivityInteractionDto>,
)

/**
 * `mobileSyncId` is null for server-born notes (dismissal/system notes and
 * future panel-authored ones) — the mapper derives a stable synthetic local
 * key from [id] for those. Author fields are a snapshot taken at creation.
 */
data class ActivityNoteDto(
    val id: String,
    val mobileSyncId: String?,
    val clientId: String,
    val interactionMobileSyncId: String?,
    val content: String,
    val type: String,
    val authorId: String?,
    val authorName: String?,
    val authorRole: String?,
    val deviceCreatedAt: String?, // ISO-8601
    val createdAt: String,        // ISO-8601
)

/**
 * Interactions are exclusively mobile-born, so `mobileSyncId` is always
 * present — it is the idempotency key shared with the local PK.
 */
data class ActivityInteractionDto(
    val id: String,
    val mobileSyncId: String,
    val clientId: String,
    val agentId: String?,
    val direction: String?,
    val callStartedAt: String?,   // ISO-8601
    val callEndedAt: String?,     // ISO-8601
    val durationSeconds: Int?,
    val outcome: String?,
    val disconnectCause: String?,
    val deviceCreatedAt: String?, // ISO-8601
    val createdAt: String,        // ISO-8601
)
