package com.project.vortex.callsagent.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that invokes [SyncManager.syncAll].
 *
 * Return policy:
 * - [Result.success] when the sync completed cleanly OR all errors were per-item
 *   (which means the records stay PENDING and we don't want WM to retry
 *   immediately — we have our own per-item retry semantics).
 * - [Result.retry] when the overall sync attempt failed (network, server 5xx,
 *   auth failure). WorkManager will back off and try again.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (val result = syncManager.syncAll()) {
            is SyncResult.Success -> Result.success()
            is SyncResult.Error -> if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            SyncResult.Idle -> Result.success()
        }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "calls-agent-periodic-sync"
        const val UNIQUE_ONE_TIME_WORK_NAME = "calls-agent-one-time-sync"
        private const val MAX_ATTEMPTS = 5
    }
}
