package com.project.vortex.callsagent.data.remote.dto

import com.squareup.moshi.JsonClass

// ──────────────────────────────────────────────────────────────────────────────
// Request payloads
// ──────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SyncRequest(
    val interactions: List<SyncInteractionDto>? = null,
    val notes: List<SyncNoteDto>? = null,
    val followUps: List<SyncFollowUpDto>? = null,
    val completedFollowUps: List<SyncCompletedFollowUpDto>? = null,
)

@JsonClass(generateAdapter = true)
data class SyncInteractionDto(
    val mobileSyncId: String,
    val clientId: String,
    /**
     * Direction of the call. `"OUTBOUND"` (agent dialed) or `"INBOUND"`
     * (agent answered an incoming call — Phase 3.5 Option B). Backend
     * defaults to OUTBOUND if absent so older clients still work.
     */
    val direction: String,
    val callStartedAt: String,       // ISO-8601
    val callEndedAt: String,         // ISO-8601
    val durationSeconds: Int,
    val outcome: String,             // CallOutcome
    val disconnectCause: String?,
    val deviceCreatedAt: String,     // ISO-8601
)

@JsonClass(generateAdapter = true)
data class SyncNoteDto(
    val mobileSyncId: String,
    val clientId: String,
    val interactionMobileSyncId: String?,
    val content: String,
    val type: String,                // NoteType
    val deviceCreatedAt: String,
)

@JsonClass(generateAdapter = true)
data class SyncFollowUpDto(
    val mobileSyncId: String,
    val clientId: String,
    val interactionMobileSyncId: String?,
    val scheduledAt: String,         // ISO-8601
    val reason: String,
    val deviceCreatedAt: String,
)

@JsonClass(generateAdapter = true)
data class SyncCompletedFollowUpDto(
    val mobileSyncId: String,
    val completedAt: String,         // ISO-8601
)

// ──────────────────────────────────────────────────────────────────────────────
// Response payloads
// ──────────────────────────────────────────────────────────────────────────────

@JsonClass(generateAdapter = true)
data class SyncResponse(
    val interactions: SyncCategoryResult,
    val notes: SyncCategoryResult,
    val followUps: SyncCategoryResult,
    val completedFollowUps: SyncCompletedCategoryResult,
)

@JsonClass(generateAdapter = true)
data class SyncCategoryResult(
    val results: List<SyncItemResult>,
    val syncedCount: Int,
    val duplicateCount: Int,
    val errorCount: Int,
)

@JsonClass(generateAdapter = true)
data class SyncCompletedCategoryResult(
    val results: List<SyncItemResult>,
    val updatedCount: Int,
    val errorCount: Int,
)

@JsonClass(generateAdapter = true)
data class SyncItemResult(
    val mobileSyncId: String,
    val status: String,              // "created" | "duplicate" | "updated" | "error"
    val error: String? = null,
)
