package com.project.vortex.callsagent.data.sync

import android.util.Log
import com.project.vortex.callsagent.data.local.preferences.AuthPreferences
import com.project.vortex.callsagent.data.local.preferences.DeviceOwnerPreferences
import com.project.vortex.callsagent.data.mapper.toCompletedSyncDto
import com.project.vortex.callsagent.data.mapper.toSyncDto
import com.project.vortex.callsagent.data.remote.api.SyncApi
import com.project.vortex.callsagent.data.remote.dto.SyncCompletedCategoryResult
import com.project.vortex.callsagent.data.remote.dto.SyncCategoryResult
import com.project.vortex.callsagent.data.remote.dto.SyncItemResult
import com.project.vortex.callsagent.data.remote.dto.SyncRequest
import com.project.vortex.callsagent.domain.repository.ClientRepository
import com.project.vortex.callsagent.domain.repository.FollowUpRepository
import com.project.vortex.callsagent.domain.repository.InteractionRepository
import com.project.vortex.callsagent.domain.repository.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val inCallGate: InCallGate,
    private val authPreferences: AuthPreferences,
    private val deviceOwnerPreferences: DeviceOwnerPreferences,
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

    /**
     * Identity gate (see [SyncIdentityPolicy]). Evaluated at the start of a
     * sync AND re-checked right before the network push: sync runs on
     * WorkManager, whose in-flight workers survive cancelAll() and whose
     * retries back off for minutes, so a worker can straddle a
     * logout→login boundary. The server attributes pushed rows to the JWT
     * agent, so pushing another owner's rows would corrupt authorship.
     *
     * Returns the agent id the push is authorized for, or null when the
     * push must not happen. Returning the ID (not a boolean) lets the
     * caller pin the authorized identity and later verify it is UNCHANGED
     * — a boolean re-check would pass after a completed A→B login because
     * both session and owner move together (current-vs-current compares
     * B to B while the collected rows still belong to A).
     */
    private suspend fun identityAllowsPush(): String? {
        val session = authPreferences.agentIdFlow.first()
        return when (SyncIdentityPolicy.evaluate(session, deviceOwnerPreferences.currentOwner())) {
            IdentityCheck.CONSISTENT -> session
            IdentityCheck.ADOPT_SESSION -> {
                // One-time migration for devices that predate ownership
                // tracking: the rows were produced by this session's agent.
                deviceOwnerPreferences.setOwner(requireNotNull(session))
                Log.i(TAG, "Data ownership adopted by active session")
                session
            }
            IdentityCheck.BLOCKED -> {
                Log.w(TAG, "Sync skipped — session/data-owner identity mismatch or no session")
                null
            }
        }
    }

    private suspend fun performSync(): SyncResult {
        val authorizedAgent = identityAllowsPush() ?: return SyncResult.Idle

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

        // Push in bounded batches (see [SyncItemPolicy]): one oversized
        // request fails as a whole (413 / timeout) and would freeze the
        // outbox forever. Each batch commits its own results, so a failure
        // midway keeps the progress already made. Interactions go first so
        // notes/follow-ups pushed after them can resolve
        // `interactionMobileSyncId` server-side.
        val batches = buildList {
            interactions.chunked(SyncItemPolicy.BATCH_SIZE).forEach { chunk ->
                add(SyncRequest(interactions = chunk.map { it.toSyncDto() }))
            }
            notes.chunked(SyncItemPolicy.BATCH_SIZE).forEach { chunk ->
                add(SyncRequest(notes = chunk.map { it.toSyncDto() }))
            }
            newFollowUps.chunked(SyncItemPolicy.BATCH_SIZE).forEach { chunk ->
                add(SyncRequest(followUps = chunk.map { it.toSyncDto() }))
            }
            completedFollowUps.mapNotNull { it.toCompletedSyncDto() }
                .chunked(SyncItemPolicy.BATCH_SIZE)
                .forEach { chunk -> add(SyncRequest(completedFollowUps = chunk)) }
        }

        var syncedInteractions = 0
        var syncedNotes = 0
        var syncedFollowUps = 0
        var syncedCompletions = 0
        var duplicates = 0

        for (request in batches) {
            // Re-check at the last responsible moment: a login could have landed
            // between collecting the PENDING rows above (they are in-memory
            // copies — the identity-keyed wipe cannot recall them) and this
            // push. The identity must be EXACTLY the one the rows were
            // collected under: a completed A→B login yields a consistent B/B
            // pair, so only the pinned comparison catches it. Aborting leaves
            // every remaining row PENDING — safe for a same-agent retry.
            // Residual window (login completing between this line and the
            // interceptor reading the token) is micro-seconds; accepted and
            // documented.
            if (identityAllowsPush() != authorizedAgent) return SyncResult.Idle

            val response = syncApi.sync(request).data

            // Reconcile server response → mark SYNCED the ones that succeeded,
            // were duplicates, or can never succeed (see [SyncItemPolicy]).
            val (interactionIds, interactionDups) = extractSyncedAndDuplicateIds(response.interactions)
            val (noteIds, noteDups) = extractSyncedAndDuplicateIds(response.notes)
            val (followUpIds, followUpDups) = extractSyncedAndDuplicateIds(response.followUps)
            val completionIds = extractUpdatedIds(response.completedFollowUps)

            interactionRepo.markSynced(interactionIds)
            noteRepo.markSynced(noteIds)
            followUpRepo.markCreationSynced(followUpIds)
            followUpRepo.markCompletionSynced(completionIds)

            syncedInteractions += response.interactions.syncedCount
            syncedNotes += response.notes.syncedCount
            syncedFollowUps += response.followUps.syncedCount
            syncedCompletions += response.completedFollowUps.updatedCount
            duplicates += interactionDups + noteDups + followUpDups
        }

        refreshServerState()

        return SyncResult.Success(
            syncedInteractions = syncedInteractions,
            syncedNotes = syncedNotes,
            syncedFollowUps = syncedFollowUps,
            syncedCompletions = syncedCompletions,
            duplicates = duplicates,
        )
    }

    /**
     * Re-fetch server-owned state so local DB mirrors the latest truth.
     * Keeps the failure isolated — if refresh fails, the push part already
     * succeeded and we don't want to report the whole sync as failed.
     *
     * **In-call gate (KI-03):** if the agent is mid-call when this
     * runs, we skip the pull entirely. The push half already
     * happened, so the agent's data made it to the server; the
     * Room flow re-emission that would shuffle the visible list
     * is deferred until the next sync tick after the call ends.
     */
    private suspend fun refreshServerState() {
        if (inCallGate.isInCall()) {
            Log.d(TAG, "refreshServerState skipped — agent is in a call")
            return
        }

        // In the 5-state model the backend is the source of truth for
        // status, and sync always pushes before it pulls. So even when a
        // no-contact outcome leaves the row PENDING (callAttempts bumped
        // but status unchanged), the server snapshot pulled here already
        // reflects that push — `replaceAllAssigned(…)` converges the row to
        // the canonical value. A push that failed just retries on the next
        // tick (eventual consistency).
        //
        // One pull of the WHOLE assigned set (any status) mirrors it locally;
        // status-scoped lists filter client-side. A client only leaves the
        // device when unassigned/hard-deleted, so a just-removed one stays in
        // "Recientes" instead of vanishing.

        runCatching { clientRepo.refreshAssigned() }
            .onFailure { Log.w(TAG, "refreshAssigned failed", it) }
        runCatching { followUpRepo.refreshAgenda() }
            .onFailure { Log.w(TAG, "refreshAgenda failed", it) }
    }

    private fun extractSyncedAndDuplicateIds(
        result: SyncCategoryResult,
    ): Pair<List<String>, Int> {
        // "updated" = the backend upserted an existing mobileSyncId with the
        // new payload (e.g. a re-classified outcome from PostCall). It MUST be
        // treated as synced, otherwise the corrected row stays PENDING and
        // re-uploads forever. "duplicate" = backend already had it and made no
        // change; also terminal for this push. A permanent "error" (e.g. the
        // client was deleted server-side) is terminal too — re-pushing it
        // forever only bloats every later request.
        val synced = result.results
            .filter {
                it.status == "created" || it.status == "duplicate" || it.status == "updated" ||
                    isDroppable(it)
            }
            .map { it.mobileSyncId }
        return synced to result.duplicateCount
    }

    private fun extractUpdatedIds(result: SyncCompletedCategoryResult): List<String> =
        result.results
            .filter { it.status == "updated" || isDroppable(it) }
            .map { it.mobileSyncId }

    private fun isDroppable(item: SyncItemResult): Boolean {
        val drop = item.status == "error" && SyncItemPolicy.isPermanentFailure(item.error)
        if (drop) Log.w(TAG, "Dropping unsyncable ${item.mobileSyncId} from outbox: ${item.error}")
        return drop
    }
}
