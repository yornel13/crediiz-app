package com.project.vortex.callsagent.data.sip

import java.time.Instant

/**
 * Engine-level call state, independent of any app's UI model.
 * Consumers map this to their own state class — see
 * `docs/SIP_ENGINE_BOUNDARIES.md` (§4) for the rationale.
 */
sealed class SipCallState {
    data object Idle : SipCallState()
    data object Dialing : SipCallState()
    data object Ringing : SipCallState()
    data class Active(val activeSince: Instant) : SipCallState()
    data object Disconnected : SipCallState()
}
