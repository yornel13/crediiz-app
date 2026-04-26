package com.project.vortex.callsagent.common.enums

/**
 * Whether an `InteractionEntity` resulted from an outgoing call (the
 * agent dialed the client) or an incoming call (the client called the
 * agent's SIM and the agent accepted).
 *
 * Default for legacy / unspecified records: [OUTBOUND].
 */
enum class CallDirection {
    OUTBOUND,
    INBOUND,
}
