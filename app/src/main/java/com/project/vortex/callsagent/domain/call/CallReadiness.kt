package com.project.vortex.callsagent.domain.call

/**
 * Unified readiness state for placing outbound calls. Combines two
 * orthogonal sources:
 *
 *  - **Backend availability** of a VoIP account
 *    ([com.project.vortex.callsagent.data.voip.VoipAvailability]):
 *    is the agent assigned a Vozelia line?
 *  - **SIP registration** with the Vozelia SBC
 *    ([com.project.vortex.callsagent.data.sip.SipRegistrationState]):
 *    is the soft-phone authenticated and reachable?
 *
 * Both must be green before the agent can dial. Surfacing them as a
 * single sealed type lets the UI render one clear banner instead of
 * stacking two.
 */
sealed interface CallReadiness {

    /** Everything green — outbound calls allowed. */
    data object Ready : CallReadiness

    /** Backend confirmed the agent has no VoIP account. Admin action needed. */
    data object Unassigned : CallReadiness

    /**
     * Credentials cached, REGISTER in flight or transient. Treated as
     * "not ready yet" by the UI; usually resolves within a few seconds.
     */
    data object Connecting : CallReadiness

    /**
     * Credentials cached, but REGISTER failed (network, SBC down, wrong
     * password, etc.). Carries the SDK message verbatim for the UI's
     * "Reintentar" affordance and diagnostics.
     */
    data class Disconnected(val message: String) : CallReadiness

    /**
     * Cold start — VoIP cache empty AND no confirmed Unassigned signal.
     * The UI must NOT block here (we may simply be offline); buttons
     * are disabled silently until the orchestrator resolves the state.
     */
    data object Unknown : CallReadiness
}
