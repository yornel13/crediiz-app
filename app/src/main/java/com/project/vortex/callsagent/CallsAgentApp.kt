package com.project.vortex.callsagent

import android.app.Application
import android.content.Intent
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.project.vortex.callsagent.data.sync.ConnectivityObserver
import com.project.vortex.callsagent.data.sync.SyncScheduler
import com.project.vortex.callsagent.domain.call.CallController
import com.project.vortex.callsagent.domain.call.model.CallUiState
import com.project.vortex.callsagent.presentation.incall.InCallActivity
import com.project.vortex.callsagent.service.SipCallForegroundService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. HiltAndroidApp triggers Hilt code generation and
 * provides the application-level dependency container.
 *
 * Responsibilities on startup:
 * 1. Register Hilt-aware WorkerFactory with WorkManager.
 * 2. Schedule the periodic background sync.
 * 3. Start listening for connectivity changes (so we trigger sync on reconnect).
 * 4. Observe [CallController.callState] and surface [InCallActivity] when a
 *    call begins. Replaces the legacy `CallsInCallService.onCallAdded`
 *    auto-launch.
 */
@HiltAndroidApp
class CallsAgentApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var connectivityObserver: ConnectivityObserver
    @Inject lateinit var callController: CallController

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.schedulePeriodicSync()
        connectivityObserver.start()
        observeCallStateForUi()
    }

    /**
     * When the call state transitions out of idle, we:
     *  1. Start [SipCallForegroundService] so the OS keeps the mic alive
     *     when the agent backgrounds the app.
     *  2. Surface [InCallActivity] (mirrors the legacy auto-launch from
     *     `CallsInCallService.onCallAdded`).
     *
     * When the state returns to idle, we stop the foreground service so
     * the persistent notification disappears immediately.
     *
     * Idempotent — `InCallActivity.singleTask` ensures a second launch
     * does not stack; the foreground service is started/stopped at most
     * once per call.
     */
    private fun observeCallStateForUi() {
        appScope.launch {
            var wasIdle = true
            callController.callState.collect { state ->
                val isIdle = state is CallUiState.Idle || state is CallUiState.Disconnected
                if (wasIdle && !isIdle) {
                    SipCallForegroundService.start(this@CallsAgentApp)
                    startActivity(
                        Intent(this@CallsAgentApp, InCallActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            )
                        },
                    )
                } else if (!wasIdle && isIdle) {
                    SipCallForegroundService.stop(this@CallsAgentApp)
                }
                wasIdle = isIdle
            }
        }
    }
}
