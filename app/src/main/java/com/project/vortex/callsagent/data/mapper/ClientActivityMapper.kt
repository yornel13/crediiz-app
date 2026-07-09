package com.project.vortex.callsagent.data.mapper

import com.project.vortex.callsagent.common.enums.CallDirection
import com.project.vortex.callsagent.common.enums.CallOutcome
import com.project.vortex.callsagent.common.enums.NoteType
import com.project.vortex.callsagent.common.enums.SyncStatus
import com.project.vortex.callsagent.data.local.entity.InteractionEntity
import com.project.vortex.callsagent.data.local.entity.NoteEntity
import com.project.vortex.callsagent.data.remote.dto.ActivityInteractionDto
import com.project.vortex.callsagent.data.remote.dto.ActivityNoteDto
import java.time.Instant

/**
 * Maps `GET clients/{id}/activity` items onto Room entities for cache
 * hydration. Every mapper here is DEFENSIVE: a row with an unparseable
 * date or an enum value this APK doesn't know yet returns null (caller
 * skips it) instead of throwing — a backend addition must never crash
 * or block hydration of the rest of the history.
 */

/**
 * Synthetic local PK for server-born notes (dismissal/system/panel),
 * which have no mobileSyncId. Derived from the immutable server id, so
 * re-hydration converges on the same row instead of duplicating.
 * Never pushed: hydrated rows are written SYNCED and the sync push
 * only collects PENDING.
 */
private fun ActivityNoteDto.localKey(): String = mobileSyncId ?: "srv-$id"

fun ActivityNoteDto.toEntityOrNull(): NoteEntity? {
    val type = runCatching { NoteType.valueOf(type) }.getOrNull() ?: return null
    val createdAt = parseInstantOrNull(deviceCreatedAt) ?: parseInstantOrNull(createdAt) ?: return null
    return NoteEntity(
        mobileSyncId = localKey(),
        clientId = clientId,
        interactionMobileSyncId = interactionMobileSyncId,
        content = content,
        type = type,
        deviceCreatedAt = createdAt,
        syncStatus = SyncStatus.SYNCED,
        // Server-side author snapshot — with N:M assignment this may be a
        // DIFFERENT agent (or an admin); the timeline renders the name.
        authorId = authorId,
        authorName = authorName,
        authorRole = authorRole,
    )
}

fun ActivityInteractionDto.toEntityOrNull(): InteractionEntity? {
    // No outcome (or one this APK doesn't know) → the timeline card can't
    // render the row anyway; skip rather than invent a value.
    val outcome = outcome?.let { runCatching { CallOutcome.valueOf(it) }.getOrNull() }
        ?: return null
    val started = parseInstantOrNull(callStartedAt)
        ?: parseInstantOrNull(deviceCreatedAt)
        ?: parseInstantOrNull(createdAt)
        ?: return null
    val ended = parseInstantOrNull(callEndedAt) ?: started
    return InteractionEntity(
        mobileSyncId = mobileSyncId,
        clientId = clientId,
        direction = direction?.let { runCatching { CallDirection.valueOf(it) }.getOrNull() }
            ?: CallDirection.OUTBOUND,
        callStartedAt = started,
        callEndedAt = ended,
        durationSeconds = durationSeconds ?: 0,
        outcome = outcome,
        disconnectCause = disconnectCause,
        deviceCreatedAt = parseInstantOrNull(deviceCreatedAt) ?: started,
        syncStatus = SyncStatus.SYNCED,
        // Server-acknowledged rows are settled history. Leaving this false
        // would feed hydrated calls into the Phase 7.5 orphan-call recovery
        // prompt on next app start.
        confirmedByAgent = true,
    )
}

private fun parseInstantOrNull(iso: String?): Instant? =
    iso?.let { runCatching { Instant.parse(it) }.getOrNull() }
