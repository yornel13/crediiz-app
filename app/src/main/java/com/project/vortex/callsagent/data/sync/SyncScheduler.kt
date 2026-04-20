package com.project.vortex.callsagent.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around WorkManager for the sync worker. Callers use this
 * instead of creating WorkRequests ad-hoc.
 */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    /**
     * Ensure the periodic sync is registered. Runs ~every 20 minutes when
     * network is available. Idempotent — safe to call on every app start.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = Duration.ofMinutes(PERIODIC_INTERVAL_MINUTES),
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                Duration.ofSeconds(30),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC_WORK_NAME,
            // KEEP → don't reset the schedule if it's already running.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Kick off a one-time sync ASAP (requires network). Used after a call
     * completes or when the user explicitly taps "Force sync".
     */
    fun triggerImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                Duration.ofSeconds(15),
            )
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.UNIQUE_ONE_TIME_WORK_NAME,
            // APPEND_OR_REPLACE → if one is already running, run a new one after.
            // This guarantees the newly-saved record eventually gets pushed.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(SyncWorker.UNIQUE_ONE_TIME_WORK_NAME)
    }

    /**
     * Observe whether a sync is currently running. Useful for a progress
     * indicator in the UI.
     */
    fun observeIsSyncing(): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.UNIQUE_ONE_TIME_WORK_NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    companion object {
        private const val PERIODIC_INTERVAL_MINUTES = 20L
    }
}
