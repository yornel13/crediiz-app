package com.project.vortex.callsagent.domain.call.model

import com.project.vortex.callsagent.common.enums.CallOutcome
import java.time.Instant

/**
 * UI-facing call state. Engine-agnostic — used by both the legacy
 * Telecom-based call manager (deprecated) and the new SIP-based
 * call controller.
 */
sealed class CallUiState {
    /** No call active. */
    data object Idle : CallUiState()

    /** Outbound call placed; remote hasn't yet returned ringing/active. */
    data object Dialing : CallUiState()

    /** Outgoing reached the remote ring tone. */
    data object Ringing : CallUiState()

    /** Audio established. The timer counts from [activeSince]. */
    data class Active(val activeSince: Instant) : CallUiState()

    /** Call ended. UI shows the wrap-up affordance and finishes shortly after. */
    data object Disconnected : CallUiState()
}

/**
 * Snapshot emitted by the call controller after a call has ended and
 * its `InteractionEntity` has been persisted. `AppNavGraph` consumes
 * this to navigate to PostCall and clear it.
 *
 * The [suggestedOutcome] / [allowedOutcomes] / [reasonLabel] triple
 * comes from [com.project.vortex.callsagent.domain.call.CallEndingInsight]
 * — it lets the PostCall screen pre-select the right outcome and hide
 * options that physically could not have happened (e.g. INTERESTED on
 * a call that never connected). All three are optional so consumers
 * outside the call-flow path (orphan recovery on app restart) can keep
 * emitting plain `EndedCall(clientId, interactionId)` values.
 */
data class EndedCall(
    val clientId: String,
    val interactionMobileSyncId: String,
    val suggestedOutcome: CallOutcome? = null,
    val allowedOutcomes: List<CallOutcome> = emptyList(),
    val reasonLabel: String? = null,
)
