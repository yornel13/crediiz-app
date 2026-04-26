package com.project.vortex.callsagent.telecom.model

import java.time.Instant

/**
 * UI-facing call state. Mapped from `android.telecom.Call.STATE_*` by
 * [com.project.vortex.callsagent.telecom.CallManager].
 */
sealed class CallUiState {
    /** No call active. */
    object Idle : CallUiState()

    /** Outgoing call placed; carrier hasn't yet returned ringing/active. */
    object Dialing : CallUiState()

    /** Incoming call ringing OR outgoing reached the remote ring tone. */
    object Ringing : CallUiState()

    /** Audio established. The timer counts from [activeSince]. */
    data class Active(val activeSince: Instant) : CallUiState()

    /** Call ended. UI shows the wrap-up affordance and finishes shortly after. */
    object Disconnected : CallUiState()
}

/**
 * Snapshot emitted by [com.project.vortex.callsagent.telecom.CallManager]
 * after a call has ended and its `InteractionEntity` has been persisted.
 * `AppNavGraph` consumes this to navigate to PostCall and clear it.
 */
data class EndedCall(
    val clientId: String,
    val interactionMobileSyncId: String,
)
