package com.project.vortex.callsagent

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.project.vortex.callsagent.data.sync.ConnectivityObserver
import com.project.vortex.callsagent.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. HiltAndroidApp triggers Hilt code generation and
 * provides the application-level dependency container.
 *
 * Responsibilities on startup:
 * 1. Register Hilt-aware WorkerFactory with WorkManager.
 * 2. Schedule the periodic background sync.
 * 3. Start listening for connectivity changes (so we trigger sync on reconnect).
 */
@HiltAndroidApp
class CallsAgentApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var connectivityObserver: ConnectivityObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.schedulePeriodicSync()
        connectivityObserver.start()
    }
}
