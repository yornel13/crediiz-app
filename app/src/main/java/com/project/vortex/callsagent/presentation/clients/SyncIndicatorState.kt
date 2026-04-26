package com.project.vortex.callsagent.presentation.clients

/**
 * Compact representation of the sync state used by the chip in
 * [ClientsScreen]. Mapped from a combination of:
 *  - the live count of PENDING records across interactions / notes /
 *    follow-ups (Room flows),
 *  - whether a sync is currently running ([com.project.vortex.callsagent.data.sync.SyncScheduler.observeIsSyncing]),
 *  - the last completed sync result ([com.project.vortex.callsagent.data.sync.SyncManager.lastResult]).
 */
sealed class SyncIndicatorState {
    /** A sync is in progress right now. */
    object Syncing : SyncIndicatorState()

    /** Last sync failed and we still have work to push. */
    data class Failed(val pendingCount: Int) : SyncIndicatorState()

    /** Records are sitting locally waiting for the next sync. */
    data class Pending(val count: Int) : SyncIndicatorState()

    /** Everything is on the server. */
    object AllSynced : SyncIndicatorState()
}
