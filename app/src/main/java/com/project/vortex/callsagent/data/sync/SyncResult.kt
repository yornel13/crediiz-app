package com.project.vortex.callsagent.data.sync

/**
 * Outcome of a sync attempt. Used by callers (UI, WorkManager) to decide
 * whether to retry or surface errors.
 */
sealed interface SyncResult {
    data object Idle : SyncResult

    data class Success(
        val syncedInteractions: Int,
        val syncedNotes: Int,
        val syncedFollowUps: Int,
        val syncedCompletions: Int,
        val duplicates: Int,
    ) : SyncResult

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : SyncResult
}

val SyncResult.totalSynced: Int
    get() = when (this) {
        is SyncResult.Success ->
            syncedInteractions + syncedNotes + syncedFollowUps + syncedCompletions
        else -> 0
    }
