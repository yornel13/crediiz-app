package com.project.vortex.callsagent.data.sync

import com.project.vortex.callsagent.telecom.CallManager
import com.project.vortex.callsagent.telecom.model.CallUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync-side gate that knows when the agent is mid-call.
 *
 * The push half of a sync (`POST /sync/interactions`) is always safe
 * to run — it doesn't touch the UI. The pull half (`refreshAssigned`,
 * `refreshAgenda`) is what re-emits Room flows and can shuffle the
 * client list under the agent's hands during a live call.
 *
 * When the gate is closed, callers should defer the pull. When the
 * call ends, [CallManager.callState] transitions back to `Idle` /
 * `Disconnected` and the next sync tick will pick up the refresh.
 *
 * KI-03 from `docs/KNOWN_ISSUES.md`.
 */
@Singleton
class InCallGate @Inject constructor(
    private val callManager: CallManager,
) {
    /** True while a call is being placed, ringing, or actively connected. */
    fun isInCall(): Boolean = callManager.callState.value.isActive()

    /** Reactive form of [isInCall] — useful for UI / observers. */
    fun observeIsInCall(): Flow<Boolean> =
        callManager.callState.map { it.isActive() }.distinctUntilChanged()

    /**
     * Suspends until [isInCall] is false. Used by [SyncManager] when
     * deferring a pull is preferable to skipping it (e.g. the user
     * just hung up — the next sync was about to fire anyway).
     */
    suspend fun awaitIdle() {
        callManager.callState.first { !it.isActive() }
    }

    private fun CallUiState.isActive(): Boolean = when (this) {
        CallUiState.Idle, CallUiState.Disconnected -> false
        CallUiState.Dialing,
        CallUiState.Ringing,
        is CallUiState.Active -> true
    }
}
