package com.project.vortex.callsagent.data.sync

import android.util.Log
import com.project.vortex.callsagent.common.enums.ClientStatus
import com.project.vortex.callsagent.data.mapper.toCompletedSyncDto
import com.project.vortex.callsagent.data.mapper.toSyncDto
import com.project.vortex.callsagent.data.remote.api.SyncApi
import com.project.vortex.callsagent.data.remote.dto.SyncCompletedCategoryResult
import com.project.vortex.callsagent.data.remote.dto.SyncCategoryResult
import com.project.vortex.callsagent.data.remote.dto.SyncRequest
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncManager"

/**
 * Orchestrator for offline-first synchronization.
 *
 * A single public entry point [syncAll] is exposed: it collects every PENDING
 * record across the four repositories, pushes them in one batch, reconciles
 * per-item statuses, then refreshes the agent's client list and agenda.
 *
 * `syncSingle` (post-call immediate sync) is not a separate method — callers
 * simply invoke [syncAll] right after saving the new data. The [mutex]
 * prevents concurrent sync runs from clobbering each other (e.g. WorkManager
 * firing while a manual sync is in-flight).
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncApi: SyncApi,
    private val interactionRepo: InteractionRepository,
    private val noteRepo: NoteRepository,
    private val followUpRepo: FollowUpRepository,
    private val clientRepo: ClientRepository,
) {
    private val mutex = Mutex()

    private val _lastResult = MutableStateFlow<SyncResult>(SyncResult.Idle)
    val lastResult: StateFlow<SyncResult> = _lastResult.asStateFlow()

    /**
     * Pull every PENDING record, push to server, mark results, refresh server
     * state. Returns a [SyncResult] reflecting what happened.
     *
     * Errors: a network/server failure leaves all PENDING records untouched so
     * the next sync picks them up again. Per-item "error" responses from the
     * server also leave that specific record PENDING.
     */
    suspend fun syncAll(): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching { performSync() }
                .onSuccess { _lastResult.value = it }
                .onFailure {
                    Log.e(TAG, "Sync failed", it)
                    _lastResult.value = SyncResult.Error(
                        message = it.message ?: "Sync failed",
                        cause = it,
                    )
                }
                .getOrElse { SyncResult.Error(message = it.message ?: "Sync failed", cause = it) }
        }
    }

    private suspend fun performSync(): SyncResult {
        val interactions = interactionRepo.pendingSync()
        val notes = noteRepo.pendingSync()
        val newFollowUps = followUpRepo.pendingCreationSync()
        val completedFollowUps = followUpRepo.pendingCompletionSync()

        val hasAnything = interactions.isNotEmpty() ||
            notes.isNotEmpty() ||
            newFollowUps.isNotEmpty() ||
            completedFollowUps.isNotEmpty()

        if (!hasAnything) {
            // Even with nothing to push, we still want to pull fresh server state.
            refreshServerState()
            return SyncResult.Success(0, 0, 0, 0, 0)
        }

        val request = SyncRequest(
            interactions = interactions.map { it.toSyncDto() }.takeIf { it.isNotEmpty() },
            notes = notes.map { it.toSyncDto() }.takeIf { it.isNotEmpty() },
            followUps = newFollowUps.map { it.toSyncDto() }.takeIf { it.isNotEmpty() },
            completedFollowUps = completedFollowUps
                .mapNotNull { it.toCompletedSyncDto() }
                .takeIf { it.isNotEmpty() },
        )

        val envelope = syncApi.sync(request)
        val response = envelope.data

        // Reconcile server response → mark SYNCED the ones that succeeded or were duplicates.
        val (interactionIds, interactionDups) = extractSyncedAndDuplicateIds(response.interactions)
        val (noteIds, noteDups) = extractSyncedAndDuplicateIds(response.notes)
        val (followUpIds, followUpDups) = extractSyncedAndDuplicateIds(response.followUps)
        val completionIds = extractUpdatedIds(response.completedFollowUps)

        interactionRepo.markSynced(interactionIds)
        noteRepo.markSynced(noteIds)
        followUpRepo.markCreationSynced(followUpIds)
        followUpRepo.markCompletionSynced(completionIds)

        refreshServerState()

        return SyncResult.Success(
            syncedInteractions = response.interactions.syncedCount,
            syncedNotes = response.notes.syncedCount,
            syncedFollowUps = response.followUps.syncedCount,
            syncedCompletions = response.completedFollowUps.updatedCount,
            duplicates = interactionDups + noteDups + followUpDups,
        )
    }

    /**
     * Re-fetch server-owned state so local DB mirrors the latest truth.
     * Keeps the failure isolated — if refresh fails, the push part already
     * succeeded and we don't want to report the whole sync as failed.
     */
    private suspend fun refreshServerState() {
        runCatching { clientRepo.refreshAssigned(ClientStatus.PENDING) }
            .onFailure { Log.w(TAG, "refreshAssigned PENDING failed", it) }
        runCatching { clientRepo.refreshAssigned(ClientStatus.INTERESTED) }
            .onFailure { Log.w(TAG, "refreshAssigned INTERESTED failed", it) }
        runCatching { followUpRepo.refreshAgenda() }
            .onFailure { Log.w(TAG, "refreshAgenda failed", it) }
    }

    private fun extractSyncedAndDuplicateIds(
        result: SyncCategoryResult,
    ): Pair<List<String>, Int> {
        val synced = result.results
            .filter { it.status == "created" || it.status == "duplicate" }
            .map { it.mobileSyncId }
        return synced to result.duplicateCount
    }

    private fun extractUpdatedIds(result: SyncCompletedCategoryResult): List<String> =
        result.results
            .filter { it.status == "updated" }
            .map { it.mobileSyncId }
}
